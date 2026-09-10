package com.shanyangcode.offlinedataservice.model.dto;

import lombok.Data;

@Data
public class OfflineMessageRequest {
    /**
     * User id
     */
    private Long userId;
    
    /**
     * Offline timestamp, in milliseconds
     */
    private Long offlineTime;
    
}