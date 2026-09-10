package com.shanyangcode.userservice.service;

import com.shanyangcode.common.model.dto.PageRequest;
import com.shanyangcode.common.model.dto.PageResponse;
import com.shanyangcode.userservice.model.dto.response.UserGroupDTO;

/**
 * User group list service
 */
public interface UserGroupService {

    /**
     * Returns a page of the groups the user belongs to
     *
     * @param userId      the user id
     * @param pageRequest the pagination parameters
     * @return a page of the user's groups
     */
    PageResponse<UserGroupDTO> getUserGroups(Long userId, PageRequest pageRequest);
}
