package com.qctv1.iam.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateUserStatusRequest {

    @NotBlank
    private String status;
}
