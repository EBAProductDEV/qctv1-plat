package com.qctv1.iam.auth.service;

import com.qctv1.iam.common.BusinessException;
import com.qctv1.iam.config.IamProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 发放与解析服务。
 * 这里只关心 token 本身的签发和验签，不关心“该 token 现在是否还应该有效”，
 * 会话是否有效由 RedisSessionService 再做一次补充校验。
 */
@Service
public class JwtTokenService {

    public static final String CLAIM_USER_NAME = "userName";
    public static final String CLAIM_ROLE_CODE = "roleCode";
    public static final String CLAIM_SESSION_ID = "sessionId";
    public static final String CLAIM_TOKEN_TYPE = "tokenType";
    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private final IamProperties properties;

    private final SecretKey secretKey;

    public JwtTokenService(IamProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public TokenPair issueTokens(Long userId, String userName, String roleCode) {
        // 每次签发都生成新的 sessionId，让同一账号后登录可以覆盖前登录。
        String sessionId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant accessExpireAt = now.plus(properties.getJwt().getAccessExpireMinutes(), ChronoUnit.MINUTES);
        Instant refreshExpireAt = now.plus(properties.getJwt().getRefreshExpireDays(), ChronoUnit.DAYS);
        String accessToken = buildToken(userId, userName, roleCode, sessionId, TOKEN_TYPE_ACCESS, accessExpireAt);
        String refreshToken = buildToken(userId, userName, roleCode, sessionId, TOKEN_TYPE_REFRESH, refreshExpireAt);
        return new TokenPair(accessToken, refreshToken, sessionId, accessExpireAt.toEpochMilli());
    }

    public TokenClaims parse(String token) {
        try {
            // 这里只验证签名、issuer 和过期时间，业务层再根据 tokenType / sessionId 做细分判断。
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(properties.getJwt().getIssuer())
                    .build()
                    .parseSignedClaims(token);
            Claims claims = jws.getPayload();
            return new TokenClaims(
                    Long.parseLong(claims.getSubject()),
                    claims.get(CLAIM_USER_NAME, String.class),
                    claims.get(CLAIM_ROLE_CODE, String.class),
                    claims.get(CLAIM_SESSION_ID, String.class),
                    claims.get(CLAIM_TOKEN_TYPE, String.class)
            );
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(401, "Token is invalid or expired");
        }
    }

    private String buildToken(
            Long userId,
            String userName,
            String roleCode,
            String sessionId,
            String tokenType,
            Instant expireAt
    ) {
        // accessToken 和 refreshToken 共用同一套 claims，只通过 tokenType 和过期时间区分用途。
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.getJwt().getIssuer())
                .subject(String.valueOf(userId))
                .claim(CLAIM_USER_NAME, userName)
                .claim(CLAIM_ROLE_CODE, roleCode)
                .claim(CLAIM_SESSION_ID, sessionId)
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(secretKey)
                .compact();
    }

    public record TokenPair(String accessToken, String refreshToken, String sessionId, long accessExpireAtMillis) {
    }

    public record TokenClaims(Long userId, String userName, String roleCode, String sessionId, String tokenType) {
    }
}
