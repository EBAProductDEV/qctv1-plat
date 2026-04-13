package com.qctv1.iam;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.qctv1.iam")
public class Qctv1IamApplication {

    public static void main(String[] args) {
        SpringApplication.run(Qctv1IamApplication.class, args);
    }
}
