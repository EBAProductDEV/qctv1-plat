package com.qctv1.iam.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateUserRequest {

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

    private String trueNameEn;

    private String gender;

    private String birth;

    private String tel;

    private String addr;

    private String postCode;

    private String identityType;

    private String identityNum;

    private String logo;

    private String remark;

    private String status;

    private String roleCode;
}
