package com.shanyangcode.userservice.service;

import com.shanyangcode.userservice.model.dto.response.SessionSummaryDTO;

import java.util.List;

/**
 * 会话列表服务
 */
public interface SessionSummaryService {

    /**
     * 查询用户的全部会话（单聊、群聊、AI），按最后一条消息时间倒序
     *
     * @param userId 用户 ID
     * @return 会话列表
     */
    List<SessionSummaryDTO> getUserSessions(Long userId);
}
