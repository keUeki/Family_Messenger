package com.shanyangcode.userservice.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shanyangcode.userservice.model.dto.FriendDTO;
import com.shanyangcode.userservice.model.dto.ModifyFriendApplicationResponse;
import com.shanyangcode.userservice.model.entity.Friend;
import com.shanyangcode.userservice.model.entity.User;
import com.shanyangcode.userservice.model.vo.FriendDetailVO;
import com.shanyangcode.common.model.dto.PageRequest;

/**
 * Friend service
 *
 * Responsibilities:
 * - Manages the whole life cycle of a friendship
 * - Supports adding, removing and blocking friends
 * - Provides friend list and friend detail lookups
 */
public interface FriendService extends IService<Friend> {

    /**
     * Searches for a user by keyword, detecting whether it is a phone number or an email address
     *
     * @param userId  the current user's id
     * @param keyword the search term (phone number or email address)
     * @return the FriendDetailVO
     */
    FriendDetailVO searchUserByKeyword(String userId, String keyword);

    /**
     * Returns a friend's details
     *
     * @param userId   the current user's id
     * @param friendId the friend's id
     * @return the FriendDetailVO
     */
    FriendDetailVO getFriendDetails(String userId, String friendId);

    /**
     * Returns the user's friend list
     *
     * Supports pagination and keyword search
     *
     * @param userId      the user id
     * @param pageRequest the pagination parameters
     * @param key         the search term
     * @return a page of friend DTOs
     */
    IPage<FriendDTO> getFriends(String userId, PageRequest pageRequest, String key);

    /**
     * Removes a friend
     * <p>
     * Deletes both directions of the friendship, the related friend request rows and the one-to-one session, and clears the friendship-status cache in both directions.
     *
     * @param userId   the current user's id
     * @param friendId the friend's id
     * @return whether the operation succeeded
     */
    boolean deleteFriend(Long userId, Long friendId);

    /**
     * Blocks a friend
     * <p>
     * Only changes the current user's side of the relation, then clears the friendship-status cache in both directions.
     *
     * @param userId   the current user's id
     * @param friendId the friend's id
     * @return whether the operation succeeded
     */
    boolean blockFriend(Long userId, Long friendId);

    /**
     * Unblocks a friend
     *
     * @param userId   the current user's id
     * @param friendId the friend's id
     * @return whether the operation succeeded
     */
    boolean unblockFriend(Long userId, Long friendId);

    /**
     * Establishes a friendship; called when a friend request is accepted
     * <p>
     * Creates both directions of the friendship, the one-to-one session and the user-session rows, then notifies the requester over Kafka.
     *
     * @param recipient the party accepting the request (the user who received it)
     * @param friendId  the user id of the party who made the request
     * @return the newly created session
     */
    ModifyFriendApplicationResponse addFriend(User recipient, Long friendId);
}
