package com.shanyangcode.userservice.controller;

import com.shanyangcode.common.common.BaseResponse;
import com.shanyangcode.common.common.ErrorCode;
import com.shanyangcode.common.common.ResultUtils;
import com.shanyangcode.common.exception.BusinessException;
import com.shanyangcode.common.model.dto.PageRequest;
import com.shanyangcode.common.model.dto.PageResponse;
import com.shanyangcode.userservice.model.dto.request.CreateGroupRequest;
import com.shanyangcode.userservice.model.dto.request.GroupExitRequestDTO;
import com.shanyangcode.userservice.model.dto.request.InviteGroupRequest;
import com.shanyangcode.userservice.model.dto.request.KickGroupMembersRequest;
import com.shanyangcode.userservice.model.dto.response.CreateGroupResponse;
import com.shanyangcode.userservice.model.dto.response.GroupMemberCountResponse;
import com.shanyangcode.userservice.model.dto.response.GroupMemberDTO;
import com.shanyangcode.userservice.model.dto.response.InviteGroupResponse;
import com.shanyangcode.userservice.model.dto.response.KickGroupMembersResponse;
import com.shanyangcode.userservice.model.dto.response.UserGroupDTO;
import com.shanyangcode.userservice.service.ExitGroupService;
import com.shanyangcode.userservice.service.GetGroupMembersService;
import com.shanyangcode.userservice.service.GroupService;
import com.shanyangcode.userservice.service.KickGroupService;
import com.shanyangcode.userservice.service.SessionService;
import com.shanyangcode.userservice.service.UserGroupService;
import com.shanyangcode.userservice.service.UserSessionService;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Group controller
 * <p>
 * Responsibilities:
 * - Exposes the REST API for groups
 * - Covers creating a group, inviting and removing members, leaving, and listing members
 */
@Slf4j
@RestController
@RequestMapping("api/group")
public class GroupController {

    private final SessionService sessionService;
    private final GroupService groupService;
    private final KickGroupService kickGroupService;
    private final ExitGroupService exitGroupService;
    private final GetGroupMembersService getGroupMembersService;
    private final UserGroupService userGroupService;
    private final UserSessionService userSessionService;

    public GroupController(SessionService sessionService,
                           GroupService groupService,
                           KickGroupService kickGroupService,
                           ExitGroupService exitGroupService,
                           GetGroupMembersService getGroupMembersService,
                           UserGroupService userGroupService,
                           UserSessionService userSessionService) {
        this.sessionService = sessionService;
        this.groupService = groupService;
        this.kickGroupService = kickGroupService;
        this.exitGroupService = exitGroupService;
        this.getGroupMembersService = getGroupMembersService;
        this.userGroupService = userGroupService;
        this.userSessionService = userSessionService;
    }

    /**
     * Creates a group chat
     * <p>
     * Mapped at both "" and "/": Spring Boot 3 no longer matches a trailing slash by default,
     * while the frontend's api/group.ts calls this endpoint as POST /api/group/.
     */
    @PostMapping({"", "/"})
    public BaseResponse<?> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        try {
            CreateGroupResponse response = sessionService.createGroup(request);
            return ResultUtils.success(response);
        } catch (BusinessException e) {
            log.error("Failed to create the group, cause: {}", e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create the group, cause: {}", e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }


    /**
     * Invites members to a group chat
     */
    @PostMapping("invite")
    public BaseResponse<?> inviteGroup(@Valid @RequestBody InviteGroupRequest request) {
        try {
            InviteGroupResponse response = groupService.inviteGroup(request);
            return ResultUtils.success(response);
        } catch (BusinessException e) {
            log.error("Group invitation failed, cause: {}", e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Group invitation failed, cause: {}", e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }


    /**
     * Removes members from a group
     */
    @PostMapping("/kick")
    public BaseResponse<?> kickGroupMembers(
            @Valid @RequestBody KickGroupMembersRequest request) {
        try {
            KickGroupMembersResponse response = kickGroupService.kickGroupMembers(request);
            return ResultUtils.success(response);
        } catch (BusinessException e) {
            log.error("Failed to remove the group members, cause: {}", e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed to remove the group members, cause: {}", e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }


    /**
     * Leaves a group chat
     */
    @PostMapping("/exit")
    public BaseResponse<?> exitGroup(@Valid @RequestBody GroupExitRequestDTO request) {
        try {
            boolean success = exitGroupService.exitGroup(request);
            return ResultUtils.success(success);
        } catch (BusinessException e) {
            log.error("Failed to leave the group, cause: {}", e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed to leave the group, cause: {}", e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }


    /**
     * Returns the group members, paginated
     */
    @GetMapping("/{sessionId}/members")
    public BaseResponse<?> getGroupMembers(
            @PathVariable("sessionId") Long sessionId,
            @Valid PageRequest pageRequest) {
        try {
            PageResponse<GroupMemberDTO> response = getGroupMembersService.getGroupMembers(sessionId, pageRequest);
            return ResultUtils.success(response);
        } catch (BusinessException e) {
            log.error("Failed to load the group members, sessionId: {}, cause: {}", sessionId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed to load the group members, sessionId: {}, cause: {}", sessionId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }


    /**
     * Returns the groups the user belongs to, paginated
     */
    @GetMapping("/user/{userId}")
    public BaseResponse<?> getUserGroups(
            @PathVariable("userId") Long userId,
            @Valid PageRequest pageRequest) {
        try {
            PageResponse<UserGroupDTO> response = userGroupService.getUserGroups(userId, pageRequest);
            return ResultUtils.success(response);
        } catch (BusinessException e) {
            log.error("Failed to load the user's group list, userId: {}, cause: {}", userId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed to load the user's group list, userId: {}, cause: {}", userId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }


    /**
     * Returns the number of group members
     */
    @GetMapping("/{sessionId}/count")
    public BaseResponse<?> getGroupMemberCount(@PathVariable("sessionId") Long sessionId) {
        try {
            int count = userSessionService.getGroupMemberCount(sessionId);
            return ResultUtils.success(new GroupMemberCountResponse(count));
        } catch (BusinessException e) {
            log.error("Failed to count the group members, sessionId: {}, cause: {}", sessionId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed to count the group members, sessionId: {}, cause: {}", sessionId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

}
