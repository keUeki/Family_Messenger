package com.shanyangcode.userservice.model.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * Friend request DTO
 * <p>
 * Carries a row of the friend request list.
 * The list mixes requests I sent with requests I received, so userId/nickname/avatar
 * always describe the other party, and isReceiver says which side I am on.
 */
@Data
public class ApplyFriendDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The other party's user id (the receiver when I sent it, the sender when I received it)
     */
    private String userId;

    /**
     * The other party's nickname
     */
    private String nickname;

    /**
     * The other party's avatar
     */
    private String avatar;

    /**
     * Message attached to the request
     */
    private String msg;

    /**
     * Request status (0: unread, 1: accepted, 2: rejected, 3: read, 4: expired)
     */
    private Integer status;

    /**
     * Last update time
     */
    private LocalDateTime time;

    /**
     * Whether I am the receiver (0: no, I sent it; 1: yes, I received it)
     */
    private Integer isReceiver;
}
