package com.shanyangcode.userservice.service;

import com.shanyangcode.common.model.dto.PageRequest;
import com.shanyangcode.common.model.dto.PageResponse;
import com.shanyangcode.userservice.model.dto.response.GroupMemberDTO;

/**
 * 群成员查询服务接口
 */
public interface GetGroupMembersService {

    /**
     * 分页查询群成员列表
     *
     * @param sessionId   会话ID（群聊ID）
     * @param pageRequest 分页参数
     * @return 群成员分页结果
     */
    PageResponse<GroupMemberDTO> getGroupMembers(Long sessionId, PageRequest pageRequest);
}
