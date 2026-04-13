package com.qctv1.iam.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("base_user")
public class BaseUser implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String userName;
    private String trueName;
    private String trueNameEn;
    private String mobile;
    private String email;
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
    private String loginIp;
    private String isDeleted;
    private Long createUserId;
    private LocalDateTime createTime;
    private Long updateUserId;
    private LocalDateTime updateTime;
    private String passwordHash;
    private String roleCode;
    private LocalDateTime loginTime;
    private LocalDateTime passwordUpdateTime;
}
