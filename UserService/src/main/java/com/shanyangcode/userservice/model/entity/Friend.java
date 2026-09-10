// Friend.java
package com.shanyangcode.userservice.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Friendship entity
 *
 * Responsibilities:
 * - Records the friendships between users
 * - Uses the composite primary key (user_id + friend_id)
 * - Tracks the friendship status (friend, blocked, deleted)
 * - The relation is bidirectional: adding B as A's friend creates two rows
 *
 * Database table: friend
 *
 * Notes:
 * - This table has a composite primary key and no standalone auto-increment id
 */
@Data
@TableName("friend")
@Accessors(chain = true)
public class Friend {

    /**
     * User id (first half of the composite primary key)
     */
    @TableField("user_id")
    private Long userId;

    /**
     * Friend id (second half of the composite primary key)
     */
    @TableField("friend_id")
    private Long friendId;

    /**
     * Friendship status
     * 0: friend (NORMAL) - an ordinary friendship
     * 1: blocked (BLOCKED)
     * 2: deleted (DELETED)
     *
     */
    @TableField("status")
    private Integer status;

    /**
     * Creation time
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /**
     * Last update time
     */
    @TableField("updated_time")
    private LocalDateTime updatedTime;
}