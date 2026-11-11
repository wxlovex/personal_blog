package com.example.personal_blog.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AccountMailPropertiesTests {
    @Test
    void sslAndRequiredStartTlsCannotBeEnabledTogether() {
        AccountMailProperties properties = new AccountMailProperties();
        properties.setSslEnabled(true);
        properties.setTlsRequired(true);
        assertThatThrownBy(properties::validateTransport).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MAIL_STARTTLS_REQUIRED=false");
        properties.setTlsRequired(false);
        assertThatCode(properties::validateTransport).doesNotThrowAnyException();
    }

    @Test
    void mailLinksRejectUntrustedSchemesCredentialsQueryAndPath() {
        for (String address : new String[]{"http://public.example", "https://user:password@example.com", "https://example.com/path", "https://example.com?next=evil", "javascript:alert(1)"}) {
            AccountMailProperties properties = new AccountMailProperties();
            properties.setPublicBaseUrl(address);
            assertThatThrownBy(properties::validateTransport).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void productionSmtpRequiresEncryptionAndLocalTestSmtpIsAllowed() {
        AccountMailProperties properties = new AccountMailProperties();
        properties.setEnabled(true);
        properties.setSmtpHost("mail.example.com");
        properties.setPublicBaseUrl("https://blog.example.com");
        assertThatThrownBy(properties::validateTransport).isInstanceOf(IllegalArgumentException.class);
        properties.setTlsRequired(true);
        properties.validateTransport();
        properties.setTlsRequired(false);
        properties.setSmtpHost("127.0.0.1");
        properties.setPublicBaseUrl("http://127.0.0.1:18082");
        properties.validateTransport();
    }
}
