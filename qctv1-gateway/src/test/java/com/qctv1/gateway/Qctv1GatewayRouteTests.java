package com.qctv1.gateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
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
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "qctv1.gateway.auth.enabled=false"
)
@AutoConfigureWebTestClient
class Qctv1GatewayRouteTests {

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
    void shouldProxyChatRequestAndAttachRequestId() throws Exception {
        CHAT_SERVER.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .setBody("chat-ok"));

        webTestClient.get()
                .uri("/api/ai/chat/simple?query=hello")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-Id")
                .expectBody(String.class).isEqualTo("chat-ok");

        RecordedRequest recordedRequest = CHAT_SERVER.takeRequest(5, TimeUnit.SECONDS);
        assertThat(recordedRequest).isNotNull();
        assertThat(recordedRequest.getPath()).isEqualTo("/chat/simple?query=hello");
        assertThat(recordedRequest.getHeader("X-Request-Id")).isNotBlank();
    }

    @Test
    void shouldProxyRagRequest() throws Exception {
        RAG_SERVER.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .setBody("rag-ok"));

        webTestClient.get()
                .uri("/api/ai/code/list")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("rag-ok");

        RecordedRequest recordedRequest = RAG_SERVER.takeRequest(5, TimeUnit.SECONDS);
        assertThat(recordedRequest).isNotNull();
        assertThat(recordedRequest.getPath()).isEqualTo("/ai/code/list");
    }

    @Test
    void shouldKeepSseStreamingHeaders() throws Exception {
        CHAT_SERVER.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                .setBody("data: hello\n\n"));

        FluxExchangeResult<String> result = webTestClient.get()
                .uri("/api/ai/chat/stream?query=hello")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String.class);

        StepVerifier.create(result.getResponseBody())
                .expectNext("hello")
                .thenCancel()
                .verify();

        RecordedRequest recordedRequest = CHAT_SERVER.takeRequest(5, TimeUnit.SECONDS);
        assertThat(recordedRequest).isNotNull();
        assertThat(recordedRequest.getPath()).isEqualTo("/chat/stream?query=hello");
    }

    @Test
    void shouldAllowCorsPreflight() {
        webTestClient.options()
                .uri("/api/ai/chat/simple")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");
    }

    @Test
    void shouldExposeGatewayActuatorEndpoints() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");

        webTestClient.get()
                .uri("/actuator/gateway/routes")
                .exchange()
                .expectStatus().isOk();
    }
}
