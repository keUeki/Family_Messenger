package com.shanyangcode.userservice.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * Session table
 * @TableName session
 */
@TableName(value ="session")
@Data
public class Session {
    /**
     * Session id
     */
    @TableId
    private Long sessionId;

    /**
     * Name
     */
    private String name;

    /**
     * Kind: 0 one-to-one, 1 group, 2 AI
     */
    private Integer type;

    /**
     * Status: 0 active, 1 deleted
     */
    private Integer status;

    /**
     * Creation time
     */
    private Date createdTime;

    /**
     * Last update time
     */
    private Date updatedTime;


    /**
     * Session avatar
     */
    private String avatar;

}