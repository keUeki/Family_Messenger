package com.shanyangcode.userservice.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Password login DTO
 */
@Data
public class UserLoginPasswordRequest {

    /**
     * Email address
     */
    @NotBlank(message = "Email address must not be blank")
    @Email(message = "Email address is not valid")
    private String email;


    /**
     * Password
     */
    @NotBlank(message = "Password must not be blank")
    @Size(min = 6, max = 20, message = "Password must be between 6 and 20 characters")
    private String password;
}