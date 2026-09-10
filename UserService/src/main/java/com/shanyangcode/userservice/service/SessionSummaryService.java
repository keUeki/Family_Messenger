package com.shanyangcode.userservice.service;

import com.shanyangcode.userservice.model.dto.response.SessionSummaryDTO;

import java.util.List;

/**
 * Session list service
 */
public interface SessionSummaryService {

    /**
     * Returns every session the user belongs to (one-to-one, group, AI), newest last-message first
     *
     * @param userId the user id
     * @return the session list
     */
    List<SessionSummaryDTO> getUserSessions(Long userId);
}
