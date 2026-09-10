package com.shanyangcode.userservice.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shanyangcode.common.common.BaseResponse;
import com.shanyangcode.common.common.ErrorCode;
import com.shanyangcode.common.common.ResultUtils;
import com.shanyangcode.common.model.dto.PageRequest;
import com.shanyangcode.common.exception.BusinessException;
import com.shanyangcode.common.model.dto.PageResponse;
import com.shanyangcode.userservice.model.dto.ApplyFriendDTO;
import com.shanyangcode.userservice.model.dto.FriendDTO;
import com.shanyangcode.userservice.model.dto.ModifyFriendApplicationResponse;
import com.shanyangcode.userservice.model.dto.request.AddFriendRequest;
import com.shanyangcode.userservice.model.dto.request.ModifyFriendApplicationRequest;
import com.shanyangcode.userservice.model.vo.FriendDetailVO;
import com.shanyangcode.userservice.service.ApplyFriendService;
import com.shanyangcode.userservice.service.FriendService;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Contacts controller
 * <p>
 * Responsibilities:
 * - Exposes the REST API for friends
 * - Covers friend requests, friend management and friend lookups
 * - Supports paginated queries and keyword search
 */
@Slf4j
@RestController
@RequestMapping("/api/contact")
public class ContactController {
    private final FriendService friendService;
    private final ApplyFriendService applyFriendService;

    public ContactController(FriendService friendService, ApplyFriendService applyFriendService) {
        this.friendService = friendService;
        this.applyFriendService = applyFriendService;
    }

/**
 * Searches for a user by phone number or email address
 *
 * @param userId  the user id
 * @param keyword the search term (phone number or email address)
 * @return the user's details
 */
    @GetMapping("/{userId}/user/search")
    public BaseResponse<?> searchUser(
            @PathVariable("userId") String userId,
            @RequestParam(value = "keyword") String keyword) {
        try {
            FriendDetailVO friendDetail = friendService.searchUserByKeyword(userId, keyword);
            return ResultUtils.success(friendDetail);
        } catch (BusinessException e) {
            log.error("User search failed, user id: {}, keyword: {}, cause: {}", userId, keyword, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("User search failed, user id: {}, keyword: {}, cause: {}", userId, keyword, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * Returns the contact list
     *
     * @param userId      the user id
     * @param pageRequest the pagination parameters
     * @param key         the search term
     * @return the paginated contact list
     */
    @GetMapping("/{userId}/friend")
    public BaseResponse<?> getFriends(
            @PathVariable("userId") String userId,
            PageRequest pageRequest,
            @RequestParam(value = "key", defaultValue = "") String key) {
        try {
            // Run the paginated query
            IPage<FriendDTO> friendsPage = friendService.getFriends(userId, pageRequest, key);

            // Wrap it in PageResponse for a consistent shape
            return ResultUtils.success(PageResponse.of(friendsPage));
        } catch (BusinessException e) {
            log.error("Failed to load the friend list, user id: {}, cause: {}", userId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed to load the friend list, user id: {}, cause: {}", userId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * Sends a friend request
     *
     * @param userId        the sender's user id
     * @param receiveuserId the receiver's user id
     * @param request       the request payload
     * @return whether the operation succeeded
     */
    @PostMapping("/{userId}/friend/{receiveuserId}")
    public BaseResponse<?> sendFriendRequest(
            @PathVariable("userId") String userId,
            @PathVariable("receiveuserId") String receiveuserId,
            @Valid @RequestBody AddFriendRequest request) {
        try {
            Long senderId = Long.valueOf(userId);
            Long receiverId = Long.valueOf(receiveuserId);
            Long applyFriendId = applyFriendService.sendFriendRequest(senderId, receiverId, request.getMsg());
            return ResultUtils.success(applyFriendId != null);
        } catch (NumberFormatException e) {
            log.error("Failed to send the friend request, malformed user id, sender: {}, receiver: {}", userId, receiveuserId);
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "Malformed user id");
        } catch (BusinessException e) {
            log.error("Failed to send the friend request, sender: {}, receiver: {}, cause: {}", userId, receiveuserId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed to send the friend request, sender: {}, receiver: {}, cause: {}", userId, receiveuserId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * Returns the friend request list
     * <p>
     * Returns both the requests I sent and the ones I received; isReceiver tells them apart.
     *
     * @param userId      the user id
     * @param pageRequest the pagination parameters
     * @return the paginated request list
     */
    @GetMapping("/{userId}/apply")
    public BaseResponse<?> getApplyList(
            @PathVariable("userId") Long userId,
            PageRequest pageRequest) {
        try {
            IPage<ApplyFriendDTO> applyFriendDTOPage =
                    applyFriendService.getReceivedRequestsWithUserInfo(userId, pageRequest);

            // Wrap it in PageResponse for a consistent shape
            return ResultUtils.success(PageResponse.of(applyFriendDTOPage));
        } catch (BusinessException e) {
            log.error("Failed to load the friend request list, user id: {}, cause: {}", userId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed to load the friend request list, user id: {}, cause: {}", userId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * Returns the number of unread friend requests
     *
     * @param userId the user id
     * @return an object shaped like {"count": 3}
     */
    @GetMapping("/{userId}/applyCount")
    public BaseResponse<?> getUnreadApplyCount(@PathVariable("userId") Long userId) {
        try {
            int count = applyFriendService.getUnreadCount(userId);
            Map<String, Integer> result = new HashMap<>();
            result.put("count", count);
            return ResultUtils.success(result);
        } catch (BusinessException e) {
            log.error("Failed to count the unread friend requests, user id: {}, cause: {}", userId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed to count the unread friend requests, user id: {}, cause: {}", userId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * Removes a friend
     *
     * @param userId        the user id
     * @param receiveuserId the id of the friend being removed
     * @return whether the operation succeeded
     */
    @DeleteMapping("/{userId}/friend/{receiveuserId}")
    public BaseResponse<?> deleteFriend(
            @PathVariable("userId") String userId,
            @PathVariable("receiveuserId") String receiveuserId) {
        try {
            Long currentUserId = Long.valueOf(userId);
            Long friendId = Long.valueOf(receiveuserId);
            boolean result = friendService.deleteFriend(currentUserId, friendId);
            return ResultUtils.success(result);
        } catch (NumberFormatException e) {
            log.error("Failed to remove the friend, malformed user id, user: {}, friend: {}", userId, receiveuserId);
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "Malformed user id");
        } catch (BusinessException e) {
            log.error("Failed to remove the friend, user: {}, friend: {}, cause: {}", userId, receiveuserId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed to remove the friend, user: {}, friend: {}, cause: {}", userId, receiveuserId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * Blocks a friend
     *
     * @param userId        the user id
     * @param receiveuserId the id of the friend being blocked
     * @return whether the operation succeeded
     */
    @PostMapping("/{userId}/block/{receiveuserId}")
    public BaseResponse<?> blockFriend(
            @PathVariable("userId") String userId,
            @PathVariable("receiveuserId") String receiveuserId) {
        try {
            Long currentUserId = Long.valueOf(userId);
            Long friendId = Long.valueOf(receiveuserId);
            boolean result = friendService.blockFriend(currentUserId, friendId);
            return ResultUtils.success(result);
        } catch (NumberFormatException e) {
            log.error("Failed to block the friend, malformed user id, user: {}, friend: {}", userId, receiveuserId);
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "Malformed user id");
        } catch (BusinessException e) {
            log.error("Failed to block the friend, user: {}, friend: {}, cause: {}", userId, receiveuserId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed to block the friend, user: {}, friend: {}, cause: {}", userId, receiveuserId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * Unblocks a friend
     *
     * @param userId        the user id
     * @param receiveuserId the id of the friend being unblocked
     * @return whether the operation succeeded
     */
    @DeleteMapping("/{userId}/block/{receiveuserId}")
    public BaseResponse<?> unblockFriend(
            @PathVariable("userId") String userId,
            @PathVariable("receiveuserId") String receiveuserId) {
        try {
            Long currentUserId = Long.valueOf(userId);
            Long friendId = Long.valueOf(receiveuserId);
            boolean result = friendService.unblockFriend(currentUserId, friendId);
            return ResultUtils.success(result);
        } catch (NumberFormatException e) {
            log.error("Failed to unblock the friend, malformed user id, user: {}, friend: {}", userId, receiveuserId);
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "Malformed user id");
        } catch (BusinessException e) {
            log.error("Failed to unblock the friend, user: {}, friend: {}, cause: {}", userId, receiveuserId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed to unblock the friend, user: {}, friend: {}, cause: {}", userId, receiveuserId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * Updates the status of a friend request
     *
     * @param userId  the current user's id (the receiver of the request)
     * @param status  the target status (1: accept, 2: reject, 3: mark as read)
     * @param request the ids of the request senders
     * @return the newly created session when accepting, otherwise true
     */
    @PostMapping("/{userId}/application/{status}")
    public BaseResponse<?> modifyFriendApplicationStatus(
            @PathVariable("userId") String userId,
            @PathVariable("status") Integer status,
            @Valid @RequestBody ModifyFriendApplicationRequest request) {
        try {
            Long receiverId = Long.valueOf(userId);
            List<Long> senderIds = request.getReceiveuserIds().stream()
                    .map(Long::valueOf)
                    .collect(Collectors.toList());

            ModifyFriendApplicationResponse response =
                    applyFriendService.modifyApplicationStatus(receiverId, senderIds, status);

            // Return the session when the request is accepted, otherwise true
            return ResultUtils.success(response != null ? response : true);
        } catch (NumberFormatException e) {
            log.error("Failed to update the friend request status, malformed user id, user: {}", userId);
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "Malformed user id");
        } catch (IllegalArgumentException e) {
            // FriendApplicationStatusEnum.fromCode throws on an unknown status code
            log.error("Failed to update the friend request status, invalid status, user: {}, status: {}", userId, status);
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "That status value is not allowed");
        } catch (BusinessException e) {
            log.error("Failed to update the friend request status, user: {}, status: {}, cause: {}", userId, status, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed to update the friend request status, user: {}, status: {}, cause: {}", userId, status, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * Returns a friend's details
     *
     * @param userId   the user id
     * @param friendId the friend's id
     * @return the friend's details
     */
    @GetMapping("/{userId}/friend/{friendId}")
    public BaseResponse<?> getFriendDetail(
            @PathVariable("userId") String userId,
            @PathVariable("friendId") String friendId) {
        try {
            FriendDetailVO friendDetail = friendService.getFriendDetails(userId, friendId);
            return ResultUtils.success(friendDetail);
        } catch (BusinessException e) {
            log.error("Failed to load the friend's details, user: {}, friend: {}, cause: {}", userId, friendId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed to load the friend's details, user: {}, friend: {}, cause: {}", userId, friendId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }
}
