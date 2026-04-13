package com.qctv1.iam.auth.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserProfileVo {

    private Long id;
    private String userName;
    private String trueName;
    private String name;
    private String mobile;
    private String email;
    private String roleCode;
    private String status;
    private String logo;
    private String gender;
    private String birth;
    private String tel;
    private String addr;
    private String postCode;
    private String identityType;
    private String identityNum;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime loginTime;
    private List<String> roles;
}
