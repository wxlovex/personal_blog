package com.example.personal_blog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "server.forward-headers-strategy=NATIVE", "blog.security.reads-per-minute=1"})
@ActiveProfiles("test")
class UntrustedProxyIntegrationTests {
    @LocalServerPort int port;

    @Test
    void nativeModeWithoutATrustedProxyStillRejectsHeaderSpoofing() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        for (int i = 0; i < 2; i++) {
            int status = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/user/security-options"))
                    .header("X-Forwarded-For", "198.51.100." + i).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode();
            assertThat(status).isEqualTo(i == 0 ? 200 : 429);
        }
    }
}
