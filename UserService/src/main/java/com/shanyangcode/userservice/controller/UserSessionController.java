package com.shanyangcode.userservice.controller;

import com.shanyangcode.common.common.BaseResponse;
import com.shanyangcode.common.common.ResultUtils;
import com.shanyangcode.userservice.model.dto.response.SessionSummaryDTO;
import com.shanyangcode.userservice.service.SessionSummaryService;
import com.shanyangcode.userservice.service.UserSessionService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user")
public class UserSessionController {

    @Resource
    private UserSessionService userSessionService;

    @Resource
    private SessionSummaryService sessionSummaryService;

    @GetMapping("/get/receivers")
    List<Long> getUserIdBySessionId(@RequestParam("sessionId") Long sessionId) {
        return userSessionService.getUserIdBySessionId(sessionId);
    }

    @GetMapping("/get/sessions")
    List<Long> getSessionIdsByUserId(@RequestParam("userId")  Long userId) {
        return userSessionService.getSessionIdsByUserId(userId);
    }

    /**
     * Returns the user's session list (the left-hand list on the chat page)
     *
     * @param userId the user id
     * @return the session list, newest last-message first
     */
    @GetMapping("/sessions")
    public BaseResponse<List<SessionSummaryDTO>> getUserSessions(@RequestParam("userId") Long userId) {
        return ResultUtils.success(sessionSummaryService.getUserSessions(userId));
    }
}