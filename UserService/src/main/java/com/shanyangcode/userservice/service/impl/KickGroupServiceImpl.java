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
 * 踢出群成员服务实现类
 */
@Slf4j
@Service
public class KickGroupServiceImpl implements KickGroupService {

    private final SessionMapper sessionMapper;
    private final UserSessionMapper userSessionMapper;
    private final NotificationService notificationService;

    /**
     * 用户角色常量
     */
    private static final int USER_ROLE_GROUP_OWNER = 0;
    private static final int USER_ROLE_GROUP_ADMIN = 1;
    private static final int USER_ROLE_GROUP_MEMBER = 2;

    /**
     * 会话状态常量
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
     * 踢出群成员
     *
     * @param request 踢出请求
     * @return 踢出结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public KickGroupMembersResponse kickGroupMembers(KickGroupMembersRequest request) {
        Long sessionId = Long.valueOf(request.getSessionId());  // String -> Long
        Long operatorId = request.getOperatorId();
        List<Long> memberIds = request.getMemberIds();

        log.info("开始踢出群成员，sessionId: {}, operatorId: {}, memberIds: {}",
                sessionId, operatorId, memberIds);

        // 1. 参数校验
        validateKickGroupParameters(sessionId, operatorId, memberIds);

        // 2. 校验会话存在且为群聊
        validateSession(sessionId);

        // 3. 获取操作者权限
        UserSession operatorSession = getOperatorSession(sessionId, operatorId);
        int operatorRole = operatorSession.getRole();

        // 4. 查询要踢出的成员信息
        List<UserSession> targetMembers = getTargetMembers(sessionId, memberIds);
        Map<Long, UserSession> memberMap = targetMembers.stream()
                .collect(Collectors.toMap(UserSession::getUserId, us -> us));

        // 5. 校验踢人权限并执行踢出操作
        List<String> successIds = new ArrayList<>();
        for (Long memberId : memberIds) {
            try {
                UserSession targetMember = memberMap.get(memberId);

                // 成员不在群内
                if (targetMember == null) {
                    log.warn("成员ID {} 不在群聊中，跳过", memberId);
                    continue;
                }

                // 不能踢出群主
                if (targetMember.getRole() == USER_ROLE_GROUP_OWNER) {
                    log.warn("不能踢出群主，成员ID: {}", memberId);
                    continue;
                }

                // 管理员只能踢出普通成员
                if (operatorRole == USER_ROLE_GROUP_ADMIN
                        && targetMember.getRole() != USER_ROLE_GROUP_MEMBER) {
                    log.warn("管理员只能踢出普通成员，成员ID: {}, 角色: {}",
                            memberId, targetMember.getRole());
                    continue;
                }

                // 删除用户会话记录（user_session 使用复合主键 user_id + session_id）
                LambdaQueryWrapper<UserSession> deleteWrapper = new LambdaQueryWrapper<>();
                deleteWrapper.eq(UserSession::getUserId, memberId)
                        .eq(UserSession::getSessionId, sessionId);
                userSessionMapper.delete(deleteWrapper);

                successIds.add(String.valueOf(memberId));
                log.info("成功踢出群成员，sessionId: {}, memberId: {}", sessionId, memberId);
            } catch (Exception e) {
                log.error("踢出群成员失败，成员ID: {}，错误信息：{}", memberId, e.getMessage(), e);
            }
        }

        // 6. 推送踢出通知给所有群成员（包括被踢出者）
        if (!successIds.isEmpty()) {
            pushKickNotification(sessionId, operatorId, successIds);
        }

        // 7. 构建响应
        KickGroupMembersResponse response = new KickGroupMembersResponse();
        response.setSuccessIds(successIds);

        log.info("踢出群成员完成，sessionId: {}, 成功数: {}", sessionId, successIds.size());
        return response;
    }

    /* ===================== 私有方法 ===================== */

    /**
     * 校验踢出群成员请求参数的合法性
     */
    private void validateKickGroupParameters(Long sessionId, Long operatorId, List<Long> memberIds) {
        ThrowUtils.throwIf(sessionId == null || sessionId <= 0, ErrorCode.PARAMS_ERROR, "会话ID不能为空");
        ThrowUtils.throwIf(operatorId == null || operatorId <= 0, ErrorCode.PARAMS_ERROR, "操作者ID不能为空");
        ThrowUtils.throwIf(memberIds == null || memberIds.isEmpty(), ErrorCode.PARAMS_ERROR, "成员ID列表不能为空");
    }

    /**
     * 校验会话存在且为群聊类型
     */
    private void validateSession(Long sessionId) {
        LambdaQueryWrapper<Session> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Session::getSessionId, sessionId)
                .eq(Session::getStatus, SESSION_STATUS_NORMAL);
        Session session = sessionMapper.selectOne(wrapper);

        ThrowUtils.throwIf(session == null, ErrorCode.NOT_FOUND_ERROR, "群聊不存在或已解散");
        ThrowUtils.throwIf(!(SessionTypeConstant.GROUP_TYPE == session.getType()),
                ErrorCode.PARAMS_ERROR, "该会话不是群聊");
    }

    /**
     * 获取操作者在群内的会话记录（必须是群主或管理员）
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
                ErrorCode.NO_AUTH_ERROR, "只有群主或管理员才能踢出成员");

        return operatorSession;
    }

    /**
     * 查询待踢出成员在群内的会话记录
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
     * 推送踢出通知
     * <p>
     * 接收者 = 群内剩余成员 + 被踢出的成员（被踢者也必须知道自己被移出）
     */
    private void pushKickNotification(Long sessionId, Long operatorId, List<String> kickedIds) {
        try {
            // 1. 转换被踢出成员 ID 为 Long 类型
            List<Long> kickedMemberIds = kickedIds.stream()
                    .map(Long::valueOf)
                    .collect(Collectors.toList());

            // 2. 查询当前群聊所有成员
            LambdaQueryWrapper<UserSession> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserSession::getSessionId, sessionId)
                    .eq(UserSession::getStatus, SESSION_STATUS_NORMAL);
            List<UserSession> currentMembers = userSessionMapper.selectList(wrapper);

            // 3. 获取所有需要接收通知的用户 ID（当前成员 + 被踢出成员）
            Set<Long> allReceiverIds = new HashSet<>();
            currentMembers.forEach(us -> allReceiverIds.add(us.getUserId()));
            allReceiverIds.addAll(kickedMemberIds);

            // 4. 构建通知 DTO
            GroupKickNotificationDTO notification = new GroupKickNotificationDTO();
            notification.setMemberIds(kickedMemberIds);
            notification.setOperatorId(operatorId);

            // 5. 推送通知给所有接收者
            for (Long receiverId : allReceiverIds) {
                try {
                    notificationService.pushGroupKickNotification(receiverId, sessionId, notification);
                } catch (Exception e) {
                    log.error("推送踢出通知失败，接收者ID: {}, 会话ID: {}, 错误: {}",
                            receiverId, sessionId, e.getMessage());
                }
            }

            log.info("踢出通知推送完成，sessionId: {}, 接收者数量: {}, 被踢出成员: {}",
                    sessionId, allReceiverIds.size(), kickedMemberIds);
        } catch (Exception e) {
            log.error("推送踢出通知失败，sessionId: {}, 错误: {}", sessionId, e.getMessage(), e);
        }
    }
}
