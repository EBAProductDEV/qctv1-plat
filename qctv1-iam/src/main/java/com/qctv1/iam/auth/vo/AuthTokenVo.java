package com.qctv1.iam.auth.vo;

import lombok.Data;

@Data
public class AuthTokenVo {

    private String accessToken;

    private String refreshToken;

    private long expiresIn;

    private UserProfileVo userInfo;
}
