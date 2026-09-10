package com.shanyangcode.userservice.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shanyangcode.common.common.ErrorCode;
import com.shanyangcode.common.constant.SessionTypeConstant;
import com.shanyangcode.common.exception.ThrowUtils;
import com.shanyangcode.common.utils.SnowflakeUtil;
import com.shanyangcode.userservice.constants.FriendStatusEnum;
import com.shanyangcode.userservice.mapper.FriendMapper;
import com.shanyangcode.userservice.mapper.SessionMapper;
import com.shanyangcode.userservice.mapper.UserSessionMapper;
import com.shanyangcode.userservice.model.dto.NewGroupSessionNotificationDTO;
import com.shanyangcode.userservice.model.dto.request.CreateGroupRequest;
import com.shanyangcode.userservice.model.dto.response.CreateGroupResponse;
import com.shanyangcode.userservice.model.entity.Friend;
import com.shanyangcode.userservice.model.entity.Session;
import com.shanyangcode.userservice.model.entity.User;
import com.shanyangcode.userservice.model.entity.UserSession;
import com.shanyangcode.userservice.service.NotificationService;
import com.shanyangcode.userservice.service.SessionService;
import com.shanyangcode.userservice.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Session service implementation
 *
 * Key design points:
 * - Uses lambda wrappers instead of string-based queries
 * - Uses asynchronous Kafka notifications instead of synchronous HTTP calls
 * - Uses SnowflakeUtil instead of Snowflake
 */
@Slf4j
@Service
public class SessionServiceImpl extends ServiceImpl<SessionMapper, Session>
        implements SessionService {

    private final UserService userService;
    private final FriendMapper friendMapper;
    private final UserSessionMapper userSessionMapper;
    private final SessionMapper sessionMapper;
    private final NotificationService notificationService;

    /**
     * User role constants
     */
    private static final int USER_ROLE_GROUP_OWNER = 0; // group owner
    private static final int USER_ROLE_GROUP_MEMBER = 2; // group member

    /**
     * Session status constants
     */
    private static final int SESSION_STATUS_NORMAL = 0;

    /**
     * User status constants
     */
    private static final int USER_STATUS_NORMAL = 0;

    /**
     * Default group avatar URL
     */
    private static final String DEFAULT_GROUP_AVATAR_URL = "https://video.shanyangcode.com/image/default/A9C9C83CCCE043EC8253DB5D7545DCB4-6-2.png";

    public SessionServiceImpl(UserService userService,
                              FriendMapper friendMapper,
                              UserSessionMapper userSessionMapper,
                              SessionMapper sessionMapper,
                              NotificationService notificationService) {
        this.userService = userService;
        this.friendMapper = friendMapper;
        this.userSessionMapper = userSessionMapper;
        this.sessionMapper = sessionMapper;
        this.notificationService = notificationService;
    }

    /**
     * Creates a group chat
     *
     * @param request the create-group request
     * @return the outcome
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateGroupResponse createGroup(CreateGroupRequest request) {
        Long creatorId = request.getCreatorId();
        List<Long> memberIds = request.getMemberIds();
        List<String> failedMemberIds = new ArrayList<>();

        // Validate the parameters
        validateCreateGroupParameters(creatorId, memberIds);

        // Confirm the creator exists and is active
        getActiveUserById(creatorId);

        // Check the friendships and collect the eligible member ids
        List<Long> validMemberIds = validateAndFilterMembers(creatorId, memberIds, failedMemberIds);

        ThrowUtils.throwIf(validMemberIds.isEmpty(), ErrorCode.OPERATION_ERROR, "There are no eligible friends to add to the group");

        // Generate the sessionId
        Long sessionId = SnowflakeUtil.nextId();

        // Build the group name
        String groupName = generateGroupName(creatorId, validMemberIds);

        // Insert into the session table
        Session session = createSession(sessionId, groupName);
        sessionMapper.insert(session);

        // Insert into user_session - the creator
        insertUserSession(sessionId, creatorId, USER_ROLE_GROUP_OWNER);

        // Member count = the eligible members plus the owner
        int membersCount = validMemberIds.size() + 1;

        // Build the new-group-session notification
        NewGroupSessionNotificationDTO notification = buildNewGroupSessionNotification(creatorId, groupName, membersCount);

        // Insert into user_session for the other members and publish the Kafka notifications
        insertMembersAndPushNotifications(validMemberIds, sessionId, notification);

        // Build the response
        CreateGroupResponse response = new CreateGroupResponse();
        BeanUtils.copyProperties(notification, response);
        response.setCreatorId(String.valueOf(creatorId));
        response.setSessionId(String.valueOf(sessionId));
        response.setSessionType(SessionTypeConstant.GROUP_TYPE);
        response.setFailedMemberIds(failedMemberIds);
        return response;
    }

    /* ===================== Private helpers ===================== */

    /**
     * Validates the create-group request parameters
     */
    private void validateCreateGroupParameters(Long creatorId, List<Long> memberIds) {
        ThrowUtils.throwIf(creatorId == null, ErrorCode.PARAMS_ERROR, "Creator id must not be empty");
        ThrowUtils.throwIf(memberIds == null || memberIds.isEmpty(), ErrorCode.PARAMS_ERROR, "The member id list must not be empty");
    }

    /**
     * Checks the user's status
     */
    private void getActiveUserById(Long userId) {
        User user = userService.getById(userId);
        ThrowUtils.throwIf(user == null || user.getState() != USER_STATUS_NORMAL,
                ErrorCode.NOT_FOUND_ERROR, "The user does not exist or is not active");
    }

    /**
     * Validates and filters the member ids, returning the eligible ones
     */
    private List<Long> validateAndFilterMembers(Long creatorId, List<Long> memberIds, List<String> failedMemberIds) {
        // Load every friend id of the creator
        LambdaQueryWrapper<Friend> friendWrapper = new LambdaQueryWrapper<>();
        friendWrapper.eq(Friend::getUserId, creatorId)
                .eq(Friend::getStatus, FriendStatusEnum.NORMAL.getCode());
        List<Friend> friends = friendMapper.selectList(friendWrapper);

        Set<Long> friendIdSet = friends.stream()
                .map(Friend::getFriendId)
                .collect(Collectors.toSet());

        List<Long> validMemberIds = new ArrayList<>();

        for (Long memberId : memberIds) {
            if (friendIdSet.contains(memberId)) {
                validMemberIds.add(memberId);
            } else {
                failedMemberIds.add(String.valueOf(memberId));
                log.info("Member {} is not a friend of the creator and cannot join the group", memberId);
            }
        }

        return validMemberIds;
    }

    /**
     * Builds the group name, capped at 16 characters
     */
    private String generateGroupName(Long creatorId, List<Long> memberIds) {
        StringBuilder groupNameBuilder = new StringBuilder();
        List<Long> allMemberIds = new ArrayList<>(memberIds);
        allMemberIds.add(0, creatorId); // make sure the owner's id comes first

        // Load every user record
        List<User> users = userService.listByIds(allMemberIds);

        // Build an id -> User map so the ordering stays under our control
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getUserId, user -> user));

        // Join the names in the order of allMemberIds
        for (Long memberId : allMemberIds) {
            User user = userMap.get(memberId);
            if (user != null) {
                if (!groupNameBuilder.isEmpty()) {
                    groupNameBuilder.append(", ");
                }
                groupNameBuilder.append(user.getNickname());
                if (groupNameBuilder.length() >= 16) {
                    groupNameBuilder.setLength(16); // keep the first 16 characters
                    break;
                }
            }
        }
        return groupNameBuilder.toString();
    }

    /**
     * Creates the session object
     */
    private Session createSession(Long sessionId, String groupName) {
        Session session = new Session();
        session.setSessionId(sessionId);
        session.setName(groupName);
        session.setType(SessionTypeConstant.GROUP_TYPE);
        session.setStatus(SESSION_STATUS_NORMAL);
        session.setAvatar(DEFAULT_GROUP_AVATAR_URL);
        session.setCreatedTime(new Date());
        session.setUpdatedTime(new Date());
        return session;
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
    private NewGroupSessionNotificationDTO buildNewGroupSessionNotification(Long creatorId, String groupName, int membersCount) {
        NewGroupSessionNotificationDTO notification = new NewGroupSessionNotificationDTO();
        notification.setSessionName(groupName);
        notification.setAvatar(DEFAULT_GROUP_AVATAR_URL);
        notification.setCreatorId(creatorId);
        notification.setMembersCount(membersCount);
        return notification;
    }

    /**
     * Inserts the members and publishes the Kafka notifications
     */
    private void insertMembersAndPushNotifications(List<Long> memberIds, Long sessionId,
                                                   NewGroupSessionNotificationDTO notification) {
        for (Long memberId : memberIds) {
            // Insert the user-session row
            insertUserSession(sessionId, memberId, USER_ROLE_GROUP_MEMBER);

            // Publish the Kafka notification
            try {
                notificationService.pushGroupNewSession(memberId, sessionId, notification);
            } catch (Exception e) {
                log.error("Failed to push the group session notification, member id {}, error: {}", memberId, e.getMessage());
            }
        }
    }
}
