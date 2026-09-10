package com.shanyangcode.userservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shanyangcode.userservice.model.dto.request.CreateGroupRequest;
import com.shanyangcode.userservice.model.dto.response.CreateGroupResponse;
import com.shanyangcode.userservice.model.entity.Session;


public interface SessionService extends IService<Session> {

    /**
     * Creates a group chat
     *
     * Processing steps:
     * 1. Check that the creator exists and is active
     * 2. Check that every member is a friend of the creator
     * 3. Build the group name by joining the members' nicknames, capped at 16 characters
     * 4. Create the Session row
     * 5. Create the creator's UserSession row (role: owner)
     * 6. Create a UserSession row for every member (role: regular member)
     * 7. Publish a Kafka notification to every member
     *
     * @param request the create-group request
     * @return the outcome (the sessionId, the group name and the members that failed)
     */
    CreateGroupResponse createGroup(CreateGroupRequest request);

}