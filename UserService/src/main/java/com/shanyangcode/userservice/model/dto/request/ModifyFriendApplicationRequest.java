package com.shanyangcode.userservice.model.dto.request;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * Friend-request update DTO
 */
@Data
public class ModifyFriendApplicationRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * User ids of the senders whose requests are being acted on
     * (accept and reject take exactly one; marking as read accepts several)
     */
    @NotEmpty(message = "Receiver id list must not be empty")
    private List<String> receiveuserIds;
}
