package com.example.personal_blog.common.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTests {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedLogsMethodAndUriWithoutQueryOrBody(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/example");
        request.setQueryString("credential=private-query-marker");
        request.setContent("private-body-marker".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(handler.unexpected(new IllegalStateException("测试异常"), request).getStatusCode().value())
                .isEqualTo(500);
        assertThat(output.getOut()).contains("method=POST uri=/api/example")
                .doesNotContain("private-query-marker", "private-body-marker");
    }

    @Test
    void integrityConflictLogsOnlyTheExceptionType(CapturedOutput output) {
        assertThat(handler.conflict(new DataIntegrityViolationException("private-field-marker"))
                .getStatusCode().value()).isEqualTo(409);
        assertThat(output.getOut()).contains("WARN", "DataIntegrityViolationException")
                .doesNotContain("private-field-marker");
    }
}
