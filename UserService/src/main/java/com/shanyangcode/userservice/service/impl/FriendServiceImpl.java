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
 * Friend service implementation
 * <p>
 * Key design points:
 * - Uses lambda wrappers instead of string-based queries
 * - Uses asynchronous Kafka notifications instead of synchronous HTTP calls
 * - Handles the composite primary key (user_id, friend_id)
 * - The status enum values were changed from 1/2/3 to 0/1/2
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
     * Role within a session: regular member
     */
    private static final int USER_ROLE_NORMAL = 2;

    /**
     * Session status: active
     */
    private static final int SESSION_STATUS_NORMAL = 0;

    /**
     * Key prefix for the friendship-status cache
     * <p>
     * The cross-service message validation path reads this cache, so any change to a friendship must
     * clear both directions; otherwise a blocked or removed user is still treated as allowed until the TTL expires.
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
        ThrowUtils.throwIf(!StringUtils.hasText(keyword), ErrorCode.PARAMS_ERROR, "The search term must not be empty");

        // Detect the keyword type by regex and build the matching predicate
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        if (keyword.matches(UserConstant.PHONE_REGEX)) {
            queryWrapper.eq(User::getPhone, keyword);
        } else if (keyword.matches(UserConstant.EMAIL_REGEX)) {
            queryWrapper.eq(User::getEmail, keyword);
        } else {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Enter a valid phone number or email address");
        }

        User user = userService.getOne(queryWrapper);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "The user does not exist");

        // Load the user's details
        return getFriendDetails(userId, String.valueOf(user.getUserId()));
    }


    /**
     * Returns a friend's details
     *
     * @param userId   the current user's id
     * @param friendId the friend's id
     * @return the FriendDetailVO
     */
    @Override
    public FriendDetailVO getFriendDetails(String userId, String friendId) {
        Long userId1 = parseUserId(userId);
        Long friendId1 = parseUserId(friendId);

        // 1. Load the friend's user record
        User friendUser = userService.getById(friendId1);
        validateFriendUser(friendUser);

        // 2. Build the friend detail VO
        FriendDetailVO friendDetailVO = buildFriendDetailVO(friendUser);

        // 3. Fill in the session id
        populateSessionId(userId1, friendId1, friendDetailVO);

        // 4. Fill in the friendship status
        populateFriendStatus(userId1, friendId1, friendDetailVO);

        return friendDetailVO;
    }


    /**
     * Parses and validates a user id
     *
     * @param userId the user id as a string
     * @return the parsed user id
     */
    private Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Malformed user id");
        }
    }

    /**
     * Checks that the friend exists and is in a usable state
     *
     * @param friendUser the friend's User entity
     */
    private void validateFriendUser(User friendUser) {
        ThrowUtils.throwIf(friendUser == null, ErrorCode.NOT_FOUND_ERROR, "The user does not exist");

        // User status: 0 active, 1 banned, 2 deactivated
        ThrowUtils.throwIf(friendUser.getState() == UserStateEnum.BANNED.getCode(), ErrorCode.FORBIDDEN_ERROR, "That user has been banned");

        ThrowUtils.throwIf(friendUser.getState() == UserStateEnum.CANCELLED.getCode(), ErrorCode.NOT_FOUND_ERROR, "That user has deactivated their account");
    }


    /**
     * Builds the friend detail VO
     *
     * @param friendUser the friend's User entity
     * @return the FriendDetailVO
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
     * Fills the session id into the FriendDetailVO
     *
     * @param userId         the current user's id
     * @param friendId       the friend's id
     * @param friendDetailVO the FriendDetailVO
     */
    private void populateSessionId(Long userId, Long friendId, FriendDetailVO friendDetailVO) {
        // 1. Find the one-to-one session the two users share
        LambdaQueryWrapper<UserSession> userSession1Wrapper = new LambdaQueryWrapper<>();
        userSession1Wrapper.eq(UserSession::getUserId, userId);
        List<UserSession> userSessions1 = userSessionMapper.selectList(userSession1Wrapper);

        LambdaQueryWrapper<UserSession> userSession2Wrapper = new LambdaQueryWrapper<>();
        userSession2Wrapper.eq(UserSession::getUserId, friendId);
        List<UserSession> userSessions2 = userSessionMapper.selectList(userSession2Wrapper);

        // 2. Work out the common session ids
        List<Long> sessionIds1 = userSessions1.stream()
                .map(UserSession::getSessionId)
                .collect(Collectors.toList());
        List<Long> sessionIds2 = userSessions2.stream()
                .map(UserSession::getSessionId)
                .collect(Collectors.toList());

        sessionIds1.retainAll(sessionIds2);

        if (!sessionIds1.isEmpty()) {
            // 3. Keep only the one-to-one sessions
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
     * Fills the friendship status into the FriendDetailVO
     *
     * @param userId         the current user's id
     * @param friendId       the friend's id
     * @param friendDetailVO the FriendDetailVO
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
     * Returns the user's friend list
     * Supports pagination and keyword search
     *
     * @param userId1      the user id
     * @param pageRequest the pagination parameters
     * @param key         the search term
     * @return a page of friend DTOs
     */
    @Override
    public IPage<FriendDTO> getFriends(String userId1, PageRequest pageRequest, String key) {
        Long userId = parseUserId(userId1);
        validateUserId(userId);

        int pageNum = pageRequest.getPageNum();
        int pageSize = pageRequest.getPageSize();

        // 1. Query the friendship rows (using a lambda wrapper)
        LambdaQueryWrapper<Friend> friendWrapper = new LambdaQueryWrapper<>();
        friendWrapper.eq(Friend::getUserId, userId)
                .ne(Friend::getStatus, FriendStatusEnum.DELETED.getCode())
                .orderByDesc(Friend::getCreatedTime); // order by creation time, newest first

        List<Friend> friendList = friendMapper.selectList(friendWrapper);

        // 2. Collect the friend ids from the friendship rows into a new list.
        List<Long> friendIds = friendList.stream() // stream the friendship rows
                .map(Friend::getFriendId) // map each row to its friendId
                .collect(Collectors.toList()); // collect the ids into a List<Long>

        if (friendIds.isEmpty()) {
            // Return an empty page
            Page<FriendDTO> emptyPage = new Page<>(pageNum, pageSize);
            emptyPage.setTotal(0);
            emptyPage.setRecords(List.of());
            return emptyPage;
        }

        // 3. Load the friends' user records
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(User::getUserId, friendIds);

        // Add the search predicate when a keyword was supplied
        if (StringUtils.hasText(key)) {
            userWrapper.and(wrapper -> wrapper
                    .like(User::getNickname, key)
                    .or()
                    .like(User::getPhone, key)
                    .or()
                    .like(User::getUserId, key));
        }

        List<User> users = userService.list(userWrapper); // the friends' user records

        // 4. Build the friend -> session id map (batched, to avoid N+1)
        Map<Long, String> friendSessionMap = buildFriendSessionMap(userId, friendIds);

        // 5. Index the friendship rows by friend id, which keeps this O(n+m) instead of nesting streams; n is the number of friendship rows and m the number of user records loaded
        Map<Long, Friend> friendRelationMap = friendList.stream()
                .collect(Collectors.toMap(Friend::getFriendId, f -> f)); // build a Map keyed by friend id, with the friendship row as the value

        // 6. Build the FriendDTO list
        List<FriendDTO> friendDTOList = users.stream()
                .map(user -> {
                    FriendDTO dto = new FriendDTO();
                    dto.setUserId(String.valueOf(user.getUserId())); // set the DTO id (the friend's user id)
                    dto.setNickname(user.getNickname());
                    dto.setAvatar(user.getAvatar());
                    dto.setSignature(user.getDescription());
                    Friend friendRelation = friendRelationMap.get(user.getUserId()); // look the friendship row up by user id
                    dto.setStatus(friendRelation != null ? friendRelation.getStatus() : FriendStatusEnum.NON_FRIEND.getCode()); // use its status when the row exists, otherwise mark them as not a friend
                    dto.setSessionId(friendSessionMap.get(user.getUserId())); // take the id of the session this user shares with the current user
                    return dto;
                })
                .collect(Collectors.toList());

        // 7. Paginate by hand
        // Create the page object with the page number and page size
        Page<FriendDTO> page = new Page<>(pageNum, pageSize);
        page.setTotal(friendDTOList.size());

        // Work out the index range from the page number, clamped to stay in bounds
        int fromIndex = (pageNum - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, friendDTOList.size());

        // Slice out the current page with subList, returning an empty list when out of range
        if (fromIndex < friendDTOList.size()) {
            page.setRecords(friendDTOList.subList(fromIndex, toIndex));
        } else {
            page.setRecords(List.of());
        }

        return page;
    }


    /**
     * Builds the friend -> session id map in bulk
     * <p>
     * Keeps the query count flat and avoids the N+1 problem
     *
     * @param userId    the current user's id
     * @param friendIds the friend ids
     * @return a map from friend id to session id
     */
    private Map<Long, String> buildFriendSessionMap(Long userId, List<Long> friendIds) {
        Map<Long, String> friendSessionMap = new HashMap<>();

        if (friendIds.isEmpty()) {
            return friendSessionMap;
        }

        // 1. Load every UserSession row for the current user
        LambdaQueryWrapper<UserSession> currentUserSessionWrapper = new LambdaQueryWrapper<>();
        currentUserSessionWrapper.eq(UserSession::getUserId, userId);
        List<UserSession> currentUserSessions = userSessionMapper.selectList(currentUserSessionWrapper);

        if (currentUserSessions.isEmpty()) {
            return friendSessionMap;
        }

        // 2. Collect the current user's session ids
        // turn the currentUserSessions rows above into a list of session ids
        List<Long> currentUserSessionIds = currentUserSessions.stream()
                .map(UserSession::getSessionId)
                .collect(Collectors.toList());

        // 3. Load those sessions and keep only the one-to-one ones (SIGNAL_TYPE)
        LambdaQueryWrapper<Session> sessionWrapper = new LambdaQueryWrapper<>();
        sessionWrapper.in(Session::getSessionId, currentUserSessionIds)
                .eq(Session::getType, SessionTypeConstant.SIGNAL_TYPE);
        List<Session> singleChatSessions = sessionMapper.selectList(sessionWrapper);

        if (singleChatSessions.isEmpty()) {
            return friendSessionMap;
        }

        // 4. Collect the one-to-one session ids
        List<Long> singleChatSessionIds = singleChatSessions.stream()
                .map(Session::getSessionId)
                .collect(Collectors.toList());

        // 5. Load the friends' UserSession rows for those sessions
        LambdaQueryWrapper<UserSession> friendSessionWrapper = new LambdaQueryWrapper<>();
        friendSessionWrapper.in(UserSession::getUserId, friendIds)
                .in(UserSession::getSessionId, singleChatSessionIds);
        List<UserSession> friendUserSessions = userSessionMapper.selectList(friendSessionWrapper);

        // 6. Build the friendId -> sessionId map
        for (UserSession friendUserSession : friendUserSessions) {
            Long friendId = friendUserSession.getUserId();
            Long sessionId = friendUserSession.getSessionId();

            // double-check that the session really is shared by the current user and this friend
            if (currentUserSessionIds.contains(sessionId)) {
                friendSessionMap.put(friendId, String.valueOf(sessionId));
            }
        }

        return friendSessionMap;
    }


    /**
     * Validates a user id
     *
     * @param userId the user id
     */
    private void validateUserId(Long userId) {
        ThrowUtils.throwIf(userId == null || userId < 0, ErrorCode.PARAMS_ERROR, "Invalid user id");
    }


    /**
     * Removes a friend
     * <p>
     * Processing steps:
     * 1. Delete the friend request rows between the two users
     * 2. Delete both directions of the friendship
     * 3. Delete their one-to-one session and the user-session rows
     * 4. Clear the friendship-status cache in both directions
     *
     * @param userId   the current user's id
     * @param friendId the friend's id
     * @return whether the operation succeeded
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteFriend(Long userId, Long friendId) {
        validateUserId(userId);
        validateUserId(friendId);

        // 1. Check that the friendship exists
        LambdaQueryWrapper<Friend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId);
        Friend friend = this.getOne(queryWrapper);

        ThrowUtils.throwIf(friend == null, ErrorCode.NOT_FOUND_ERROR, "That friendship does not exist");

        try {
            // 2. Delete the friend request rows
            deleteApplyFriendRecords(userId, friendId);

            // 3. Delete both directions of the friendship
            deleteFriendRecords(userId, friendId);

            // 4. Delete the one-to-one session
            deleteSessionRecords(userId, friendId);

            // 5. Clear the friendship cache in both directions
            evictFriendCache(userId, friendId);

            return true;
        } catch (Exception e) {
            log.error("Failed to remove the friend, user id: {}, friend id: {}, cause: {}", userId, friendId, e.getMessage(), e);
            throw new RuntimeException("Failed to remove the friend");
        }
    }

    /**
     * Deletes the friend request rows between the two users, in both directions
     *
     * @param userId   the user id
     * @param friendId the friend's id
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
     * Deletes both directions of the friendship
     *
     * @param userId   the user id
     * @param friendId the friend's id
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
     * Deletes the one-to-one session the two users share, and its user-session rows
     *
     * @param userId   the user id
     * @param friendId the friend's id
     */
    private void deleteSessionRecords(Long userId, Long friendId) {
        // 1. Load each user's sessions
        LambdaQueryWrapper<UserSession> userSession1Wrapper = new LambdaQueryWrapper<>();
        userSession1Wrapper.eq(UserSession::getUserId, userId);
        List<UserSession> userSessions1 = userSessionMapper.selectList(userSession1Wrapper);

        LambdaQueryWrapper<UserSession> userSession2Wrapper = new LambdaQueryWrapper<>();
        userSession2Wrapper.eq(UserSession::getUserId, friendId);
        List<UserSession> userSessions2 = userSessionMapper.selectList(userSession2Wrapper);

        // 2. Intersect them to find the sessions they share
        List<Long> commonSessionIds = userSessions1.stream()
                .map(UserSession::getSessionId)
                .collect(Collectors.toList());
        List<Long> sessionIds2 = userSessions2.stream()
                .map(UserSession::getSessionId)
                .collect(Collectors.toList());

        commonSessionIds.retainAll(sessionIds2); // keep only the ids that also appear in sessionIds2

        if (commonSessionIds.isEmpty()) {
            return;
        }

        // 3. Delete only the one-to-one sessions; group sessions are left alone
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

        // 4. Delete the user-session rows first, then the session itself
        LambdaQueryWrapper<UserSession> userSessionDeleteWrapper = new LambdaQueryWrapper<>();
        userSessionDeleteWrapper.in(UserSession::getSessionId, singleChatSessionIds);
        userSessionMapper.delete(userSessionDeleteWrapper);

        LambdaQueryWrapper<Session> sessionDeleteWrapper = new LambdaQueryWrapper<>();
        sessionDeleteWrapper.in(Session::getSessionId, singleChatSessionIds);
        sessionMapper.delete(sessionDeleteWrapper);
    }

    /**
     * Clears the friendship cache in both directions
     * <p>
     * Called whenever a friendship changes, to keep the cache consistent with the database:
     * - after blocking a friend
     * - after unblocking
     * - after removing a friend
     * <p>
     * Both directions must be cleared: miss one and the blocked or removed user is still treated as allowed until the cache TTL expires.
     * A failure to clear the cache does not fail the operation; it is only logged.
     *
     * @param userId   the user id
     * @param friendId the friend's id
     */
    private void evictFriendCache(Long userId, Long friendId) {
        try {
            String key1 = FRIEND_STATUS_KEY_PREFIX + userId + ":" + friendId;
            String key2 = FRIEND_STATUS_KEY_PREFIX + friendId + ":" + userId;
            stringRedisTemplate.delete(Arrays.asList(key1, key2));
            log.info("Friendship cache cleared, user id: {}, friend id: {}", userId, friendId);
        } catch (Exception e) {
            log.warn("Failed to clear the friendship cache, user id: {}, friend id: {}, cause: {}", userId, friendId, e.getMessage());
        }
    }

    /**
     * Blocks a friend
     * <p>
     * Only the current user's side of the relation changes; the other side stays an ordinary friendship.
     *
     * @param userId   the current user's id
     * @param friendId the friend's id
     * @return whether the operation succeeded
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean blockFriend(Long userId, Long friendId) {
        validateUserId(userId);
        validateUserId(friendId);

        // 1. Check that the friendship exists
        LambdaQueryWrapper<Friend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId);
        Friend friend = this.getOne(queryWrapper);

        ThrowUtils.throwIf(friend == null, ErrorCode.NOT_FOUND_ERROR, "That friendship does not exist");

        // 2. Friend has a composite primary key, so updateById does not work and a LambdaUpdateWrapper is required
        LambdaUpdateWrapper<Friend> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Friend::getStatus, FriendStatusEnum.BLOCKED.getCode())
                .set(Friend::getUpdatedTime, LocalDateTime.now())
                .eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId);

        boolean result = this.update(updateWrapper);

        // 3. Clear the friendship cache in both directions
        evictFriendCache(userId, friendId);

        return result;
    }

    /**
     * Unblocks a friend
     *
     * @param userId   the current user's id
     * @param friendId the friend's id
     * @return whether the operation succeeded
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unblockFriend(Long userId, Long friendId) {
        validateUserId(userId);
        validateUserId(friendId);

        // 1. Check that the friendship exists
        LambdaQueryWrapper<Friend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId);
        Friend friend = this.getOne(queryWrapper);

        ThrowUtils.throwIf(friend == null, ErrorCode.NOT_FOUND_ERROR, "That friendship does not exist");
        ThrowUtils.throwIf(friend.getStatus() != FriendStatusEnum.BLOCKED.getCode(),
                ErrorCode.OPERATION_ERROR, "That friend is not blocked");

        // 2. Friend has a composite primary key, so updateById does not work and a LambdaUpdateWrapper is required
        LambdaUpdateWrapper<Friend> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Friend::getStatus, FriendStatusEnum.NORMAL.getCode())
                .set(Friend::getUpdatedTime, LocalDateTime.now())
                .eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId);

        boolean result = this.update(updateWrapper);

        // 3. Clear the friendship cache in both directions
        evictFriendCache(userId, friendId);

        return result;
    }

    /**
     * Establishes a friendship; called when a friend request is accepted
     * <p>
     * Processing steps:
     * 1. Check that the requester exists
     * 2. Check whether they are already friends
     * 3. Create both directions of the friendship
     * 4. Create the one-to-one session and the user-session rows
     * 5. Notify the requester over Kafka
     *
     * @param recipient the party accepting the request
     * @param friendId  the user id of the party who made the request
     * @return the newly created session
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModifyFriendApplicationResponse addFriend(User recipient, Long friendId) {
        validateUserId(friendId);
        ThrowUtils.throwIf(recipient == null, ErrorCode.NOT_FOUND_ERROR, "The receiver does not exist");

        // 1. Check that the requester exists
        User applicant = userService.getById(friendId);
        ThrowUtils.throwIf(applicant == null, ErrorCode.NOT_FOUND_ERROR, "The requester does not exist");

        // 2. Check whether they are already friends
        boolean exists = this.lambdaQuery()
                .eq(Friend::getUserId, recipient.getUserId())
                .eq(Friend::getFriendId, friendId)
                .exists();
        ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "You are already friends");

        // 3. Create both directions of the friendship
        createFriendRelations(recipient.getUserId(), friendId);

        // 4. Create the session
        Long sessionId = createSession();

        // 5. Create the user-session rows
        createUserSessions(recipient.getUserId(), friendId, sessionId);

        // 6. Tell the requester about the new session
        sendNewSessionNotification(friendId, recipient, sessionId);

        // 7. Build the response
        return buildModifyFriendApplicationResponse(applicant, sessionId);
    }

    /**
     * Creates both directions of the friendship
     *
     * @param userId   the current user's id
     * @param friendId the friend's id
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

        // Friend has a composite primary key, so save() takes the updateById branch and does nothing; mapper.insert() must be used directly
        int inserted1 = friendMapper.insert(friend1);
        int inserted2 = friendMapper.insert(friend2);

        ThrowUtils.throwIf(inserted1 <= 0 || inserted2 <= 0, ErrorCode.SYSTEM_ERROR, "Failed to create the friendship");
    }

    /**
     * Creates the one-to-one session
     *
     * @return the session id
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
        ThrowUtils.throwIf(!sessionSaved, ErrorCode.SYSTEM_ERROR, "Failed to create the session");

        return sessionId;
    }

    /**
     * Creates the user-session rows for both users
     *
     * @param userId    the current user's id
     * @param friendId  the friend's id
     * @param sessionId the session id
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

        ThrowUtils.throwIf(!saved1 || !saved2, ErrorCode.SYSTEM_ERROR, "Failed to create the user-session rows");
    }

    /**
     * Publishes the new-session notification over Kafka
     * <p>
     * A failed notification does not undo the friendship; it is only logged.
     *
     * @param recipientId the id of the user receiving the notification (the party who made the request)
     * @param sender      the party who accepted the request
     * @param sessionId   the session id
     */
    private void sendNewSessionNotification(Long recipientId, User sender, Long sessionId) {
        try {
            NewSessionNotificationDTO notification = new NewSessionNotificationDTO();
            notification.setSessionName(sender.getNickname());
            notification.setAvatar(sender.getAvatar());

            notificationService.pushNewSession(sender.getUserId(), recipientId, sessionId,
                    SessionTypeConstant.SIGNAL_TYPE, notification);
            log.info("New-session notification published, receiver id: {}, session id: {}", recipientId, sessionId);
        } catch (Exception e) {
            log.warn("Failed to publish the new-session notification, receiver id: {}, session id: {}, cause: {}",
                    recipientId, sessionId, e.getMessage());
        }
    }

    /**
     * Builds the response returned when a friend request is accepted
     *
     * @param applicant the requester
     * @param sessionId the session id
     * @return the response
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
