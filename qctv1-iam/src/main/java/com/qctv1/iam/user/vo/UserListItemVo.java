package com.qctv1.iam.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserListItemVo {

    private Long id;
    private String userName;
    private String trueName;
    private String mobile;
    private String email;
    private String roleCode;
    private String status;
    private LocalDateTime loginTime;
    private LocalDateTime createTime;
}
