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
 * Group member lookup service implementation
 */
@Slf4j
@Service
public class GetGroupMembersServiceImpl implements GetGroupMembersService {

    private final SessionMapper sessionMapper;
    private final UserSessionMapper userSessionMapper;
    private final UserService userService;

    /**
     * Session status constants
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
     * Returns a page of the group's members
     *
     * @param sessionId   the session id
     * @param pageRequest the pagination parameters
     * @return a page of group members
     */
    @Override
    public PageResponse<GroupMemberDTO> getGroupMembers(Long sessionId, PageRequest pageRequest) {
        log.info("Loading the group member list, sessionId: {}, pageNum: {}, pageSize: {}",
                sessionId, pageRequest.getPageNum(), pageRequest.getPageSize());

        // 1. Validate the parameters
        ThrowUtils.throwIf(sessionId == null || sessionId <= 0,
                ErrorCode.PARAMS_ERROR, "Session id must not be empty");

        // 2. Check that the session exists and is a group chat
        validateSession(sessionId);

        // 3. Query a page of the members' UserSession rows
        Page<UserSession> page = pageRequest.toPage();
        LambdaQueryWrapper<UserSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSession::getSessionId, sessionId)
                .eq(UserSession::getStatus, SESSION_STATUS_NORMAL);
        Page<UserSession> userSessionPage = userSessionMapper.selectPage(page, wrapper);

        List<UserSession> userSessions = userSessionPage.getRecords();

        if (userSessions.isEmpty()) {
            log.info("The group member list is empty, sessionId: {}", sessionId);
            return PageResponse.of(userSessionPage, us -> null);
        }

        // 4. Collect the user ids
        List<Long> userIds = userSessions.stream()
                .map(UserSession::getUserId)
                .collect(Collectors.toList());

        // 5. Fetch the user records in one query
        List<User> users = userService.listByIds(userIds);
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getUserId, user -> user));

        // 6. Assemble the GroupMemberDTO list
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

        // 7. Build the paginated response
        PageResponse<GroupMemberDTO> response = PageResponse.<GroupMemberDTO>builder()
                .list(groupMembers)
                .total(userSessionPage.getTotal())
                .pageSize(userSessionPage.getSize())
                .pageNum(userSessionPage.getCurrent())
                .pages(userSessionPage.getPages())
                .hasNext(userSessionPage.getCurrent() < userSessionPage.getPages())
                .hasPrevious(userSessionPage.getCurrent() > 1)
                .build();

        log.info("Group member list loaded, sessionId: {}, members on this page: {}, total: {}",
                sessionId, groupMembers.size(), userSessionPage.getTotal());

        return response;
    }

    /* ===================== Private helpers ===================== */

    /**
     * Checks that the session exists and is a group chat
     */
    private void validateSession(Long sessionId) {
        LambdaQueryWrapper<Session> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Session::getSessionId, sessionId)
                .eq(Session::getStatus, SESSION_STATUS_NORMAL);
        Session session = sessionMapper.selectOne(wrapper);

        ThrowUtils.throwIf(session == null, ErrorCode.NOT_FOUND_ERROR, "The group does not exist or has been disbanded");
        ThrowUtils.throwIf(!(SessionTypeConstant.GROUP_TYPE == session.getType()),
                ErrorCode.PARAMS_ERROR, "That session is not a group chat");
    }
}
