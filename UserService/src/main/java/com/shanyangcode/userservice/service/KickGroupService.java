package com.shanyangcode.userservice.service;

import com.shanyangcode.userservice.model.dto.request.KickGroupMembersRequest;
import com.shanyangcode.userservice.model.dto.response.KickGroupMembersResponse;

/**
 * Remove-group-members service
 */
public interface KickGroupService {

    /**
     * Removes members from a group
     * <p>
     * Processing steps:
     * 1. Validate the parameters
     * 2. Check that the session exists and is a group chat
     * 3. Check that the actor is the owner or an admin
     * 4. Validate and remove each member (the owner cannot be removed; an admin may only remove regular members)
     * 5. Push the removal notification to every group member and to those removed
     *
     * @param request the removal request
     * @return the outcome (the ids of the members actually removed)
     */
    KickGroupMembersResponse kickGroupMembers(KickGroupMembersRequest request);
}
