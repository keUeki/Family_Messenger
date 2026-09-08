package com.shanyangcode.userservice.service.impl;

import java.util.Collections;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shanyangcode.common.common.ErrorCode;
import com.shanyangcode.common.constant.SessionTypeConstant;
import com.shanyangcode.common.exception.BusinessException;
import com.shanyangcode.common.exception.ThrowUtils;
import com.shanyangcode.userservice.mapper.SessionMapper;
import com.shanyangcode.userservice.mapper.UserSessionMapper;
import com.shanyangcode.userservice.model.dto.GroupKickNotificationDTO;
import com.shanyangcode.userservice.model.dto.request.GroupExitRequestDTO;
import com.shanyangcode.userservice.model.entity.Session;
import com.shanyangcode.userservice.model.entity.User;
import com.shanyangcode.userservice.model.entity.UserSession;
import com.shanyangcode.userservice.service.ExitGroupService;
import com.shanyangcode.userservice.service.NotificationService;
import com.shanyangcode.userservice.service.UserService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 退出群聊服务实现类
 */
@Slf4j
@Service
public class ExitGroupServiceImpl implements ExitGroupService {

    private final SessionMapper sessionMapper;
    private final UserSessionMapper userSessionMapper;
    private final NotificationService notificationService;
    private final UserService userService;

    /**
     * 用户角色常量
     */
    private static final int USER_ROLE_GROUP_OWNER = 0;

    /**
     * 会话状态常量
     */
    private static final int SESSION_STATUS_NORMAL = 0;

    /**
     * 用户状态常量
     */
    private static final int USER_STATUS_NORMAL = 0;

    public ExitGroupServiceImpl(SessionMapper sessionMapper,
                                UserSessionMapper userSessionMapper,
                                NotificationService notificationService,
                                UserService userService) {
        this.sessionMapper = sessionMapper;
        this.userSessionMapper = userSessionMapper;
        this.notificationService = notificationService;
        this.userService = userService;
    }

    /**
     * 退出群聊
     *
     * @param request 退出群聊请求
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean exitGroup(GroupExitRequestDTO request) {
        Long sessionId = request.getSessionId();
        Long userId = request.getUserId();

        log.info("用户退出群聊，sessionId: {}, userId: {}", sessionId, userId);

        // 1. 参数校验
        validateExitGroupParameters(sessionId, userId);

        // 2. 校验用户存在且状态正常
        validateUser(userId);

        // 3. 校验会话存在且为群聊
        validateSession(sessionId);

        // 4. 查询用户在群内的记录
        LambdaQueryWrapper<UserSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSession::getSessionId, sessionId)
                .eq(UserSession::getUserId, userId)
                .eq(UserSession::getStatus, SESSION_STATUS_NORMAL);
        UserSession userSession = userSessionMapper.selectOne(wrapper);

        ThrowUtils.throwIf(userSession == null, ErrorCode.OPERATION_ERROR, "您不在该群聊中");

        // 5. 群主不能直接退出群聊（需要先转让群主或解散群）
        if (userSession.getRole() == USER_ROLE_GROUP_OWNER) {
            log.warn("群主不能直接退出群聊，需要先转让群主或解散群，userId: {}, sessionId: {}",
                    userId, sessionId);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "群主不能直接退出群聊，请先转让群主或解散群");
        }

        // 6. 删除用户会话记录（user_session 使用复合主键 user_id + session_id）
        LambdaQueryWrapper<UserSession> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(UserSession::getUserId, userId)
                .eq(UserSession::getSessionId, sessionId);
        int deleted = userSessionMapper.delete(deleteWrapper);
        ThrowUtils.throwIf(deleted <= 0, ErrorCode.SYSTEM_ERROR, "退出群聊失败");

        log.info("用户成功退出群聊，sessionId: {}, userId: {}", sessionId, userId);

        // 7. 推送退出通知给群内剩余成员
        pushExitNotification(sessionId, userId);

        return true;
    }

    /* ===================== 私有方法 ===================== */

    /**
     * 校验退出群聊请求参数的合法性
     */
    private void validateExitGroupParameters(Long sessionId, Long userId) {
        ThrowUtils.throwIf(sessionId == null || sessionId <= 0, ErrorCode.PARAMS_ERROR, "会话ID不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
    }

    /**
     * 校验用户存在且状态正常
     */
    private void validateUser(Long userId) {
        User user = userService.getById(userId);
        ThrowUtils.throwIf(user == null || user.getState() != USER_STATUS_NORMAL,
                ErrorCode.NOT_FOUND_ERROR, "用户不存在或状态异常");
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
     * 推送退出通知给群内剩余成员
     * <p>
     * 复用 GroupKickNotificationDTO，operatorId 为 null 表示主动退出而非被踢出。
     */
    private void pushExitNotification(Long sessionId, Long exitUserId) {
        try {
            // 1. 查询当前群聊所有剩余成员（不含退出者，因为已删除）
            LambdaQueryWrapper<UserSession> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserSession::getSessionId, sessionId)
                    .eq(UserSession::getStatus, SESSION_STATUS_NORMAL);
            List<UserSession> remainingMembers = userSessionMapper.selectList(wrapper);

            if (remainingMembers.isEmpty()) {
                log.info("群聊无剩余成员，跳过通知推送，sessionId: {}", sessionId);
                return;
            }

            // 2. 构建通知 DTO（operatorId 为 null 表示主动退出）
            GroupKickNotificationDTO notification = new GroupKickNotificationDTO();
            notification.setMemberIds(Collections.singletonList(exitUserId));
            notification.setOperatorId(null); // 主动退出，无操作者

            // 3. 推送通知给所有剩余成员
            for (UserSession member : remainingMembers) {
                try {
                    notificationService.pushGroupKickNotification(member.getUserId(),
                            sessionId, notification);
                } catch (Exception e) {
                    log.error("推送退出通知失败，接收者ID: {}, 会话ID: {}, 错误: {}",
                            member.getUserId(), sessionId, e.getMessage());
                }
            }

            log.info("退出通知推送完成，sessionId: {}, 退出者: {}, 接收者数量: {}",
                    sessionId, exitUserId, remainingMembers.size());
        } catch (Exception e) {
            log.error("推送退出通知失败，sessionId: {}, 错误: {}", sessionId, e.getMessage(), e);
        }
    }
}
