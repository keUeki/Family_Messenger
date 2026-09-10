package com.shanyangcode.userservice.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * User table
 * @TableName user
 */
@TableName(value ="user")
@Data
public class User {
    /**
     * User id
     */
    @TableId
    private Long userId;

    /**
     * Phone number
     */
    private String phone;

    /**
     * Email address
     */
    private String email;

    /**
     * Password
     */
    private String password;

    /**
     * Nickname
     */
    private String nickname;

    /**
     * Avatar url
     */
    private String avatar;

    /**
     * Gender: 0 female, 1 male, 2 unknown
     */
    private Integer gender;

    /**
     * Bio
     */
    private String description;

    /**
     * Status: 0 active, 1 banned, 2 deactivated
     */
    private Integer state;

    /**
     * Role: 0 regular user, 1 admin, 2 super admin
     */
    private Integer role;

    /**
     * Creation time
     */
    private Date createdTime;

    /**
     * Last update time
     */
    private Date updatedTime;

    /**
     * Soft-delete flag (0 not deleted, 1 deleted)
     */
    private Integer isDelete;
}