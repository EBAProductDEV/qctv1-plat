package com.qctv1.iam.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    private String userName;

    @NotBlank
    private String trueName;

    @NotBlank
    private String mobile;

    @Email
    private String email;

    @NotBlank
    private String password;

    @NotBlank
    private String confirmPassword;
}
