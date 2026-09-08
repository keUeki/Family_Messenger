package com.shanyangcode.userservice.service.impl;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shanyangcode.common.common.ErrorCode;
import com.shanyangcode.common.constant.SessionTypeConstant;
import com.shanyangcode.common.exception.ThrowUtils;
import com.shanyangcode.common.model.dto.PageRequest;
import com.shanyangcode.common.model.dto.PageResponse;
import com.shanyangcode.userservice.mapper.SessionMapper;
import com.shanyangcode.userservice.mapper.UserSessionMapper;
import com.shanyangcode.userservice.model.dto.response.UserGroupDTO;
import com.shanyangcode.userservice.model.entity.Session;
import com.shanyangcode.userservice.model.entity.UserSession;
import com.shanyangcode.userservice.service.UserGroupService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户群聊列表服务实现类
 */
@Slf4j
@Service
public class UserGroupServiceImpl implements UserGroupService {

    private final SessionMapper sessionMapper;
    private final UserSessionMapper userSessionMapper;

    /**
     * 用户角色常量
     */
    private static final int ROLE_OWNER = 0;

    /**
     * 状态常量
     */
    private static final int STATUS_NORMAL = 0;

    /**
     * 时间格式
     */
    private static final String TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    public UserGroupServiceImpl(SessionMapper sessionMapper,
                                UserSessionMapper userSessionMapper) {
        this.sessionMapper = sessionMapper;
        this.userSessionMapper = userSessionMapper;
    }

    /**
     * 分页查询用户加入的群聊列表
     *
     * @param userId      用户ID
     * @param pageRequest 分页参数
     * @return 用户群聊分页结果
     */
    @Override
    public PageResponse<UserGroupDTO> getUserGroups(Long userId, PageRequest pageRequest) {
        log.info("查询用户群聊列表，userId: {}, pageNum: {}, pageSize: {}",
                userId, pageRequest.getPageNum(), pageRequest.getPageSize());

        // 1. 参数校验
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户ID不能为空");

        // 2. 查询用户关联的所有 sessionId（正常状态）
        LambdaQueryWrapper<UserSession> userSessionWrapper = new LambdaQueryWrapper<>();
        userSessionWrapper.eq(UserSession::getUserId, userId)
                .eq(UserSession::getStatus, STATUS_NORMAL)
                .select(UserSession::getSessionId);
        List<UserSession> userSessions = userSessionMapper.selectList(userSessionWrapper);

        if (userSessions.isEmpty()) {
            log.info("用户没有加入任何会话，userId: {}", userId);
            return buildEmptyResponse(pageRequest);
        }

        List<Long> sessionIds = userSessions.stream()
                .map(UserSession::getSessionId)
                .toList();

        // 3. 查询群聊类型的 session（type=1，status=0）
        LambdaQueryWrapper<Session> sessionWrapper = new LambdaQueryWrapper<>();
        sessionWrapper.in(Session::getSessionId, sessionIds)
                .eq(Session::getType, SessionTypeConstant.GROUP_TYPE)
                .eq(Session::getStatus, STATUS_NORMAL);
        List<Session> groupSessions = sessionMapper.selectList(sessionWrapper);

        if (groupSessions.isEmpty()) {
            log.info("用户没有加入任何群聊，userId: {}", userId);
            return buildEmptyResponse(pageRequest);
        }

        // 4. 获取群聊 sessionId 集合
        Set<Long> groupSessionIds = groupSessions.stream()
                .map(Session::getSessionId)
                .collect(Collectors.toSet());

        // 5. 查询用户在这些群聊中的信息（带分页，按加入时间倒序）
        Page<UserSession> page = pageRequest.toPage();
        LambdaQueryWrapper<UserSession> pagedWrapper = new LambdaQueryWrapper<>();
        pagedWrapper.eq(UserSession::getUserId, userId)
                .in(UserSession::getSessionId, groupSessionIds)
                .eq(UserSession::getStatus, STATUS_NORMAL)
                .orderByDesc(UserSession::getCreatedTime);
        Page<UserSession> resultPage = userSessionMapper.selectPage(page, pagedWrapper);

        if (resultPage.getRecords().isEmpty()) {
            return buildEmptyResponse(pageRequest);
        }

        // 6. 获取当前页的 sessionId 列表
        List<Long> pagedSessionIds = resultPage.getRecords().stream()
                .map(UserSession::getSessionId)
                .toList();

        // 7. 构建 sessionId -> Session 映射
        Map<Long, Session> sessionMap = groupSessions.stream()
                .filter(s -> pagedSessionIds.contains(s.getSessionId()))
                .collect(Collectors.toMap(Session::getSessionId, s -> s));

        // 8. 查询群主信息（role=0）获取 creatorId
        Map<Long, Long> creatorMap = getCreatorMap(pagedSessionIds);

        // 9. 查询群成员数量
        Map<Long, Integer> memberCountMap = getMemberCountMap(pagedSessionIds);

        // 10. 转换为 DTO 列表
        List<UserGroupDTO> dtoList = resultPage.getRecords().stream()
                .map(us -> convertToDTO(us, sessionMap.get(us.getSessionId()), creatorMap, memberCountMap))
                .toList();

        log.info("查询用户群聊列表成功，userId: {}, total: {}, 返回记录数: {}",
                userId, resultPage.getTotal(), dtoList.size());

        // 11. 构建分页响应
        return PageResponse.<UserGroupDTO>builder()
                .list(dtoList)
                .total(resultPage.getTotal())
                .pageSize(resultPage.getSize())
                .pageNum(resultPage.getCurrent())
                .pages(resultPage.getPages())
                .hasNext(resultPage.getCurrent() < resultPage.getPages())
                .hasPrevious(resultPage.getCurrent() > 1)
                .build();
    }

    /* ===================== 私有方法 ===================== */

    /**
     * 构建空的分页响应
     */
    private PageResponse<UserGroupDTO> buildEmptyResponse(PageRequest pageRequest) {
        return PageResponse.empty(pageRequest.getPageNum(), pageRequest.getPageSize());
    }

    /**
     * 查询每个群聊的群主用户ID
     */
    private Map<Long, Long> getCreatorMap(List<Long> sessionIds) {
        if (sessionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        LambdaQueryWrapper<UserSession> ownerWrapper = new LambdaQueryWrapper<>();
        ownerWrapper.in(UserSession::getSessionId, sessionIds)
                .eq(UserSession::getRole, ROLE_OWNER)
                .eq(UserSession::getStatus, STATUS_NORMAL)
                .select(UserSession::getSessionId, UserSession::getUserId);
        List<UserSession> owners = userSessionMapper.selectList(ownerWrapper);

        return owners.stream()
                .collect(Collectors.toMap(
                        UserSession::getSessionId,
                        UserSession::getUserId,
                        (existing, replacement) -> existing // 如果有多个群主，取第一个
                ));
    }

    /**
     * 查询每个群聊的成员数量
     */
    private Map<Long, Integer> getMemberCountMap(List<Long> sessionIds) {
        if (sessionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 逐个查询每个群的成员数量
        return sessionIds.stream()
                .collect(Collectors.toMap(
                        sessionId -> sessionId,
                        this::countGroupMembers
                ));
    }

    /**
     * 统计单个群聊的成员数量
     */
    private Integer countGroupMembers(Long sessionId) {
        LambdaQueryWrapper<UserSession> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(UserSession::getSessionId, sessionId)
                .eq(UserSession::getStatus, STATUS_NORMAL);
        return Math.toIntExact(userSessionMapper.selectCount(countWrapper));
    }

    /**
     * 将 UserSession 与 Session 组装为 UserGroupDTO
     */
    private UserGroupDTO convertToDTO(UserSession userSession, Session session,
                                      Map<Long, Long> creatorMap,
                                      Map<Long, Integer> memberCountMap) {
        UserGroupDTO dto = new UserGroupDTO();

        if (session != null) {
            dto.setSessionId(String.valueOf(session.getSessionId()));
            dto.setSessionName(session.getName());
            dto.setAvatar(session.getAvatar());
        }

        // 设置群主 ID
        Long creatorId = creatorMap.get(userSession.getSessionId());
        dto.setCreatorId(creatorId != null ? String.valueOf(creatorId) : null);

        // 设置用户角色
        dto.setRole(userSession.getRole());

        // 设置成员数量
        dto.setMemberCount(memberCountMap.getOrDefault(userSession.getSessionId(), 0));

        // 格式化加入时间
        if (userSession.getCreatedTime() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat(TIME_FORMAT);
            dto.setCreatedTime(sdf.format(userSession.getCreatedTime()));
        }

        return dto;
    }
}
