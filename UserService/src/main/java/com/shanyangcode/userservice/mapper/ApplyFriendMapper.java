package com.shanyangcode.userservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanyangcode.userservice.model.entity.ApplyFriend;

import org.apache.ibatis.annotations.Mapper;

/**
 * Friend request mapper
 *
 * Notes:
 * - Extends MyBatis-Plus BaseMapper, which supplies the CRUD methods
 * - No XML mapping or hand-written SQL is required
 * - Complex queries are expressed with lambda wrappers in the service layer
 */
@Mapper
public interface ApplyFriendMapper extends BaseMapper<ApplyFriend> {
    // MyBatis-Plus already provides every basic CRUD method
    // Complex queries are expressed with lambda wrappers in the service layer
}