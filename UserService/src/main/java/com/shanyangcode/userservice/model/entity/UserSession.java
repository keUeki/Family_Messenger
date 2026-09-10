package com.shanyangcode.userservice.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * User-session relation table
 * @TableName user_session
 */
@TableName(value ="user_session")
@Data
public class UserSession {
    /**
     * User id
     */

    private Long userId;

    /**
     * Session id
     */

    private Long sessionId;

    /**
     * Role: 0 owner, 1 admin, 2 regular member
     */
    private Integer role;

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
}