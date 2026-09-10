package com.shanyangcode.userservice.model.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * Group removal/leave notification DTO
 * <p>
 * Scenario: a member is removed from a group, or leaves it of their own accord
 * <p>
 * Note: fields such as sessionId and sessionType live at the top level of SystemNotificationMessage;
 * this DTO is only the content of SystemNotificationMessage.body.
 */
@Data
public class GroupKickNotificationDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Ids of the members who left the group
     */
    private List<Long> memberIds;

    /**
     * User id of the actor
     * <p>
     * The owner's or admin's id when someone was removed; null when the member left voluntarily.
     */
    private Long operatorId;
}
