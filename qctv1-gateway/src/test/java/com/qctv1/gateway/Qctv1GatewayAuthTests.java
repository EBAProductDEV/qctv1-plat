package com.qctv1.gateway;

import com.qctv1.gateway.security.GatewaySessionStore;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "qctv1.gateway.auth.enabled=true",
                "qctv1.gateway.auth.jwt-secret=Qctv1JwtSecretKeyForDevOnlyPleaseChange1234567890",
                "qctv1.gateway.auth.issuer=qctv1-iam"
        }
)
@AutoConfigureWebTestClient
class Qctv1GatewayAuthTests {

    private static final MockWebServer CHAT_SERVER = new MockWebServer();

    private static final MockWebServer RAG_SERVER = new MockWebServer();

    private static final MockWebServer IAM_SERVER = new MockWebServer();

    private static final String SECRET = "Qctv1JwtSecretKeyForDevOnlyPleaseChange1234567890";

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private GatewaySessionStore gatewaySessionStore;

    @BeforeAll
    static void setUp() throws IOException {
        CHAT_SERVER.start();
        RAG_SERVER.start();
        IAM_SERVER.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        CHAT_SERVER.shutdown();
        RAG_SERVER.shutdown();
        IAM_SERVER.shutdown();
    }

    @DynamicPropertySource
    static void routeProperties(DynamicPropertyRegistry registry) {
        registry.add("qctv1.gateway.routes.chat-uri", () -> CHAT_SERVER.url("/").toString());
        registry.add("qctv1.gateway.routes.rag-uri", () -> RAG_SERVER.url("/").toString());
        registry.add("qctv1.gateway.routes.iam-uri", () -> IAM_SERVER.url("/").toString());
    }

    @Test
    void shouldRejectProtectedRouteWithoutToken() {
        webTestClient.get()
                .uri("/api/ai/code/list")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.message").isEqualTo("Missing Authorization header");
    }

    @Test
    void shouldAllowWhitelistedRouteWithoutToken() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldAllowProtectedRouteWithJwtToken() {
        String token = generateAccessToken(1L, "admin", "ADMIN", "session-1");
        when(gatewaySessionStore.getAccessSessionId(1L)).thenReturn(Mono.just("session-1"));

        RAG_SERVER.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .setBody("ok"));

        webTestClient.get()
                .uri("/api/ai/code/list")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("ok");
    }

    @Test
    void shouldRejectProtectedRouteWithUnknownSession() {
        String token = generateAccessToken(1L, "admin", "ADMIN", "session-1");
        when(gatewaySessionStore.getAccessSessionId(1L)).thenReturn(Mono.just("other-session"));

        webTestClient.get()
                .uri("/api/ai/code/list")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden();
    }

    private String generateAccessToken(Long userId, String userName, String roleCode, String sessionId) {
        SecretKey secretKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("qctv1-iam")
                .subject(String.valueOf(userId))
                .claim("userName", userName)
                .claim("roleCode", roleCode)
                .claim("sessionId", sessionId)
                .claim("tokenType", "ACCESS")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(30, ChronoUnit.MINUTES)))
                .signWith(secretKey)
                .compact();
    }
}
