package com.shanyangcode.userservice.service;

import com.shanyangcode.userservice.model.dto.request.InviteGroupRequest;
import com.shanyangcode.userservice.model.dto.response.InviteGroupResponse;

/**
 * Group service
 *
 * Responsibilities:
 * - Handles group invitations
 */
public interface GroupService {

    /**
     * Invites users to join a group chat
     *
     * Processing steps:
     * 1. Check that the session exists and is a group chat
     * 2. Check the inviter's permission (they must be the owner or an admin)
     * 3. Check that every invitee is a friend of the inviter
     * 4. Check whether an invitee is already in the group
     * 5. Create the UserSession rows
     * 6. Publish the Kafka notifications
     *
     * @param request the invitation request
     * @return the outcome (the successes and the failures)
     */
    InviteGroupResponse inviteGroup(InviteGroupRequest request);
}