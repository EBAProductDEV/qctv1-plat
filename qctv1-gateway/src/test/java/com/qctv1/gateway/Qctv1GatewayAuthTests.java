package com.qctv1.gateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "qctv1.gateway.auth.enabled=true",
                "qctv1.gateway.auth.mock-valid-tokens=test-token"
        }
)
@AutoConfigureWebTestClient
class Qctv1GatewayAuthTests {

    private static final MockWebServer CHAT_SERVER = new MockWebServer();

    private static final MockWebServer RAG_SERVER = new MockWebServer();

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void setUp() throws IOException {
        CHAT_SERVER.start();
        RAG_SERVER.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        CHAT_SERVER.shutdown();
        RAG_SERVER.shutdown();
    }

    @DynamicPropertySource
    static void routeProperties(DynamicPropertyRegistry registry) {
        registry.add("qctv1.gateway.routes.chat-uri", () -> CHAT_SERVER.url("/").toString());
        registry.add("qctv1.gateway.routes.rag-uri", () -> RAG_SERVER.url("/").toString());
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
    void shouldAllowProtectedRouteWithMockToken() {
        RAG_SERVER.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .setBody("ok"));

        webTestClient.get()
                .uri("/api/ai/code/list")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("ok");
    }
}
