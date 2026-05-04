package com.qctv1.iam.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "qctv1.iam")
public class IamProperties {

    private final Jwt jwt = new Jwt();

    @Data
    public static class Jwt {
        private String issuer;
        private String secret;
        private long accessExpireMinutes = 720;
        private long refreshExpireDays = 30;
    }
}
