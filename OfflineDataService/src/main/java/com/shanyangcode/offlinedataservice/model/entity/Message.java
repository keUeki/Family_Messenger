package com.shanyangcode.offlinedataservice.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * Message table
 * @TableName message
 */
@TableName(value ="message")
@Data
public class Message {
    /**
     * Message id
     */
    @TableId
    private Long messageId;

    /**
     * Sender id
     */
    private Long senderId;

    /**
     * Session id
     */
    private Long sessionId;

    /**
     * Message type: 0 text, 1 image, 3 red packet, 4 sticker
     */
    private Integer type;

    /**
     * Message content
     */
    private String content;

    /**
     * Referenced message id
     */
    private Long replyId;

    /**
     * Session type: 0 one-to-one, 1 group
     */
    private Integer sessionType;

    /**
     * Creation time
     */
    private Date createdTime;

    /**
     * Last update time
     */
    private Date updatedTime;
}