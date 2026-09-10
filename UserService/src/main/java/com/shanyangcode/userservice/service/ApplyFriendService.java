package com.shanyangcode.userservice.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shanyangcode.common.model.dto.PageRequest;
import com.shanyangcode.userservice.model.dto.ApplyFriendDTO;
import com.shanyangcode.userservice.model.dto.ModifyFriendApplicationResponse;
import com.shanyangcode.userservice.model.entity.ApplyFriend;

/**
 * Friend request service
 *
 * Responsibilities:
 * - Manages the whole life cycle of a friend request
 * - Supports creating, querying and updating requests
 * - Integrates the Kafka notification and expiry mechanisms
 */
public interface ApplyFriendService extends IService<ApplyFriend> {

    /**
     * Sends a friend request
     * <p>
     * Processing steps:
     * 1. Check that both the sender and the receiver exist
     * 2. Check whether they are already friends
     * 3. Check for an existing pending request
     *   4a. None: insert a new request row and asynchronously emit the Kafka events (notification + expiry)
     *   4b. One exists and was accepted: report that they are already friends
     *   4c. One exists in another state (read / rejected / expired): reuse the row, reset the status to UNREAD, update the message, and asynchronously emit the Kafka events (notification + expiry)
     * 5. Return the applyFriendId
     *
     * @param senderId   the sender's user id
     * @param receiverId the receiver's user id
     * @param message    the request message
     * @return the friend request id
     */
    Long sendFriendRequest(Long senderId, Long receiverId, String message);

    /**
     * Returns a page of the friend requests involving this user, including the other party's details
     * <p>
     * The list mixes requests I sent with requests I received;
     * the isReceiver field on each row says which side I am on.
     *
     * @param userId      the user id
     * @param pageRequest the pagination parameters
     * @return a page of request DTOs
     */
    IPage<ApplyFriendDTO> getReceivedRequestsWithUserInfo(Long userId, PageRequest pageRequest);

    /**
     * Returns the number of unread friend requests
     * <p>
     * Counts only the requests I received that are still unread.
     *
     * @param userId the user id
     * @return the unread count
     */
    int getUnreadCount(Long userId);

    /**
     * Updates the status of one or more friend requests
     * <p>
     * Only accept (1), reject (2) and read (3) are allowed;
     * accept and reject take exactly one senderId, while read accepts several.
     *
     * @param receiverId the receiver's user id (the current actor)
     * @param senderIds  the user ids of the request senders
     * @param status     the target status code
     * @return the newly created session when a request is accepted, otherwise null
     */
    ModifyFriendApplicationResponse modifyApplicationStatus(Long receiverId, List<Long> senderIds, Integer status);
}
