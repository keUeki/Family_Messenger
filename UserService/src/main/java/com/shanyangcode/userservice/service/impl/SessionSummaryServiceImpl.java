package com.shanyangcode.userservice.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shanyangcode.common.common.ErrorCode;
import com.shanyangcode.common.constant.MessageTypeConstant;
import com.shanyangcode.common.constant.SessionTypeConstant;
import com.shanyangcode.common.enums.UserSessionStatusEnum;
import com.shanyangcode.common.exception.ThrowUtils;
import com.shanyangcode.common.model.entity.Message;
import com.shanyangcode.common.utils.FormatDateUtil;
import com.shanyangcode.userservice.mapper.MessageMapper;
import com.shanyangcode.userservice.mapper.SessionMapper;
import com.shanyangcode.userservice.mapper.UserMapper;
import com.shanyangcode.userservice.mapper.UserSessionMapper;
import com.shanyangcode.userservice.model.dto.response.SessionSummaryDTO;
import com.shanyangcode.userservice.model.entity.Session;
import com.shanyangcode.userservice.model.entity.User;
import com.shanyangcode.userservice.model.entity.UserSession;
import com.shanyangcode.userservice.service.SessionSummaryService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 会话列表服务实现类
 */
@Slf4j
@Service
public class SessionSummaryServiceImpl implements SessionSummaryService {

    /**
     * 图片消息在会话列表中的预览文案
     */
    private static final String IMAGE_PREVIEW = "[图片]";

    /**
     * 表情消息在会话列表中的预览文案
     */
    private static final String EMOJI_PREVIEW = "[表情]";

    private final UserSessionMapper userSessionMapper;
    private final SessionMapper sessionMapper;
    private final UserMapper userMapper;
    private final MessageMapper messageMapper;

    public SessionSummaryServiceImpl(UserSessionMapper userSessionMapper,
                                     SessionMapper sessionMapper,
                                     UserMapper userMapper,
                                     MessageMapper messageMapper) {
        this.userSessionMapper = userSessionMapper;
        this.sessionMapper = sessionMapper;
        this.userMapper = userMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public List<SessionSummaryDTO> getUserSessions(Long userId) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户ID不能为空");

        // 1. 查询用户参与的全部会话
        List<Long> sessionIds = listJoinedSessionIds(userId);
        if (sessionIds.isEmpty()) {
            log.info("用户没有任何会话，userId: {}", userId);
            return Collections.emptyList();
        }

        // 2. 过滤出仍然正常的会话
        LambdaQueryWrapper<Session> sessionWrapper = new LambdaQueryWrapper<>();
        sessionWrapper.in(Session::getSessionId, sessionIds)
                .eq(Session::getStatus, UserSessionStatusEnum.NORMAL.getCode());
        List<Session> sessions = sessionMapper.selectList(sessionWrapper);
        if (sessions.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> validSessionIds = sessions.stream().map(Session::getSessionId).toList();

        // 3. 单聊和 AI 会话需要展示对方的昵称与头像
        Map<Long, Long> peerMap = resolvePeerMap(userId, sessions);
        Map<Long, User> peerUserMap = loadUsers(peerMap.values());

        // 4. 每个会话的最后一条消息
        Map<Long, Message> latestMessageMap = loadLatestMessages(validSessionIds);

        // 5. 组装并按最后活跃时间倒序
        List<SessionSummaryDTO> result = new ArrayList<>(sessions.size());
        for (Session session : sessions) {
            result.add(convert(session, peerMap.get(session.getSessionId()), peerUserMap,
                    latestMessageMap.get(session.getSessionId())));
        }
        result.sort(Comparator.comparing(SessionSummaryDTO::getLastMsgTime,
                Comparator.nullsLast(Comparator.reverseOrder())));

        log.info("查询用户会话列表成功，userId: {}, 会话数: {}", userId, result.size());
        return result;
    }

    /* ===================== 私有方法 ===================== */

    /**
     * 查询用户加入的所有会话 ID
     */
    private List<Long> listJoinedSessionIds(Long userId) {
        LambdaQueryWrapper<UserSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSession::getUserId, userId)
                .eq(UserSession::getStatus, UserSessionStatusEnum.NORMAL.getCode())
                .select(UserSession::getSessionId);
        return userSessionMapper.selectList(wrapper).stream()
                .map(UserSession::getSessionId)
                .distinct()
                .toList();
    }

    /**
     * 为单聊 / AI 会话找出对方的用户 ID，群聊不需要
     */
    private Map<Long, Long> resolvePeerMap(Long userId, List<Session> sessions) {
        Set<Long> pairSessionIds = sessions.stream()
                .filter(session -> !isGroup(session))
                .map(Session::getSessionId)
                .collect(Collectors.toSet());
        if (pairSessionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        LambdaQueryWrapper<UserSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(UserSession::getSessionId, pairSessionIds)
                .ne(UserSession::getUserId, userId)
                .eq(UserSession::getStatus, UserSessionStatusEnum.NORMAL.getCode())
                .select(UserSession::getSessionId, UserSession::getUserId);

        Map<Long, Long> peerMap = new LinkedHashMap<>();
        for (UserSession member : userSessionMapper.selectList(wrapper)) {
            peerMap.putIfAbsent(member.getSessionId(), member.getUserId());
        }
        return peerMap;
    }

    /**
     * 批量加载对方的用户资料
     */
    private Map<Long, User> loadUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> distinctIds = new HashSet<>(userIds);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(User::getUserId, distinctIds);
        return userMapper.selectList(wrapper).stream()
                .collect(Collectors.toMap(User::getUserId, user -> user, (a, b) -> a));
    }

    /**
     * 批量加载每个会话的最后一条消息
     */
    private Map<Long, Message> loadLatestMessages(List<Long> sessionIds) {
        if (sessionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return messageMapper.selectLatestBySessionIds(sessionIds).stream()
                .collect(Collectors.toMap(Message::getSessionId, message -> message, (a, b) -> a));
    }

    private boolean isGroup(Session session) {
        return session.getType() != null && session.getType() == SessionTypeConstant.GROUP_TYPE;
    }

    /**
     * 组装单个会话列表项
     */
    private SessionSummaryDTO convert(Session session, Long peerId,
                                      Map<Long, User> peerUserMap, Message latestMessage) {
        SessionSummaryDTO dto = new SessionSummaryDTO();
        dto.setSessionId(String.valueOf(session.getSessionId()));
        dto.setSessionType(session.getType());
        // 未读数由前端结合离线消息计算，这里统一返回 0
        dto.setCount(0);

        if (isGroup(session)) {
            dto.setName(session.getName());
            dto.setAvatar(session.getAvatar());
        } else {
            User peer = peerId == null ? null : peerUserMap.get(peerId);
            dto.setPeerId(peerId == null ? null : String.valueOf(peerId));
            dto.setName(peer != null ? peer.getNickname() : session.getName());
            dto.setAvatar(peer != null ? peer.getAvatar() : session.getAvatar());
        }

        if (latestMessage != null) {
            dto.setType(latestMessage.getType());
            dto.setSenderId(latestMessage.getSenderId() == null
                    ? null : String.valueOf(latestMessage.getSenderId()));
            dto.setLastMsgContent(previewOf(latestMessage));
            dto.setLastMsgTime(latestMessage.getCreatedTime() == null
                    ? null : FormatDateUtil.formatDate(latestMessage.getCreatedTime()));
        } else {
            dto.setType(MessageTypeConstant.TEXT_MESSAGE);
            dto.setLastMsgContent("");
            dto.setLastMsgTime(session.getCreatedTime() == null
                    ? null : FormatDateUtil.formatDate(session.getCreatedTime()));
        }
        return dto;
    }

    /**
     * 生成会话列表中展示的消息预览
     */
    private String previewOf(Message message) {
        Integer type = message.getType();
        String content = message.getContent() == null ? "" : message.getContent();
        if (type == null) {
            return content;
        }
        if (type == MessageTypeConstant.IMAGE_MESSAGE) {
            return IMAGE_PREVIEW;
        }
        if (type == MessageTypeConstant.EMOJI_MESSAGE) {
            return content.isEmpty() ? EMOJI_PREVIEW : content;
        }
        return content;
    }
}
