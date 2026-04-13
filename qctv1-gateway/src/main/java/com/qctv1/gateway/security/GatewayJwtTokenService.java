package com.qctv1.gateway.security;

import com.qctv1.gateway.config.Qctv1GatewayProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * 网关侧 JWT 解析服务。
 * 网关只需要识别 accessToken，不负责签发 token。
 */
@Service
public class GatewayJwtTokenService {

    private static final String CLAIM_USER_NAME = "userName";
    private static final String CLAIM_ROLE_CODE = "roleCode";
    private static final String CLAIM_SESSION_ID = "sessionId";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String TOKEN_TYPE_ACCESS = "ACCESS";

    private final SecretKey secretKey;
    private final String issuer;

    public GatewayJwtTokenService(Qctv1GatewayProperties properties) {
        this.secretKey = Keys.hmacShaKeyFor(properties.getAuth().getJwtSecret().getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.getAuth().getIssuer();
    }

    public TokenPrincipal parseAccessToken(String token) {
        try {
            // 这里先验证签名与 issuer，确保 token 确实由 IAM 发出。
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token);
            Claims claims = jws.getPayload();
            String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
            if (!TOKEN_TYPE_ACCESS.equals(tokenType)) {
                // 网关只接受 accessToken，refreshToken 不能直接拿来访问业务接口。
                return null;
            }
            return new TokenPrincipal(
                    Long.parseLong(claims.getSubject()),
                    claims.get(CLAIM_USER_NAME, String.class),
                    claims.get(CLAIM_ROLE_CODE, String.class),
                    claims.get(CLAIM_SESSION_ID, String.class)
            );
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }
}
