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
 * 群组Controller
 * <p>
 * 功能说明：
 * - 提供群组相关的REST API接口
 * - 包含创建群聊、邀请成员、踢出成员、退出群聊、查询成员等功能
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
     * 创建群聊
     * <p>
     * 同时映射 "" 与 "/"：Spring Boot 3 默认不再匹配尾部斜杠，
     * 而前端 api/group.ts 使用 POST /api/group/ 调用该接口。
     */
    @PostMapping({"", "/"})
    public BaseResponse<?> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        try {
            CreateGroupResponse response = sessionService.createGroup(request);
            return ResultUtils.success(response);
        } catch (BusinessException e) {
            log.error("创建群聊失败，原因：{}", e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("创建群聊失败，原因：{}", e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }


    /**
     * 群聊邀请接口
     */
    @PostMapping("invite")
    public BaseResponse<?> inviteGroup(@Valid @RequestBody InviteGroupRequest request) {
        try {
            InviteGroupResponse response = groupService.inviteGroup(request);
            return ResultUtils.success(response);
        } catch (BusinessException e) {
            log.error("群聊邀请失败，原因：{}", e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("群聊邀请失败，原因：{}", e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }


    /**
     * 踢出群成员
     */
    @PostMapping("/kick")
    public BaseResponse<?> kickGroupMembers(
            @Valid @RequestBody KickGroupMembersRequest request) {
        try {
            KickGroupMembersResponse response = kickGroupService.kickGroupMembers(request);
            return ResultUtils.success(response);
        } catch (BusinessException e) {
            log.error("踢出群成员失败，原因：{}", e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("踢出群成员失败，原因：{}", e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }


    /**
     * 退出群聊
     */
    @PostMapping("/exit")
    public BaseResponse<?> exitGroup(@Valid @RequestBody GroupExitRequestDTO request) {
        try {
            boolean success = exitGroupService.exitGroup(request);
            return ResultUtils.success(success);
        } catch (BusinessException e) {
            log.error("退出群聊失败，原因：{}", e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("退出群聊失败，原因：{}", e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }


    /**
     * 分页查询群成员
     */
    @GetMapping("/{sessionId}/members")
    public BaseResponse<?> getGroupMembers(
            @PathVariable("sessionId") Long sessionId,
            @Valid PageRequest pageRequest) {
        try {
            PageResponse<GroupMemberDTO> response = getGroupMembersService.getGroupMembers(sessionId, pageRequest);
            return ResultUtils.success(response);
        } catch (BusinessException e) {
            log.error("获取群成员失败，sessionId：{}，原因：{}", sessionId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("获取群成员失败，sessionId：{}，原因：{}", sessionId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }


    /**
     * 分页查询用户加入的群聊
     */
    @GetMapping("/user/{userId}")
    public BaseResponse<?> getUserGroups(
            @PathVariable("userId") Long userId,
            @Valid PageRequest pageRequest) {
        try {
            PageResponse<UserGroupDTO> response = userGroupService.getUserGroups(userId, pageRequest);
            return ResultUtils.success(response);
        } catch (BusinessException e) {
            log.error("获取用户群聊列表失败，userId：{}，原因：{}", userId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("获取用户群聊列表失败，userId：{}，原因：{}", userId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }


    /**
     * 查询群成员数量
     */
    @GetMapping("/{sessionId}/count")
    public BaseResponse<?> getGroupMemberCount(@PathVariable("sessionId") Long sessionId) {
        try {
            int count = userSessionService.getGroupMemberCount(sessionId);
            return ResultUtils.success(new GroupMemberCountResponse(count));
        } catch (BusinessException e) {
            log.error("获取群聊人数失败，sessionId：{}，原因：{}", sessionId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("获取群聊人数失败，sessionId：{}，原因：{}", sessionId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

}
