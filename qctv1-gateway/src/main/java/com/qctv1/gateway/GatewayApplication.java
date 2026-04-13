package com.qctv1.gateway;

import com.qctv1.gateway.config.Qctv1GatewayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关启动入口。
 * 这里统一装配 Spring Boot、服务发现以及自定义配置绑定能力。
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(Qctv1GatewayProperties.class)
public class GatewayApplication {

    public static void main(String[] args) {
        // 网关是 AI 相关接口的统一对外入口。
        // 1. 默认 local 模式下，直接转发到本机已启动的 chat / rag 服务。
        // 2. 切到 nacos 模式后，按服务名发现下游实例。
        // 3. 所有请求都会在这里进入统一治理链路，例如请求 ID、日志、鉴权和 CORS。
        SpringApplication.run(GatewayApplication.class, args);
    }
}
