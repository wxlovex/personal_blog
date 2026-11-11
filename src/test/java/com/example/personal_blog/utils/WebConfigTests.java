package com.example.personal_blog.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class WebConfigTests {
    @Test
    void originsIgnoreBlankEntriesAndNormalizeTrailingSlash() {
        assertThat(WebConfig.parseOrigins(" http://localhost:5173, ,http://127.0.0.1:5173,"
                + "https://bounce-club.onrender.com,https://example.com/ ,"))
                .containsExactly("http://localhost:5173", "http://127.0.0.1:5173",
                        "https://bounce-club.onrender.com", "https://example.com");
        assertThat(WebConfig.parseOrigins(" , ")).isEmpty();
    }

    @Test
    void originsRejectWildcardsAndNonOriginUrls() {
        for (String origin : new String[]{"*", "ftp://x", "https://x/path", "https://x?a=1",
                "https://user:pw@x", "https:///", "https://x#part", "https://*.example.com",
                "https://x:65536", "https://x/%2F", "http://["}) {
            assertThatThrownBy(() -> WebConfig.parseOrigins(origin))
                    .as("来源 %s 应被拒绝", origin).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
