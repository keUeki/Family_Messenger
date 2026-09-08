package com.shanyangcode.userservice.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import com.shanyangcode.userservice.model.dto.response.GroupMemberDTO;
import com.shanyangcode.userservice.model.entity.Session;
import com.shanyangcode.userservice.model.entity.User;
import com.shanyangcode.userservice.model.entity.UserSession;
import com.shanyangcode.userservice.service.GetGroupMembersService;
import com.shanyangcode.userservice.service.UserService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 群成员查询服务实现类
 */
@Slf4j
@Service
public class GetGroupMembersServiceImpl implements GetGroupMembersService {

    private final SessionMapper sessionMapper;
    private final UserSessionMapper userSessionMapper;
    private final UserService userService;

    /**
     * 会话状态常量
     */
    private static final int SESSION_STATUS_NORMAL = 0;

    public GetGroupMembersServiceImpl(SessionMapper sessionMapper,
                                      UserSessionMapper userSessionMapper,
                                      UserService userService) {
        this.sessionMapper = sessionMapper;
        this.userSessionMapper = userSessionMapper;
        this.userService = userService;
    }

    /**
     * 分页查询群成员列表
     *
     * @param sessionId   会话ID
     * @param pageRequest 分页参数
     * @return 群成员分页结果
     */
    @Override
    public PageResponse<GroupMemberDTO> getGroupMembers(Long sessionId, PageRequest pageRequest) {
        log.info("查询群聊成员列表，sessionId: {}, pageNum: {}, pageSize: {}",
                sessionId, pageRequest.getPageNum(), pageRequest.getPageSize());

        // 1. 参数校验
        ThrowUtils.throwIf(sessionId == null || sessionId <= 0,
                ErrorCode.PARAMS_ERROR, "会话ID不能为空");

        // 2. 校验会话存在且为群聊
        validateSession(sessionId);

        // 3. 分页查询群成员的 UserSession 记录
        Page<UserSession> page = pageRequest.toPage();
        LambdaQueryWrapper<UserSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSession::getSessionId, sessionId)
                .eq(UserSession::getStatus, SESSION_STATUS_NORMAL);
        Page<UserSession> userSessionPage = userSessionMapper.selectPage(page, wrapper);

        List<UserSession> userSessions = userSessionPage.getRecords();

        if (userSessions.isEmpty()) {
            log.info("群聊成员列表为空，sessionId: {}", sessionId);
            return PageResponse.of(userSessionPage, us -> null);
        }

        // 4. 提取所有用户 ID
        List<Long> userIds = userSessions.stream()
                .map(UserSession::getUserId)
                .collect(Collectors.toList());

        // 5. 批量查询用户信息
        List<User> users = userService.listByIds(userIds);
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getUserId, user -> user));

        // 6. 组装 GroupMemberDTO 列表
        List<GroupMemberDTO> groupMembers = new ArrayList<>();
        for (UserSession userSession : userSessions) {
            User user = userMap.get(userSession.getUserId());
            if (user != null) {
                GroupMemberDTO memberDTO = new GroupMemberDTO();
                memberDTO.setUserId(String.valueOf(user.getUserId()));
                memberDTO.setNickname(user.getNickname());
                memberDTO.setAvatar(user.getAvatar());
                memberDTO.setRole(userSession.getRole());
                groupMembers.add(memberDTO);
            }
        }

        // 7. 构建分页响应
        PageResponse<GroupMemberDTO> response = PageResponse.<GroupMemberDTO>builder()
                .list(groupMembers)
                .total(userSessionPage.getTotal())
                .pageSize(userSessionPage.getSize())
                .pageNum(userSessionPage.getCurrent())
                .pages(userSessionPage.getPages())
                .hasNext(userSessionPage.getCurrent() < userSessionPage.getPages())
                .hasPrevious(userSessionPage.getCurrent() > 1)
                .build();

        log.info("查询群聊成员列表成功，sessionId: {}, 当前页成员数: {}, 总数: {}",
                sessionId, groupMembers.size(), userSessionPage.getTotal());

        return response;
    }

    /* ===================== 私有方法 ===================== */

    /**
     * 校验会话存在且为群聊类型
     */
    private void validateSession(Long sessionId) {
        LambdaQueryWrapper<Session> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Session::getSessionId, sessionId)
                .eq(Session::getStatus, SESSION_STATUS_NORMAL);
        Session session = sessionMapper.selectOne(wrapper);

        ThrowUtils.throwIf(session == null, ErrorCode.NOT_FOUND_ERROR, "群聊不存在或已解散");
        ThrowUtils.throwIf(!(SessionTypeConstant.GROUP_TYPE == session.getType()),
                ErrorCode.PARAMS_ERROR, "该会话不是群聊");
    }
}
