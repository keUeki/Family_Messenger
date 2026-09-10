package com.shanyangcode.offlinedataservice.controller;

import java.util.List;
import java.util.Map;

import com.shanyangcode.common.common.BaseResponse;
import com.shanyangcode.common.common.ResultUtils;
import com.shanyangcode.common.model.vo.MessageResponse;
import com.shanyangcode.offlinedataservice.model.dto.HistoryMessageRequest;
import com.shanyangcode.offlinedataservice.model.dto.OfflineMessageRequest;

import com.shanyangcode.offlinedataservice.model.dto.SessionSummaryRequest;
import com.shanyangcode.offlinedataservice.service.MessageService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/message")
public class MessageController {

    @Resource
    private MessageService messageService;

    /**
     * Returns the offline messages; called once the user comes online
     * 
     * @return Map<sessionId, List<message>>
     */
    @PostMapping("/offline")
    public BaseResponse<Map<Long, List<MessageResponse>>> getOfflineMessages(
            @RequestBody OfflineMessageRequest request) {
        return ResultUtils.success(messageService.getOfflineMessages(request));
    }

    /**
     * Returns historical messages (scrolling back through the conversation)
     */
    @PostMapping("/history")
    public BaseResponse<List<MessageResponse>> getHistoryMessages(
            @RequestBody HistoryMessageRequest request) {
        return ResultUtils.success(messageService.getHistoryMessages(request));
    }

    @PostMapping("/summary")
    public BaseResponse<String> chatSummary(@RequestBody SessionSummaryRequest sessionSummaryRequest) {
        return ResultUtils.success(messageService.getSummary(sessionSummaryRequest));
    }
}