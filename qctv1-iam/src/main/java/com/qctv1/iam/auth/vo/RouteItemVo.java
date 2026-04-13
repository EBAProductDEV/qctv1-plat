package com.qctv1.iam.auth.vo;

import lombok.Data;

import java.util.List;

@Data
public class RouteItemVo {

    private String path;
    private String name;
    private String component;
    private String redirect;
    private RouteMetaVo meta;
    private List<RouteItemVo> children;
}
