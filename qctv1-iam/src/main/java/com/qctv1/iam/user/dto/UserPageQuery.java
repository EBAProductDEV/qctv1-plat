package com.qctv1.iam.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UserPageQuery {

    @Min(1)
    private long pageNum = 1;

    @Min(1)
    @Max(100)
    private long pageSize = 10;

    private String userName;

    private String trueName;

    private String mobile;

    private String roleCode;

    private String status;
}
