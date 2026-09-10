package com.shanyangcode.userservice.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * User registration DTO
 */
@Data
public class UserRegisterRequest {

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

    @Size(max = 50, message = "Nickname must not exceed 50 characters")
    private String nickname;

}