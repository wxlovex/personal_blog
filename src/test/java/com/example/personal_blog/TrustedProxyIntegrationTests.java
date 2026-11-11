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
        "server.address=127.0.0.1", "server.forward-headers-strategy=NATIVE",
        "server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.1", "blog.security.reads-per-minute=1"})
@ActiveProfiles("test")
class TrustedProxyIntegrationTests {
    @LocalServerPort int port;

    @Test
    void trustedProxySeparatesClientsAndIgnoresForgedLeftmostAddresses() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        assertThat(read(client, "192.0.2.10, 198.51.100.1")).isEqualTo(200);
        assertThat(read(client, "198.51.100.2")).isEqualTo(200);
        assertThat(read(client, "192.0.2.99, 198.51.100.1")).isEqualTo(429);
    }

    private int read(HttpClient client, String forwardedFor) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/user/security-options"))
                .header("X-Forwarded-For", forwardedFor).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
