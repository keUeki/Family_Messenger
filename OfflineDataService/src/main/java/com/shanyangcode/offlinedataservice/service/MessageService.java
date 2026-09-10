package com.shanyangcode.offlinedataservice.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.shanyangcode.common.model.dto.MessageRequest;
import com.shanyangcode.common.model.vo.MessageResponse;
import com.shanyangcode.offlinedataservice.model.dto.HistoryMessageRequest;
import com.shanyangcode.offlinedataservice.model.dto.OfflineMessageRequest;
import com.shanyangcode.offlinedataservice.model.dto.SessionSummaryRequest;
import com.shanyangcode.offlinedataservice.model.entity.Message;

import java.util.List;
import java.util.Map;


public interface MessageService extends IService<Message> {
    void saveMessageToMySQL(MessageRequest messageRequest);

    /**
     * Returns the offline messages
     *
     * @return Map<sessionId, message list>
     */
    Map<Long, List<MessageResponse>> getOfflineMessages(OfflineMessageRequest request);

    /**
     * Returns historical messages, merging the hot (Redis) and cold (MySQL) stores
     */
    List<MessageResponse> getHistoryMessages(HistoryMessageRequest request);

    String getSummary(SessionSummaryRequest sessionSummaryRequest);
}