package com.shanyangcode.userservice.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Change-password request
 */
@Data
public class UpdatePasswordRequest {

    @NotBlank(message = "Email address must not be blank")
    @Email(message = "Email address is not valid")
    private String email;

    @NotBlank(message = "Password must not be blank")
    @Size(min = 6, max = 20, message = "Password must be between 6 and 20 characters")
    private String password;

    @NotBlank(message = "Password confirmation must not be blank")
    private String confirmPassword;

    @NotBlank(message = "Verification code must not be blank")
    @Pattern(regexp = "^\\d{6}$", message = "Verification code must be 6 digits")
    private String code;
}