package com.shanyangcode.userservice.service;

import com.shanyangcode.common.model.dto.PageRequest;
import com.shanyangcode.common.model.dto.PageResponse;
import com.shanyangcode.userservice.model.dto.response.GroupMemberDTO;

/**
 * Group member lookup service
 */
public interface GetGroupMembersService {

    /**
     * Returns a page of the group's members
     *
     * @param sessionId   the session id (the group id)
     * @param pageRequest the pagination parameters
     * @return a page of group members
     */
    PageResponse<GroupMemberDTO> getGroupMembers(Long sessionId, PageRequest pageRequest);
}
