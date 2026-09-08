package com.shanyangcode.userservice.service.impl;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shanyangcode.common.common.ErrorCode;
import com.shanyangcode.common.constant.SessionTypeConstant;
import com.shanyangcode.common.exception.BusinessException;
import com.shanyangcode.common.exception.ThrowUtils;
import com.shanyangcode.common.model.dto.PageRequest;
import com.shanyangcode.common.utils.SnowflakeUtil;
import com.shanyangcode.userservice.constants.FriendStatusEnum;
import com.shanyangcode.userservice.constants.UserConstant;
import com.shanyangcode.userservice.constants.UserStateEnum;
import com.shanyangcode.userservice.mapper.ApplyFriendMapper;
import com.shanyangcode.userservice.mapper.FriendMapper;
import com.shanyangcode.userservice.mapper.SessionMapper;
import com.shanyangcode.userservice.mapper.UserSessionMapper;
import com.shanyangcode.userservice.model.dto.FriendDTO;
import com.shanyangcode.userservice.model.dto.ModifyFriendApplicationResponse;
import com.shanyangcode.userservice.model.dto.NewSessionNotificationDTO;
import com.shanyangcode.userservice.model.entity.*;
import com.shanyangcode.userservice.model.vo.FriendDetailVO;
import com.shanyangcode.userservice.service.FriendService;
import com.shanyangcode.userservice.service.NotificationService;
import com.shanyangcode.userservice.service.SessionService;
import com.shanyangcode.userservice.service.UserService;
import com.shanyangcode.userservice.service.UserSessionService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 好友服务实现类
 * <p>
 * 核心改动：
 * - 使用Lambda Wrapper替代string-based查询
 * - 使用Kafka异步通知替代HTTP同步调用
 * - 处理复合主键（user_id, friend_id）
 * - 状态枚举值从1/2/3调整为0/1/2
 */
@Slf4j
@Service
public class FriendServiceImpl extends ServiceImpl<FriendMapper, Friend> implements FriendService {

    private final UserService userService;
    private final SessionMapper sessionMapper;
    private final UserSessionMapper userSessionMapper;
    private final FriendMapper friendMapper;
    private final ApplyFriendMapper applyFriendMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final SessionService sessionService;
    private final NotificationService notificationService;
    private final UserSessionService userSessionService;

    /**
     * 会话内用户角色：普通成员
     */
    private static final int USER_ROLE_NORMAL = 2;

    /**
     * 会话状态：正常
     */
    private static final int SESSION_STATUS_NORMAL = 0;

    /**
     * 好友状态缓存 Key 前缀
     * <p>
     * 该缓存由跨服务的消息校验链路读取，好友关系一旦变更必须双向清除，
     * 否则被拉黑/删除的一方在 TTL 内仍会被判定为可发送。
     */
    private static final String FRIEND_STATUS_KEY_PREFIX = "msg:validate:friend:status:";

    public FriendServiceImpl(UserService userService,
                             SessionMapper sessionMapper,
                             UserSessionMapper userSessionMapper,
                             FriendMapper friendMapper,
                             ApplyFriendMapper applyFriendMapper,
                             StringRedisTemplate stringRedisTemplate,
                             SessionService sessionService,
                             NotificationService notificationService,
                             UserSessionService userSessionService) {
        this.userService = userService;
        this.sessionMapper = sessionMapper;
        this.userSessionMapper = userSessionMapper;
        this.friendMapper = friendMapper;
        this.applyFriendMapper = applyFriendMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.sessionService = sessionService;
        this.notificationService = notificationService;
        this.userSessionService = userSessionService;
    }

    @Override
    public FriendDetailVO searchUserByKeyword(String userId, String keyword) {
        ThrowUtils.throwIf(!StringUtils.hasText(keyword), ErrorCode.PARAMS_ERROR, "搜索关键字不能为空");

        // 根据正则表达式自动判断关键字类型并构建查询条件
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        if (keyword.matches(UserConstant.PHONE_REGEX)) {
            queryWrapper.eq(User::getPhone, keyword);
        } else if (keyword.matches(UserConstant.EMAIL_REGEX)) {
            queryWrapper.eq(User::getEmail, keyword);
        } else {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请输入有效的手机号或邮箱");
        }

        User user = userService.getOne(queryWrapper);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        // 获取用户详情
        return getFriendDetails(userId, String.valueOf(user.getUserId()));
    }


    /**
     * 获取好友的详细信息
     *
     * @param userId   当前用户Id
     * @param friendId 好友Id
     * @return FriendDetailVO 对象
     */
    @Override
    public FriendDetailVO getFriendDetails(String userId, String friendId) {
        Long userId1 = parseUserId(userId);
        Long friendId1 = parseUserId(friendId);

        // 1. 获取好友用户信息
        User friendUser = userService.getById(friendId1);
        validateFriendUser(friendUser);

        // 2. 构建好友详情VO
        FriendDetailVO friendDetailVO = buildFriendDetailVO(friendUser);

        // 3. 填充会话ID
        populateSessionId(userId1, friendId1, friendDetailVO);

        // 4. 填充好友状态
        populateFriendStatus(userId1, friendId1, friendDetailVO);

        return friendDetailVO;
    }


    /**
     * 解析并验证用户ID
     *
     * @param userId 用户Id字符串
     * @return 解析后的用户ID
     */
    private Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户ID格式错误");
        }
    }

    /**
     * 验证好友用户是否存在及其状态
     *
     * @param friendUser 好友的User实体
     */
    private void validateFriendUser(User friendUser) {
        ThrowUtils.throwIf(friendUser == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        // 用户状态：0 正常，1 封禁，2 注销
        ThrowUtils.throwIf(friendUser.getState() == UserStateEnum.BANNED.getCode(), ErrorCode.FORBIDDEN_ERROR, "该用户已被封禁");

        ThrowUtils.throwIf(friendUser.getState() == UserStateEnum.CANCELLED.getCode(), ErrorCode.NOT_FOUND_ERROR, "该用户已注销");
    }


    /**
     * 构建好友详细信息VO
     *
     * @param friendUser 好友的User实体
     * @return FriendDetailVO 对象
     */
    private FriendDetailVO buildFriendDetailVO(User friendUser) {
        FriendDetailVO vo = new FriendDetailVO();
        vo.setUserId(String.valueOf(friendUser.getUserId()));
        vo.setNickname(friendUser.getNickname());
        vo.setAvatar(friendUser.getAvatar());
        vo.setEmail(friendUser.getEmail());
        vo.setPhone(friendUser.getPhone());
        vo.setSignature(friendUser.getDescription());
        vo.setGender(friendUser.getGender());
        return vo;
    }

    /**
     * 填充会话ID到FriendDetailVO
     *
     * @param userId         当前用户ID
     * @param friendId       好友ID
     * @param friendDetailVO FriendDetailVO 对象
     */
    private void populateSessionId(Long userId, Long friendId, FriendDetailVO friendDetailVO) {
        // 1. 查找两个用户共同的单聊会话
        LambdaQueryWrapper<UserSession> userSession1Wrapper = new LambdaQueryWrapper<>();
        userSession1Wrapper.eq(UserSession::getUserId, userId);
        List<UserSession> userSessions1 = userSessionMapper.selectList(userSession1Wrapper);

        LambdaQueryWrapper<UserSession> userSession2Wrapper = new LambdaQueryWrapper<>();
        userSession2Wrapper.eq(UserSession::getUserId, friendId);
        List<UserSession> userSessions2 = userSessionMapper.selectList(userSession2Wrapper);

        // 2. 找出共同的会话ID
        List<Long> sessionIds1 = userSessions1.stream()
                .map(UserSession::getSessionId)
                .collect(Collectors.toList());
        List<Long> sessionIds2 = userSessions2.stream()
                .map(UserSession::getSessionId)
                .collect(Collectors.toList());

        sessionIds1.retainAll(sessionIds2);

        if (!sessionIds1.isEmpty()) {
            // 3. 筛选出单聊会话
            LambdaQueryWrapper<Session> sessionWrapper = new LambdaQueryWrapper<>();
            sessionWrapper.in(Session::getSessionId, sessionIds1)
                    .eq(Session::getType, SessionTypeConstant.SIGNAL_TYPE);
            List<Session> sessions = sessionMapper.selectList(sessionWrapper);

            if (!sessions.isEmpty()) {
                friendDetailVO.setSessionId(String.valueOf(sessions.get(0).getSessionId()));
            } else {
                friendDetailVO.setSessionId(null);
            }
        } else {
            friendDetailVO.setSessionId(null);
        }
    }


    /**
     * 填充好友状态到FriendDetailVO
     *
     * @param userId         当前用户ID
     * @param friendId       好友ID
     * @param friendDetailVO FriendDetailVO 对象
     */
    private void populateFriendStatus(Long userId, Long friendId, FriendDetailVO friendDetailVO) {
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId);
        Friend friend = this.getOne(wrapper);

        if (friend != null) {
            friendDetailVO.setStatus(friend.getStatus());
        } else {
            friendDetailVO.setStatus(FriendStatusEnum.NON_FRIEND.getCode());
        }
    }

    /**
     * 获取用户的好友列表
     * 支持分页和关键字搜索
     *
     * @param userId1      用户ID
     * @param pageRequest 分页参数
     * @param key         搜索关键字
     * @return 分页的好友DTO列表
     */
    @Override
    public IPage<FriendDTO> getFriends(String userId1, PageRequest pageRequest, String key) {
        Long userId = parseUserId(userId1);
        validateUserId(userId);

        int pageNum = pageRequest.getPageNum();
        int pageSize = pageRequest.getPageSize();

        // 1. 查询好友关系列表（使用Lambda Wrapper）
        LambdaQueryWrapper<Friend> friendWrapper = new LambdaQueryWrapper<>();
        friendWrapper.eq(Friend::getUserId, userId)
                .ne(Friend::getStatus, FriendStatusEnum.DELETED.getCode())
                .orderByDesc(Friend::getCreatedTime); // 按创建时间降序排列

        List<Friend> friendList = friendMapper.selectList(friendWrapper);

        // 2. 获取好友ID列表，从好友列表中提取所有好友的ID，组成一个新的ID列表。
        List<Long> friendIds = friendList.stream() // 将好友列表转换成流
                .map(Friend::getFriendId) // 对每个好友对象，提取其 friendId（映射转换）
                .collect(Collectors.toList()); // 将所有ID收集成一个 List<Long> 类型的列表

        if (friendIds.isEmpty()) {
            // 返回空分页结果
            Page<FriendDTO> emptyPage = new Page<>(pageNum, pageSize);
            emptyPage.setTotal(0);
            emptyPage.setRecords(List.of());
            return emptyPage;
        }

        // 3. 查询好友用户信息
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(User::getUserId, friendIds);

        // 如果有搜索关键字，添加搜索条件
        if (StringUtils.hasText(key)) {
            userWrapper.and(wrapper -> wrapper
                    .like(User::getNickname, key)
                    .or()
                    .like(User::getPhone, key)
                    .or()
                    .like(User::getUserId, key));
        }

        List<User> users = userService.list(userWrapper); // 好友信息列表

        // 4. 查询好友与当前用户之间的会话ID映射（批量查询优化）
        Map<Long, String> friendSessionMap = buildFriendSessionMap(userId, friendIds);

        // 5. 构建好友ID到好友关系的映射（避免嵌套stream，O(n+m)），n = friendList 的大小（好友关系数量），m = users 的大小（查询到的好友用户信息数量）
        Map<Long, Friend> friendRelationMap = friendList.stream()
                .collect(Collectors.toMap(Friend::getFriendId, f -> f)); // 创建一个 Map，将好友关系列表转换为 Map，好友ID为key、好友对象为value。

        // 6. 构建 FriendDTO 列表
        List<FriendDTO> friendDTOList = users.stream()
                .map(user -> {
                    FriendDTO dto = new FriendDTO();
                    dto.setUserId(String.valueOf(user.getUserId())); // 设置dto的ID（好友用户ID）
                    dto.setNickname(user.getNickname());
                    dto.setAvatar(user.getAvatar());
                    dto.setSignature(user.getDescription());
                    Friend friendRelation = friendRelationMap.get(user.getUserId()); // 通过用户ID从映射表中获取对应的好友关系
                    dto.setStatus(friendRelation != null ? friendRelation.getStatus() : FriendStatusEnum.NON_FRIEND.getCode()); // 如果存在好友关系则取其状态，否则标记为非好友
                    dto.setSessionId(friendSessionMap.get(user.getUserId())); // 从会话映射中获取该用户与当前用户的聊天会话ID
                    return dto;
                })
                .collect(Collectors.toList());

        // 7. 手动分页
        // 创建分页对象：初始化页码和每页大小
        Page<FriendDTO> page = new Page<>(pageNum, pageSize);
        page.setTotal(friendDTOList.size());

        // 计算索引范围：根据页码计算起始和结束位置，防止越界
        int fromIndex = (pageNum - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, friendDTOList.size());

        // 截取数据：使用 subList 提取当前页数据，若超出范围则返回空列表
        if (fromIndex < friendDTOList.size()) {
            page.setRecords(friendDTOList.subList(fromIndex, toIndex));
        } else {
            page.setRecords(List.of());
        }

        return page;
    }


    /**
     * 批量构建好友与会话ID的映射关系
     * <p>
     * 优化查询性能，避免N+1问题
     *
     * @param userId    当前用户ID
     * @param friendIds 好友ID列表
     * @return 好友ID到会话ID的映射
     */
    private Map<Long, String> buildFriendSessionMap(Long userId, List<Long> friendIds) {
        Map<Long, String> friendSessionMap = new HashMap<>();

        if (friendIds.isEmpty()) {
            return friendSessionMap;
        }

        // 1. 查询当前用户的所有 UserSession
        LambdaQueryWrapper<UserSession> currentUserSessionWrapper = new LambdaQueryWrapper<>();
        currentUserSessionWrapper.eq(UserSession::getUserId, userId);
        List<UserSession> currentUserSessions = userSessionMapper.selectList(currentUserSessionWrapper);

        if (currentUserSessions.isEmpty()) {
            return friendSessionMap;
        }

        // 2. 获取当前用户的所有会话ID
        // 把上面的 currentUserSessions 对象转换成会话ID列表
        List<Long> currentUserSessionIds = currentUserSessions.stream()
                .map(UserSession::getSessionId)
                .collect(Collectors.toList());

        // 3. 查询这些会话的详细信息，筛选出单聊会话（SIGNAL_TYPE）
        LambdaQueryWrapper<Session> sessionWrapper = new LambdaQueryWrapper<>();
        sessionWrapper.in(Session::getSessionId, currentUserSessionIds)
                .eq(Session::getType, SessionTypeConstant.SIGNAL_TYPE);
        List<Session> singleChatSessions = sessionMapper.selectList(sessionWrapper);

        if (singleChatSessions.isEmpty()) {
            return friendSessionMap;
        }

        // 4. 获取单聊会话ID列表
        List<Long> singleChatSessionIds = singleChatSessions.stream()
                .map(Session::getSessionId)
                .collect(Collectors.toList());

        // 5. 查询所有好友在这些会话中的 UserSession 记录
        LambdaQueryWrapper<UserSession> friendSessionWrapper = new LambdaQueryWrapper<>();
        friendSessionWrapper.in(UserSession::getUserId, friendIds)
                .in(UserSession::getSessionId, singleChatSessionIds);
        List<UserSession> friendUserSessions = userSessionMapper.selectList(friendSessionWrapper);

        // 6. 构建 friendId -> sessionId 映射
        for (UserSession friendUserSession : friendUserSessions) {
            Long friendId = friendUserSession.getUserId();
            Long sessionId = friendUserSession.getSessionId();

            // 验证这个会话是否确实是当前用户和该好友的共同会话 double check
            if (currentUserSessionIds.contains(sessionId)) {
                friendSessionMap.put(friendId, String.valueOf(sessionId));
            }
        }

        return friendSessionMap;
    }


    /**
     * 校验用户ID的有效性
     *
     * @param userId 用户ID
     */
    private void validateUserId(Long userId) {
        ThrowUtils.throwIf(userId == null || userId < 0, ErrorCode.PARAMS_ERROR, "用户ID无效");
    }


    /**
     * 删除好友
     * <p>
     * 处理流程：
     * 1. 删除双方之间的好友申请记录
     * 2. 删除双向好友关系
     * 3. 删除双方的单聊会话及用户会话关系
     * 4. 清除双向好友状态缓存
     *
     * @param userId   当前用户ID
     * @param friendId 好友ID
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteFriend(Long userId, Long friendId) {
        validateUserId(userId);
        validateUserId(friendId);

        // 1. 检查好友关系是否存在
        LambdaQueryWrapper<Friend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId);
        Friend friend = this.getOne(queryWrapper);

        ThrowUtils.throwIf(friend == null, ErrorCode.NOT_FOUND_ERROR, "好友关系不存在");

        try {
            // 2. 删除好友申请记录
            deleteApplyFriendRecords(userId, friendId);

            // 3. 删除双向好友关系记录
            deleteFriendRecords(userId, friendId);

            // 4. 删除单聊会话记录
            deleteSessionRecords(userId, friendId);

            // 5. 清除双向好友关系缓存
            evictFriendCache(userId, friendId);

            return true;
        } catch (Exception e) {
            log.error("删除好友失败，用户ID：{}，好友ID：{}，原因：{}", userId, friendId, e.getMessage(), e);
            throw new RuntimeException("删除好友失败");
        }
    }

    /**
     * 删除双方之间的好友申请记录（两个方向）
     *
     * @param userId   用户ID
     * @param friendId 好友ID
     */
    private void deleteApplyFriendRecords(Long userId, Long friendId) {
        LambdaQueryWrapper<ApplyFriend> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w
                .nested(nested -> nested.eq(ApplyFriend::getSenderId, userId)
                        .eq(ApplyFriend::getReceiverId, friendId))
                .or()
                .nested(nested -> nested.eq(ApplyFriend::getSenderId, friendId)
                        .eq(ApplyFriend::getReceiverId, userId)));

        applyFriendMapper.delete(wrapper);
    }

    /**
     * 删除双向好友关系记录
     *
     * @param userId   用户ID
     * @param friendId 好友ID
     */
    private void deleteFriendRecords(Long userId, Long friendId) {
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w
                .nested(nested -> nested.eq(Friend::getUserId, userId)
                        .eq(Friend::getFriendId, friendId))
                .or()
                .nested(nested -> nested.eq(Friend::getUserId, friendId)
                        .eq(Friend::getFriendId, userId)));

        friendMapper.delete(wrapper);
    }

    /**
     * 删除双方共同的单聊会话及其用户会话关系
     *
     * @param userId   用户ID
     * @param friendId 好友ID
     */
    private void deleteSessionRecords(Long userId, Long friendId) {
        // 1. 分别查出双方的会话
        LambdaQueryWrapper<UserSession> userSession1Wrapper = new LambdaQueryWrapper<>();
        userSession1Wrapper.eq(UserSession::getUserId, userId);
        List<UserSession> userSessions1 = userSessionMapper.selectList(userSession1Wrapper);

        LambdaQueryWrapper<UserSession> userSession2Wrapper = new LambdaQueryWrapper<>();
        userSession2Wrapper.eq(UserSession::getUserId, friendId);
        List<UserSession> userSessions2 = userSessionMapper.selectList(userSession2Wrapper);

        // 2. 取交集，得到双方共同的会话ID
        List<Long> commonSessionIds = userSessions1.stream()
                .map(UserSession::getSessionId)
                .collect(Collectors.toList());
        List<Long> sessionIds2 = userSessions2.stream()
                .map(UserSession::getSessionId)
                .collect(Collectors.toList());

        commonSessionIds.retainAll(sessionIds2); // 保留 commonSessionIds 中与 sessionIds2 的交集

        if (commonSessionIds.isEmpty()) {
            return;
        }

        // 3. 只删除其中的单聊会话，群聊会话不受影响
        LambdaQueryWrapper<Session> sessionWrapper = new LambdaQueryWrapper<>();
        sessionWrapper.in(Session::getSessionId, commonSessionIds)
                .eq(Session::getType, SessionTypeConstant.SIGNAL_TYPE);
        List<Session> sessions = sessionMapper.selectList(sessionWrapper);

        List<Long> singleChatSessionIds = sessions.stream()
                .map(Session::getSessionId)
                .collect(Collectors.toList());

        if (singleChatSessionIds.isEmpty()) {
            return;
        }

        // 4. 先删用户会话关系，再删会话本身
        LambdaQueryWrapper<UserSession> userSessionDeleteWrapper = new LambdaQueryWrapper<>();
        userSessionDeleteWrapper.in(UserSession::getSessionId, singleChatSessionIds);
        userSessionMapper.delete(userSessionDeleteWrapper);

        LambdaQueryWrapper<Session> sessionDeleteWrapper = new LambdaQueryWrapper<>();
        sessionDeleteWrapper.in(Session::getSessionId, singleChatSessionIds);
        sessionMapper.delete(sessionDeleteWrapper);
    }

    /**
     * 清除好友关系缓存（双向）
     * <p>
     * 在好友关系发生变更时调用，确保缓存与数据库的一致性：
     * - 拉黑好友后
     * - 取消拉黑后
     * - 删除好友后
     * <p>
     * 必须双向清除：漏掉任一方向，被拉黑/删除的一方在缓存TTL内仍会被判定为可发送。
     * 缓存清除失败不影响主流程，仅记录日志。
     *
     * @param userId   用户ID
     * @param friendId 好友ID
     */
    private void evictFriendCache(Long userId, Long friendId) {
        try {
            String key1 = FRIEND_STATUS_KEY_PREFIX + userId + ":" + friendId;
            String key2 = FRIEND_STATUS_KEY_PREFIX + friendId + ":" + userId;
            stringRedisTemplate.delete(Arrays.asList(key1, key2));
            log.info("已清除好友关系缓存，用户ID：{}，好友ID：{}", userId, friendId);
        } catch (Exception e) {
            log.warn("清除好友关系缓存失败，用户ID：{}，好友ID：{}，原因：{}", userId, friendId, e.getMessage());
        }
    }

    /**
     * 拉黑好友
     * <p>
     * 只更新当前用户方向的关系状态，对方仍保留正常的好友关系。
     *
     * @param userId   当前用户ID
     * @param friendId 好友ID
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean blockFriend(Long userId, Long friendId) {
        validateUserId(userId);
        validateUserId(friendId);

        // 1. 检查好友关系是否存在
        LambdaQueryWrapper<Friend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId);
        Friend friend = this.getOne(queryWrapper);

        ThrowUtils.throwIf(friend == null, ErrorCode.NOT_FOUND_ERROR, "好友关系不存在");

        // 2. Friend 使用复合主键，不能用 updateById，必须走 LambdaUpdateWrapper
        LambdaUpdateWrapper<Friend> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Friend::getStatus, FriendStatusEnum.BLOCKED.getCode())
                .set(Friend::getUpdatedTime, LocalDateTime.now())
                .eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId);

        boolean result = this.update(updateWrapper);

        // 3. 清除双向好友关系缓存
        evictFriendCache(userId, friendId);

        return result;
    }

    /**
     * 取消拉黑好友
     *
     * @param userId   当前用户ID
     * @param friendId 好友ID
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unblockFriend(Long userId, Long friendId) {
        validateUserId(userId);
        validateUserId(friendId);

        // 1. 检查好友关系是否存在
        LambdaQueryWrapper<Friend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId);
        Friend friend = this.getOne(queryWrapper);

        ThrowUtils.throwIf(friend == null, ErrorCode.NOT_FOUND_ERROR, "好友关系不存在");
        ThrowUtils.throwIf(friend.getStatus() != FriendStatusEnum.BLOCKED.getCode(),
                ErrorCode.OPERATION_ERROR, "该好友未被拉黑");

        // 2. Friend 使用复合主键，不能用 updateById，必须走 LambdaUpdateWrapper
        LambdaUpdateWrapper<Friend> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Friend::getStatus, FriendStatusEnum.NORMAL.getCode())
                .set(Friend::getUpdatedTime, LocalDateTime.now())
                .eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId);

        boolean result = this.update(updateWrapper);

        // 3. 清除双向好友关系缓存
        evictFriendCache(userId, friendId);

        return result;
    }

    /**
     * 建立好友关系（好友申请通过时调用）
     * <p>
     * 处理流程：
     * 1. 验证申请者用户存在性
     * 2. 检查是否已是好友关系
     * 3. 创建双向好友关系
     * 4. 创建单聊会话及用户会话关系
     * 5. 通过Kafka通知申请方
     *
     * @param recipient 同意申请的一方
     * @param friendId  发起申请的一方用户ID
     * @return 新建会话的信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModifyFriendApplicationResponse addFriend(User recipient, Long friendId) {
        validateUserId(friendId);
        ThrowUtils.throwIf(recipient == null, ErrorCode.NOT_FOUND_ERROR, "接收者用户不存在");

        // 1. 验证申请者用户是否存在
        User applicant = userService.getById(friendId);
        ThrowUtils.throwIf(applicant == null, ErrorCode.NOT_FOUND_ERROR, "好友申请者不存在");

        // 2. 检查是否已是好友关系
        boolean exists = this.lambdaQuery()
                .eq(Friend::getUserId, recipient.getUserId())
                .eq(Friend::getFriendId, friendId)
                .exists();
        ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "已经是好友关系");

        // 3. 创建双向好友关系
        createFriendRelations(recipient.getUserId(), friendId);

        // 4. 创建会话
        Long sessionId = createSession();

        // 5. 创建用户会话关系
        createUserSessions(recipient.getUserId(), friendId, sessionId);

        // 6. 通知申请方有新会话
        sendNewSessionNotification(friendId, recipient, sessionId);

        // 7. 构建响应对象
        return buildModifyFriendApplicationResponse(applicant, sessionId);
    }

    /**
     * 创建双向好友关系
     *
     * @param userId   当前用户ID
     * @param friendId 好友ID
     */
    private void createFriendRelations(Long userId, Long friendId) {
        Friend friend1 = new Friend();
        friend1.setUserId(userId);
        friend1.setFriendId(friendId);
        friend1.setStatus(FriendStatusEnum.NORMAL.getCode());
        friend1.setCreatedTime(LocalDateTime.now());
        friend1.setUpdatedTime(LocalDateTime.now());

        Friend friend2 = new Friend();
        friend2.setUserId(friendId);
        friend2.setFriendId(userId);
        friend2.setStatus(FriendStatusEnum.NORMAL.getCode());
        friend2.setCreatedTime(LocalDateTime.now());
        friend2.setUpdatedTime(LocalDateTime.now());

        // Friend 使用复合主键，save() 会走 updateById 分支而失效，必须直接用 mapper.insert()
        int inserted1 = friendMapper.insert(friend1);
        int inserted2 = friendMapper.insert(friend2);

        ThrowUtils.throwIf(inserted1 <= 0 || inserted2 <= 0, ErrorCode.SYSTEM_ERROR, "添加好友关系失败");
    }

    /**
     * 创建单聊会话
     *
     * @return 会话ID
     */
    private Long createSession() {
        Long sessionId = SnowflakeUtil.nextId();
        Session session = new Session();
        session.setSessionId(sessionId);
        session.setName("");
        session.setType(SessionTypeConstant.SIGNAL_TYPE);
        session.setStatus(SESSION_STATUS_NORMAL);
        session.setCreatedTime(new Date());
        session.setUpdatedTime(new Date());

        boolean sessionSaved = sessionService.save(session);
        ThrowUtils.throwIf(!sessionSaved, ErrorCode.SYSTEM_ERROR, "创建会话失败");

        return sessionId;
    }

    /**
     * 创建双方的用户会话关系
     *
     * @param userId    当前用户ID
     * @param friendId  好友ID
     * @param sessionId 会话ID
     */
    private void createUserSessions(Long userId, Long friendId, Long sessionId) {
        UserSession userSession1 = new UserSession();
        userSession1.setUserId(userId);
        userSession1.setSessionId(sessionId);
        userSession1.setRole(USER_ROLE_NORMAL);
        userSession1.setStatus(SESSION_STATUS_NORMAL);
        userSession1.setCreatedTime(new Date());
        userSession1.setUpdatedTime(new Date());

        UserSession userSession2 = new UserSession();
        userSession2.setUserId(friendId);
        userSession2.setSessionId(sessionId);
        userSession2.setRole(USER_ROLE_NORMAL);
        userSession2.setStatus(SESSION_STATUS_NORMAL);
        userSession2.setCreatedTime(new Date());
        userSession2.setUpdatedTime(new Date());

        boolean saved1 = userSessionService.save(userSession1);
        boolean saved2 = userSessionService.save(userSession2);

        ThrowUtils.throwIf(!saved1 || !saved2, ErrorCode.SYSTEM_ERROR, "创建用户会话关系失败");
    }

    /**
     * 发送新会话通知（通过Kafka）
     * <p>
     * 通知失败不影响好友关系的建立，仅记录日志。
     *
     * @param recipientId 接收通知的用户ID（发起申请的一方）
     * @param sender      同意申请的一方
     * @param sessionId   会话ID
     */
    private void sendNewSessionNotification(Long recipientId, User sender, Long sessionId) {
        try {
            NewSessionNotificationDTO notification = new NewSessionNotificationDTO();
            notification.setSessionName(sender.getNickname());
            notification.setAvatar(sender.getAvatar());

            notificationService.pushNewSession(sender.getUserId(), recipientId, sessionId,
                    SessionTypeConstant.SIGNAL_TYPE, notification);
            log.info("发送新会话通知成功，接收者ID：{}，会话ID：{}", recipientId, sessionId);
        } catch (Exception e) {
            log.warn("发送新会话通知失败，接收者ID：{}，会话ID：{}，原因：{}",
                    recipientId, sessionId, e.getMessage());
        }
    }

    /**
     * 构建通过好友申请的响应对象
     *
     * @param applicant 申请者
     * @param sessionId 会话ID
     * @return 响应对象
     */
    private ModifyFriendApplicationResponse buildModifyFriendApplicationResponse(User applicant, Long sessionId) {
        ModifyFriendApplicationResponse response = new ModifyFriendApplicationResponse();
        response.setUserId(String.valueOf(applicant.getUserId()));
        response.setSessionId(String.valueOf(sessionId));
        response.setSessionType(SessionTypeConstant.SIGNAL_TYPE);
        response.setSessionName(applicant.getNickname());
        response.setAvatar(applicant.getAvatar());
        return response;
    }
}
