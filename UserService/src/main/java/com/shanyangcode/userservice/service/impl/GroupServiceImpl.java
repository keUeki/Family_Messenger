package com.shanyangcode.userservice.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shanyangcode.common.common.ErrorCode;
import com.shanyangcode.common.constant.SessionTypeConstant;
import com.shanyangcode.common.exception.ThrowUtils;
import com.shanyangcode.userservice.constants.FriendStatusEnum;
import com.shanyangcode.userservice.mapper.FriendMapper;
import com.shanyangcode.userservice.mapper.SessionMapper;
import com.shanyangcode.userservice.mapper.UserSessionMapper;
import com.shanyangcode.userservice.model.dto.NewGroupSessionNotificationDTO;
import com.shanyangcode.userservice.model.dto.request.InviteGroupRequest;
import com.shanyangcode.userservice.model.dto.response.InviteGroupResponse;
import com.shanyangcode.userservice.model.entity.Friend;
import com.shanyangcode.userservice.model.entity.Session;
import com.shanyangcode.userservice.model.entity.UserSession;
import com.shanyangcode.userservice.service.GroupService;
import com.shanyangcode.userservice.service.NotificationService;
import com.shanyangcode.userservice.service.UserSessionService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Group service implementation
 */
@Slf4j
@Service
public class GroupServiceImpl implements GroupService {

    private final SessionMapper sessionMapper;
    private final UserSessionMapper userSessionMapper;
    private final FriendMapper friendMapper;
    private final NotificationService notificationService;
    private final UserSessionService userSessionService;

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

    /**
     * Default group avatar URL
     */
    private static final String DEFAULT_GROUP_AVATAR_URL = "https://video.shanyangcode.com/image/default/A9C9C83CCCE043EC8253DB5D7545DCB4-6-2.png";

    public GroupServiceImpl(SessionMapper sessionMapper,
                            UserSessionMapper userSessionMapper,
                            FriendMapper friendMapper,
                            NotificationService notificationService,
                            UserSessionService userSessionService) {
        this.sessionMapper = sessionMapper;
        this.userSessionMapper = userSessionMapper;
        this.friendMapper = friendMapper;
        this.notificationService = notificationService;
        this.userSessionService = userSessionService;
    }

    /**
     * Invites members to a group chat
     *
     * @param request the invitation request
     * @return the outcome of the invitation
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InviteGroupResponse inviteGroup(InviteGroupRequest request) {
        Long sessionId = request.getSessionId();
        Long inviterId = request.getInviterId();
        List<Long> inviteeIds = request.getInviteeIds();

        log.info("Inviting members to the group, sessionId: {}, inviterId: {}, inviteeIds: {}", sessionId, inviterId, inviteeIds);

        // 1. Validate the parameters
        validateInviteGroupParameters(sessionId, inviterId, inviteeIds);

        // 2. Check that the session exists and is a group chat
        Session session = validateSession(sessionId);

        // 3. Check the inviter's permission (they must be the owner or an admin)
        validateInviterPermission(sessionId, inviterId);

        // 4. Check that the inviter is friends with each invitee
        List<Long> failedIds = new ArrayList<>();
        List<Long> validInviteeIds = validateAndFilterFriends(inviterId, inviteeIds, failedIds);

        // 5. Filter out anyone already in the group
        validInviteeIds = filterExistingMembers(sessionId, validInviteeIds, failedIds);

        ThrowUtils.throwIf(validInviteeIds.isEmpty(), ErrorCode.OPERATION_ERROR, "There are no eligible friends to add to the group");

        // 6. Insert the user_session rows and publish the Kafka notifications
        List<Long> successIds = insertMembersAndPushNotifications(sessionId, session.getName(), validInviteeIds, failedIds);

        // 7. Build the response
        InviteGroupResponse response = new InviteGroupResponse();

        // Convert the List<Long> into a List<String>
        response.setSuccessIds(successIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList()));

        response.setFailedIds(failedIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList()));

        log.info("Group invitation finished, sessionId: {}, succeeded: {}, failed: {}", sessionId, successIds.size(), failedIds.size());
        return response;
    }

    /* ===================== Private helpers ===================== */

    /**
     * Validates the group invitation request parameters
     */
    private void validateInviteGroupParameters(Long sessionId, Long inviterId, List<Long> inviteeIds) {
        ThrowUtils.throwIf(sessionId == null || sessionId <= 0, ErrorCode.PARAMS_ERROR, "Session id must not be empty");
        ThrowUtils.throwIf(inviterId == null || inviterId <= 0, ErrorCode.PARAMS_ERROR, "Inviter id must not be empty");
        ThrowUtils.throwIf(inviteeIds == null || inviteeIds.isEmpty(), ErrorCode.PARAMS_ERROR, "The invitee id list must not be empty");
    }

    /**
     * Checks that the session exists and is a group chat
     */
    private Session validateSession(Long sessionId) {
        LambdaQueryWrapper<Session> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Session::getSessionId, sessionId)
                .eq(Session::getStatus, SESSION_STATUS_NORMAL);
        Session session = sessionMapper.selectOne(wrapper);

        ThrowUtils.throwIf(session == null, ErrorCode.NOT_FOUND_ERROR, "The group does not exist or has been disbanded");
        ThrowUtils.throwIf(!(SessionTypeConstant.GROUP_TYPE == session.getType()),
                ErrorCode.PARAMS_ERROR, "That session is not a group chat");

        return session;
    }

    /**
     * Checks the inviter's permission (they must be the owner or an admin)
     */
    private void validateInviterPermission(Long sessionId, Long inviterId) {
        LambdaQueryWrapper<UserSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSession::getSessionId, sessionId)
                .eq(UserSession::getUserId, inviterId)
                .eq(UserSession::getStatus, SESSION_STATUS_NORMAL);
        UserSession userSession = userSessionMapper.selectOne(wrapper);

        ThrowUtils.throwIf(userSession == null, ErrorCode.NO_AUTH_ERROR, "You are not a member of that group");
        ThrowUtils.throwIf(userSession.getRole() != USER_ROLE_GROUP_OWNER && userSession.getRole() != USER_ROLE_GROUP_ADMIN,
                ErrorCode.NO_AUTH_ERROR, "Only the group owner or an admin can invite members");
    }

    /**
     * Checks the friendships and returns the ids of the invitees that are eligible
     */
    private List<Long> validateAndFilterFriends(Long inviterId, List<Long> inviteeIds, List<Long> failedIds) {
        // Load every friend id of the inviter
        LambdaQueryWrapper<Friend> friendWrapper = new LambdaQueryWrapper<>();
        friendWrapper.eq(Friend::getUserId, inviterId)
                .eq(Friend::getStatus, FriendStatusEnum.NORMAL.getCode());
        List<Friend> friends = friendMapper.selectList(friendWrapper);

        Set<Long> friendIdSet = friends.stream()
                .map(Friend::getFriendId)
                .collect(Collectors.toSet());

        List<Long> validInviteeIds = new ArrayList<>();

        for (Long inviteeId : inviteeIds) {
            if (friendIdSet.contains(inviteeId)) {
                validInviteeIds.add(inviteeId);
            } else {
                failedIds.add(inviteeId);
                log.info("Invitee {} is not a friend of the inviter and cannot join the group", inviteeId);
            }
        }

        return validInviteeIds;
    }

    /**
     * Filters out the members already in the group
     */
    private List<Long> filterExistingMembers(Long sessionId, List<Long> inviteeIds, List<Long> failedIds) {
        if (inviteeIds.isEmpty()) {
            return inviteeIds;
        }

        // Load the members already in the group
        LambdaQueryWrapper<UserSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSession::getSessionId, sessionId)
                .in(UserSession::getUserId, inviteeIds)
                .eq(UserSession::getStatus, SESSION_STATUS_NORMAL);
        List<UserSession> existingMembers = userSessionMapper.selectList(wrapper);

        Set<Long> existingMemberIds = existingMembers.stream()
                .map(UserSession::getUserId)
                .collect(Collectors.toSet());

        List<Long> newInviteeIds = new ArrayList<>();
        for (Long inviteeId : inviteeIds) {
            if (existingMemberIds.contains(inviteeId)) {
                failedIds.add(inviteeId);
                log.info("Invitee {} is already in the group", inviteeId);
            } else {
                newInviteeIds.add(inviteeId);
            }
        }

        return newInviteeIds;
    }

    /**
     * Inserts the members and publishes the Kafka notifications
     */
    private List<Long> insertMembersAndPushNotifications(Long sessionId, String groupName,
                                                         List<Long> inviteeIds, List<Long> failedIds) {
        List<Long> successIds = new ArrayList<>();

        // 1. Insert every member first
        for (Long inviteeId : inviteeIds) {
            try {
                insertUserSession(sessionId, inviteeId, USER_ROLE_GROUP_MEMBER);
                successIds.add(inviteeId);
            } catch (Exception e) {
                failedIds.add(inviteeId);
                log.error("Failed to add the member to the group, member id {}, error: {}", inviteeId, e.getMessage(), e);
            }
        }

        // 2. Read the owner id and the up-to-date member count (after every insert)
        Long creatorId = getGroupCreatorId(sessionId);
        int membersCount = userSessionService.getGroupMemberCount(sessionId);

        // 3. Build the notification message
        NewGroupSessionNotificationDTO notification = buildNewGroupSessionNotification(groupName, creatorId, membersCount);

        // 4. Publish all the Kafka notifications
        for (Long inviteeId : successIds) {
            try {
                notificationService.pushGroupNewSession(inviteeId, sessionId, notification);
            } catch (Exception e) {
                log.error("Failed to push the group session notification, member id {}, error: {}", inviteeId, e.getMessage(), e);
                // A failed notification does not undo a successful invitation; it is only logged
            }
        }

        return successIds;
    }

    /**
     * Returns the group owner's user id
     *
     * @param sessionId the session id
     * @return the group owner's user id
     */
    private Long getGroupCreatorId(Long sessionId) {
        LambdaQueryWrapper<UserSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSession::getSessionId, sessionId)
                .eq(UserSession::getRole, USER_ROLE_GROUP_OWNER)
                .eq(UserSession::getStatus, SESSION_STATUS_NORMAL);
        UserSession ownerSession = userSessionMapper.selectOne(wrapper);
        ThrowUtils.throwIf(ownerSession == null, ErrorCode.NOT_FOUND_ERROR, "The group owner record does not exist");
        return ownerSession.getUserId();
    }

    /**
     * Inserts the user-session row
     */
    private void insertUserSession(Long sessionId, Long userId, int role) {
        UserSession userSession = new UserSession();
        userSession.setUserId(userId);
        userSession.setSessionId(sessionId);
        userSession.setRole(role);
        userSession.setStatus(SESSION_STATUS_NORMAL);
        userSession.setCreatedTime(new Date());
        userSession.setUpdatedTime(new Date());
        userSessionMapper.insert(userSession);
    }

    /**
     * Builds the notification message for the new group session
     */
    private NewGroupSessionNotificationDTO buildNewGroupSessionNotification(String groupName, Long creatorId, int membersCount) {
        NewGroupSessionNotificationDTO notification = new NewGroupSessionNotificationDTO();
        notification.setSessionName(groupName);
        notification.setAvatar(DEFAULT_GROUP_AVATAR_URL);
        notification.setCreatorId(creatorId);
        notification.setMembersCount(membersCount);
        return notification;
    }
}