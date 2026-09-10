package com.shanyangcode.offlinedataservice.model.dto;

import lombok.Data;

@Data
public class HistoryMessageRequest {
    /**
     * Session id
     */
    private Long sessionId;
    
    /**
     * Return messages older than this timestamp, in milliseconds
     */
    private Long beforeTime;
    
    /**
     * Page size; defaults to 20
     */
    private Integer limit = 20;
}