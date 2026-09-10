package com.shanyangcode.userservice.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Verification-code login DTO
 */
@Data
public class UserLoginCodeRequest {

    /**
     * Email address
     */
    @NotBlank(message = "Email address must not be blank")
    @Email(message = "Email address is not valid")
    private String email;


    /**
     * Verification code
     */
    @NotBlank(message = "Verification code must not be blank")
    @Pattern(regexp = "^\\d{6}$", message = "Verification code must be 6 digits")
    private String code;
}


