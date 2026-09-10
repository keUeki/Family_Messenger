package com.shanyangcode.userservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanyangcode.userservice.model.entity.Friend;

import org.apache.ibatis.annotations.Mapper;

/**
 * Friendship mapper
 *
 * Notes:
 * - Extends MyBatis-Plus BaseMapper, which supplies the CRUD methods
 * - The friend table uses the composite primary key (user_id, friend_id)
 * - No XML mapping or hand-written SQL is required
 * - Complex queries are expressed with lambda wrappers in the service layer
 */
@Mapper
public interface FriendMapper extends BaseMapper<Friend> {
    // MyBatis-Plus already provides every basic CRUD method
    // Complex queries are expressed with lambda wrappers in the service layer
}