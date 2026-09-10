package com.shanyangcode.userservice.service.impl;

import java.util.Collections;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shanyangcode.common.common.ErrorCode;
import com.shanyangcode.common.constant.SessionTypeConstant;
import com.shanyangcode.common.exception.BusinessException;
import com.shanyangcode.common.exception.ThrowUtils;
import com.shanyangcode.userservice.mapper.SessionMapper;
import com.shanyangcode.userservice.mapper.UserSessionMapper;
import com.shanyangcode.userservice.model.dto.GroupKickNotificationDTO;
import com.shanyangcode.userservice.model.dto.request.GroupExitRequestDTO;
import com.shanyangcode.userservice.model.entity.Session;
import com.shanyangcode.userservice.model.entity.User;
import com.shanyangcode.userservice.model.entity.UserSession;
import com.shanyangcode.userservice.service.ExitGroupService;
import com.shanyangcode.userservice.service.NotificationService;
import com.shanyangcode.userservice.service.UserService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leave-group service implementation
 */
@Slf4j
@Service
public class ExitGroupServiceImpl implements ExitGroupService {

    private final SessionMapper sessionMapper;
    private final UserSessionMapper userSessionMapper;
    private final NotificationService notificationService;
    private final UserService userService;

    /**
     * User role constants
     */
    private static final int USER_ROLE_GROUP_OWNER = 0;

    /**
     * Session status constants
     */
    private static final int SESSION_STATUS_NORMAL = 0;

    /**
     * User status constants
     */
    private static final int USER_STATUS_NORMAL = 0;

    public ExitGroupServiceImpl(SessionMapper sessionMapper,
                                UserSessionMapper userSessionMapper,
                                NotificationService notificationService,
                                UserService userService) {
        this.sessionMapper = sessionMapper;
        this.userSessionMapper = userSessionMapper;
        this.notificationService = notificationService;
        this.userService = userService;
    }

    /**
     * Leaves a group chat
     *
     * @param request the leave-group request
     * @return whether the operation succeeded
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean exitGroup(GroupExitRequestDTO request) {
        Long sessionId = request.getSessionId();
        Long userId = request.getUserId();

        log.info("User is leaving the group, sessionId: {}, userId: {}", sessionId, userId);

        // 1. Validate the parameters
        validateExitGroupParameters(sessionId, userId);

        // 2. Check that the user exists and is active
        validateUser(userId);

        // 3. Check that the session exists and is a group chat
        validateSession(sessionId);

        // 4. Load the user's membership row
        LambdaQueryWrapper<UserSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSession::getSessionId, sessionId)
                .eq(UserSession::getUserId, userId)
                .eq(UserSession::getStatus, SESSION_STATUS_NORMAL);
        UserSession userSession = userSessionMapper.selectOne(wrapper);

        ThrowUtils.throwIf(userSession == null, ErrorCode.OPERATION_ERROR, "You are not a member of that group");

        // 5. The owner cannot simply leave; they must transfer ownership or disband the group first
        if (userSession.getRole() == USER_ROLE_GROUP_OWNER) {
            log.warn("The owner cannot leave the group directly and must transfer ownership or disband it first, userId: {}, sessionId: {}",
                    userId, sessionId);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "The group owner cannot leave directly; transfer ownership or disband the group first");
        }

        // 6. Delete the user-session row (user_session has the composite key user_id + session_id)
        LambdaQueryWrapper<UserSession> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(UserSession::getUserId, userId)
                .eq(UserSession::getSessionId, sessionId);
        int deleted = userSessionMapper.delete(deleteWrapper);
        ThrowUtils.throwIf(deleted <= 0, ErrorCode.SYSTEM_ERROR, "Failed to leave the group");

        log.info("User left the group, sessionId: {}, userId: {}", sessionId, userId);

        // 7. Notify the members who remain in the group
        pushExitNotification(sessionId, userId);

        return true;
    }

    /* ===================== Private helpers ===================== */

    /**
     * Validates the leave-group request parameters
     */
    private void validateExitGroupParameters(Long sessionId, Long userId) {
        ThrowUtils.throwIf(sessionId == null || sessionId <= 0, ErrorCode.PARAMS_ERROR, "Session id must not be empty");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "User id must not be empty");
    }

    /**
     * Checks that the user exists and is active
     */
    private void validateUser(Long userId) {
        User user = userService.getById(userId);
        ThrowUtils.throwIf(user == null || user.getState() != USER_STATUS_NORMAL,
                ErrorCode.NOT_FOUND_ERROR, "The user does not exist or is not active");
    }

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

    /**
     * Notifies the members who remain in the group
     * <p>
     * Reuses GroupKickNotificationDTO; a null operatorId means the member left rather than being removed.
     */
    private void pushExitNotification(Long sessionId, Long exitUserId) {
        try {
            // 1. Load the remaining members (the leaver is already gone)
            LambdaQueryWrapper<UserSession> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserSession::getSessionId, sessionId)
                    .eq(UserSession::getStatus, SESSION_STATUS_NORMAL);
            List<UserSession> remainingMembers = userSessionMapper.selectList(wrapper);

            if (remainingMembers.isEmpty()) {
                log.info("No members remain in the group, skipping the notification, sessionId: {}", sessionId);
                return;
            }

            // 2. Build the notification DTO (a null operatorId means the member left voluntarily)
            GroupKickNotificationDTO notification = new GroupKickNotificationDTO();
            notification.setMemberIds(Collections.singletonList(exitUserId));
            notification.setOperatorId(null); // left voluntarily, so there is no actor

            // 3. Push the notification to every remaining member
            for (UserSession member : remainingMembers) {
                try {
                    notificationService.pushGroupKickNotification(member.getUserId(),
                            sessionId, notification);
                } catch (Exception e) {
                    log.error("Failed to push the leave notification, receiver id: {}, session id: {}, error: {}",
                            member.getUserId(), sessionId, e.getMessage());
                }
            }

            log.info("Leave notification pushed, sessionId: {}, leaver: {}, recipients: {}",
                    sessionId, exitUserId, remainingMembers.size());
        } catch (Exception e) {
            log.error("Failed to push the leave notification, sessionId: {}, error: {}", sessionId, e.getMessage(), e);
        }
    }
}
