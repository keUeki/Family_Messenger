package com.shanyangcode.userservice.service;

import com.shanyangcode.userservice.model.dto.request.GroupExitRequestDTO;

/**
 * Leave-group service
 */
public interface ExitGroupService {

    /**
     * Leaves a group chat
     * <p>
     * Processing steps:
     * 1. Validate the parameters
     * 2. Check that the user exists and is active
     * 3. Check that the session exists and is a group chat
     * 4. Check that the user is in the group (the owner cannot simply leave)
     * 5. Delete the user-session row and notify the remaining members
     *
     * @param request the leave-group request
     * @return whether the operation succeeded
     */
    boolean exitGroup(GroupExitRequestDTO request);
}
