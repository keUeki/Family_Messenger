package com.shanyangcode.userservice.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shanyangcode.common.common.ErrorCode;
import com.shanyangcode.common.constant.SessionTypeConstant;
import com.shanyangcode.common.exception.ThrowUtils;
import com.shanyangcode.userservice.mapper.SessionMapper;
import com.shanyangcode.userservice.mapper.UserSessionMapper;
import com.shanyangcode.userservice.model.dto.GroupKickNotificationDTO;
import com.shanyangcode.userservice.model.dto.request.KickGroupMembersRequest;
import com.shanyangcode.userservice.model.dto.response.KickGroupMembersResponse;
import com.shanyangcode.userservice.model.entity.Session;
import com.shanyangcode.userservice.model.entity.UserSession;
import com.shanyangcode.userservice.service.KickGroupService;
import com.shanyangcode.userservice.service.NotificationService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remove-group-members service implementation
 */
@Slf4j
@Service
public class KickGroupServiceImpl implements KickGroupService {

    private final SessionMapper sessionMapper;
    private final UserSessionMapper userSessionMapper;
    private final NotificationService notificationService;

    /**
     * User role constants
     */
    private static final int USER_ROLE_GROUP_OWNER = 0;
    private static final int USER_ROLE_GROUP_ADMIN = 1;
    private static final int USER_ROLE_GROUP_MEMBER = 2;

    /**
     * Session status constants
     */
    private static final int SESSION_STATUS_NORMAL = 0;

    public KickGroupServiceImpl(SessionMapper sessionMapper,
                                UserSessionMapper userSessionMapper,
                                NotificationService notificationService) {
        this.sessionMapper = sessionMapper;
        this.userSessionMapper = userSessionMapper;
        this.notificationService = notificationService;
    }

    /**
     * Removes members from a group
     *
     * @param request the removal request
     * @return the outcome of the removal
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public KickGroupMembersResponse kickGroupMembers(KickGroupMembersRequest request) {
        Long sessionId = Long.valueOf(request.getSessionId());  // String -> Long
        Long operatorId = request.getOperatorId();
        List<Long> memberIds = request.getMemberIds();

        log.info("Removing group members, sessionId: {}, operatorId: {}, memberIds: {}",
                sessionId, operatorId, memberIds);

        // 1. Validate the parameters
        validateKickGroupParameters(sessionId, operatorId, memberIds);

        // 2. Check that the session exists and is a group chat
        validateSession(sessionId);

        // 3. Resolve the actor's permission
        UserSession operatorSession = getOperatorSession(sessionId, operatorId);
        int operatorRole = operatorSession.getRole();

        // 4. Load the members that are to be removed
        List<UserSession> targetMembers = getTargetMembers(sessionId, memberIds);
        Map<Long, UserSession> memberMap = targetMembers.stream()
                .collect(Collectors.toMap(UserSession::getUserId, us -> us));

        // 5. Check the permission for each member and remove them
        List<String> successIds = new ArrayList<>();
        for (Long memberId : memberIds) {
            try {
                UserSession targetMember = memberMap.get(memberId);

                // The member is not in the group
                if (targetMember == null) {
                    log.warn("Member {} is not in the group, skipping", memberId);
                    continue;
                }

                // The owner cannot be removed
                if (targetMember.getRole() == USER_ROLE_GROUP_OWNER) {
                    log.warn("The group owner cannot be removed, member id: {}", memberId);
                    continue;
                }

                // An admin may only remove regular members
                if (operatorRole == USER_ROLE_GROUP_ADMIN
                        && targetMember.getRole() != USER_ROLE_GROUP_MEMBER) {
                    log.warn("An admin may only remove regular members, member id: {}, role: {}",
                            memberId, targetMember.getRole());
                    continue;
                }

                // Delete the user-session row (user_session has the composite key user_id + session_id)
                LambdaQueryWrapper<UserSession> deleteWrapper = new LambdaQueryWrapper<>();
                deleteWrapper.eq(UserSession::getUserId, memberId)
                        .eq(UserSession::getSessionId, sessionId);
                userSessionMapper.delete(deleteWrapper);

                successIds.add(String.valueOf(memberId));
                log.info("Group member removed, sessionId: {}, memberId: {}", sessionId, memberId);
            } catch (Exception e) {
                log.error("Failed to remove the group member, member id: {}, error: {}", memberId, e.getMessage(), e);
            }
        }

        // 6. Push the removal notification to every group member, including those removed
        if (!successIds.isEmpty()) {
            pushKickNotification(sessionId, operatorId, successIds);
        }

        // 7. Build the response
        KickGroupMembersResponse response = new KickGroupMembersResponse();
        response.setSuccessIds(successIds);

        log.info("Group member removal finished, sessionId: {}, removed: {}", sessionId, successIds.size());
        return response;
    }

    /* ===================== Private helpers ===================== */

    /**
     * Validates the remove-group-members request parameters
     */
    private void validateKickGroupParameters(Long sessionId, Long operatorId, List<Long> memberIds) {
        ThrowUtils.throwIf(sessionId == null || sessionId <= 0, ErrorCode.PARAMS_ERROR, "Session id must not be empty");
        ThrowUtils.throwIf(operatorId == null || operatorId <= 0, ErrorCode.PARAMS_ERROR, "Actor id must not be empty");
        ThrowUtils.throwIf(memberIds == null || memberIds.isEmpty(), ErrorCode.PARAMS_ERROR, "The member id list must not be empty");
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
     * Loads the actor's membership row (they must be the owner or an admin)
     */
    private UserSession getOperatorSession(Long sessionId, Long operatorId) {
        LambdaQueryWrapper<UserSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSession::getSessionId, sessionId)
                .eq(UserSession::getUserId, operatorId)
                .eq(UserSession::getStatus, SESSION_STATUS_NORMAL);
        UserSession operatorSession = userSessionMapper.selectOne(wrapper);

        ThrowUtils.throwIf(operatorSession == null
                        || (operatorSession.getRole() != USER_ROLE_GROUP_OWNER
                        && operatorSession.getRole() != USER_ROLE_GROUP_ADMIN),
                ErrorCode.NO_AUTH_ERROR, "Only the group owner or an admin can remove members");

        return operatorSession;
    }

    /**
     * Loads the membership rows of the members to be removed
     */
    private List<UserSession> getTargetMembers(Long sessionId, List<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<UserSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSession::getSessionId, sessionId)
                .in(UserSession::getUserId, memberIds)
                .eq(UserSession::getStatus, SESSION_STATUS_NORMAL);
        return userSessionMapper.selectList(wrapper);
    }

    /**
     * Pushes the removal notification
     * <p>
     * Recipients = the remaining members plus those removed (they need to know they were removed)
     */
    private void pushKickNotification(Long sessionId, Long operatorId, List<String> kickedIds) {
        try {
            // 1. Convert the removed members' ids to Long
            List<Long> kickedMemberIds = kickedIds.stream()
                    .map(Long::valueOf)
                    .collect(Collectors.toList());

            // 2. Load every current member of the group
            LambdaQueryWrapper<UserSession> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserSession::getSessionId, sessionId)
                    .eq(UserSession::getStatus, SESSION_STATUS_NORMAL);
            List<UserSession> currentMembers = userSessionMapper.selectList(wrapper);

            // 3. Collect every recipient id (the current members plus those removed)
            Set<Long> allReceiverIds = new HashSet<>();
            currentMembers.forEach(us -> allReceiverIds.add(us.getUserId()));
            allReceiverIds.addAll(kickedMemberIds);

            // 4. Build the notification DTO
            GroupKickNotificationDTO notification = new GroupKickNotificationDTO();
            notification.setMemberIds(kickedMemberIds);
            notification.setOperatorId(operatorId);

            // 5. Push the notification to every recipient
            for (Long receiverId : allReceiverIds) {
                try {
                    notificationService.pushGroupKickNotification(receiverId, sessionId, notification);
                } catch (Exception e) {
                    log.error("Failed to push the removal notification, receiver id: {}, session id: {}, error: {}",
                            receiverId, sessionId, e.getMessage());
                }
            }

            log.info("Removal notification pushed, sessionId: {}, recipients: {}, members removed: {}",
                    sessionId, allReceiverIds.size(), kickedMemberIds);
        } catch (Exception e) {
            log.error("Failed to push the removal notification, sessionId: {}, error: {}", sessionId, e.getMessage(), e);
        }
    }
}
