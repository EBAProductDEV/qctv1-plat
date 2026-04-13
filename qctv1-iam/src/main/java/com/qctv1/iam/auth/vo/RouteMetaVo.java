package com.qctv1.iam.auth.vo;

import lombok.Data;

import java.util.Map;

@Data
public class RouteMetaVo {

    private Map<String, String> title;
    private String icon;
    private Integer orderNo;
    private Boolean hidden;
    private String frameSrc;
    private Boolean frameBlank;
}
