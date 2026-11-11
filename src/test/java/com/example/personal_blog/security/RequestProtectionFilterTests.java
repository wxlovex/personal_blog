package com.example.personal_blog.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;

class RequestProtectionFilterTests {
    @Test
    void readAndPreflightRequestsAreNotBufferedAndStillReceiveSecurityHeaders() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setMaxRequestBytes(1024);
        RequestProtectionFilter filter = new RequestProtectionFilter(properties, new ObjectMapper());
        for (String method : new String[]{"GET", "HEAD", "OPTIONS"}) {
            MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/plaza/categories") {
                @Override public jakarta.servlet.ServletInputStream getInputStream() {
                    throw new AssertionError("只读和预检请求不应读取请求体");
                }
            };
            request.setContent(new byte[1025]);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> assertThat(req).isSameAs(request));
            assertThat(response.getHeader("Strict-Transport-Security"))
                    .isEqualTo("max-age=31536000; includeSubDomains");
        }
    }

    @Test
    void everyWriteMethodRejectsOversizedBodies() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setMaxRequestBytes(1024);
        RequestProtectionFilter filter = new RequestProtectionFilter(properties, new ObjectMapper());
        for (String method : new String[]{"POST", "PUT", "PATCH", "DELETE"}) {
            MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/article/1");
            request.setContent(new byte[1025]);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> fail("超限请求不应到达应用"));
            assertThat(response.getStatus()).isEqualTo(413);
        }
    }

    @Test
    void unknownContentLengthCannotBypassTheByteLimit() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setMaxRequestBytes(1024);
        RequestProtectionFilter filter = new RequestProtectionFilter(properties, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/article/publish") {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        request.setContent(new byte[1025]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("Oversized chunked body reached the application"));
        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    void aBodyAtTheLimitIsPreservedForTheController() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setMaxRequestBytes(1024);
        RequestProtectionFilter filter = new RequestProtectionFilter(properties, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/article/publish");
        byte[] body = "日记".repeat(170).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        request.setContent(body);
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertThat(req.getInputStream().readAllBytes()).isEqualTo(body));
    }
}
