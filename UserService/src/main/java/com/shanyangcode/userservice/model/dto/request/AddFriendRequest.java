package com.shanyangcode.userservice.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Add-friend request DTO
 */
@Data
public class AddFriendRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Message attached to the request
     */
    @NotBlank(message = "Request message must not be blank")
    private String msg;
}