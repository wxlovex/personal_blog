package com.example.personal_blog;

import com.example.personal_blog.dto.RegisterDTO;
import com.example.personal_blog.security.AuthSessionService;
import com.example.personal_blog.security.SecurityProperties;
import com.example.personal_blog.service.UserService;
import com.example.personal_blog.utils.JwtUtil;
import com.example.personal_blog.mapper.AuthSessionMapper;
import com.example.personal_blog.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:security;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,COMMENT;IGNORECASE=TRUE",
        "blog.security.max-request-bytes=2048", "blog.security.login-per-minute=2",
        "blog.security.account-login-per-minute=3", "blog.security.registration-per-hour=2",
        "blog.security.writes-per-minute=3", "blog.security.reads-per-minute=5"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityProtectionTests {
    private static final String PASSWORD = "security-test-password";
    private static final AtomicInteger sequence = new AtomicInteger();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserService users;
    @Autowired AuthSessionService sessions;
    @Autowired JwtUtil jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired SecurityProperties properties;
    @Autowired AuthSessionMapper sessionMapper;
    @Autowired UserMapper userMapper;

    record Account(long id, String username, String token) {}
    private Account account() {
        String username = "sec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long id = users.register(new RegisterDTO(username, PASSWORD, null)).id();
        return new Account(id, username, sessions.issue(id, username));
    }
    private String peer() { return "198.51.100." + sequence.incrementAndGet(); }
    private ResultActions send(MockHttpServletRequestBuilder request, String peer, Object payload) throws Exception {
        request.with(r -> { r.setRemoteAddr(peer); return r; });
        if (payload != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(payload));
        return mvc.perform(request);
    }
    private Map<String, String> credentials(Account account, String password) {
        return Map.of("username", account.username(), "password", password);
    }

    @Test
    void logoutRevokesOnlyTheCurrentSessionOnBothApiPrefixes() throws Exception {
        for (String prefix : new String[]{"", "/api"}) {
            Account account = account();
            String other = sessions.issue(account.id(), account.username());
            assertThat(other).isNotEqualTo(account.token());
            send(post(prefix + "/user/logout").header("Authorization", "Bearer " + account.token()), peer(), null)
                    .andExpect(status().isOk());
            for (String readPrefix : new String[]{"", "/api"}) {
                send(get(readPrefix + "/user/me").header("Authorization", "Bearer " + account.token()), peer(), null)
                        .andExpect(status().isUnauthorized());
            }
            send(get("/api/user/me").header("Authorization", "Bearer " + other), peer(), null).andExpect(status().isOk());
            assertThatThrownBy(() -> sessions.authenticate(account.token())).hasMessageContaining("失效");
        }
    }

    @Test
    void signedButUnregisteredTokensAndDatabaseExpiredSessionsAreRejected() throws Exception {
        Account account = account();
        String unregistered = jwt.generateToken(account.id(), account.username());
        send(get("/api/user/me").header("Authorization", "Bearer " + unregistered), peer(), null)
                .andExpect(status().isUnauthorized());
        jdbc.update("UPDATE auth_session SET expires_at = ? WHERE user_id = ?",
                java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(1), account.id());
        send(get("/api/article/list").header("Authorization", "Bearer " + account.token()), peer(), null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionStateSurvivesServiceRecreationAndSessionStorageIsBounded() {
        Account account = account();
        // Wall clocks may have millisecond resolution. Make the oldest-session fixture unambiguous.
        jdbc.update("UPDATE auth_session SET created_at = ? WHERE user_id = ?",
                java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(1), account.id());
        AuthSessionService recreated = new AuthSessionService(sessionMapper, userMapper, jwt);
        assertThat(recreated.authenticate(account.token()).userId()).isEqualTo(account.id());
        String latest = account.token();
        for (int i = 0; i < 20; i++) latest = sessions.issue(account.id(), account.username());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_session WHERE user_id = ?", Integer.class, account.id())).isEqualTo(20);
        assertThat(sessions.authenticate(latest).userId()).isEqualTo(account.id());
        assertThatThrownBy(() -> sessions.authenticate(account.token())).hasMessageContaining("失效");
    }

    @Test
    void loginSourceBudgetIsSharedAcrossAliasesAndForwardedHeadersDoNotBypassIt() throws Exception {
        Account account = account();
        String peer = peer();
        send(post("/api/user/login"), peer, credentials(account, "wrong")).andExpect(status().isUnauthorized());
        send(post("/user/login"), peer, credentials(account, "wrong")).andExpect(status().isUnauthorized());
        send(post("/api/user/login").header("X-Forwarded-For", "203.0.113.90")
                        .header("Forwarded", "for=203.0.113.91").header("Origin", "http://localhost:5173"), peer,
                credentials(account, PASSWORD)).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Expose-Headers", "Retry-After"))
                .andExpect(jsonPath("$.code").value(429));
    }

    @Test
    void accountBudgetCannotBeBypassedWithDifferentPeersOrUsernameCase() throws Exception {
        Account account = account();
        for (int i = 0; i < 3; i++) {
            send(post("/api/user/login"), peer(), Map.of("username", i % 2 == 0
                    ? account.username() : account.username().toUpperCase(java.util.Locale.ROOT), "password", "wrong"))
                    .andExpect(status().isUnauthorized());
        }
        send(post("/user/login"), peer(), credentials(account, PASSWORD)).andExpect(status().isTooManyRequests());
    }

    @Test
    void successfulLoginClearsTheAccountFailureBudget() throws Exception {
        Account account = account();
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < 2; i++) send(post("/api/user/login"), peer(), credentials(account, "wrong"))
                    .andExpect(status().isUnauthorized());
            send(post("/api/user/login"), peer(), credentials(account, PASSWORD)).andExpect(status().isOk());
        }
    }

    @Test
    void registrationBudgetIsSharedAcrossAliasesAndRegistrationCanBeClosed() throws Exception {
        String peer = peer();
        for (String prefix : new String[]{"", "/api"}) send(post(prefix + "/user/register"), peer,
                Map.of("username", "reg_" + sequence.incrementAndGet(), "password", PASSWORD)).andExpect(status().isOk());
        send(post("/user/register"), peer, Map.of("username", "reg_" + sequence.incrementAndGet(), "password", PASSWORD))
                .andExpect(status().isTooManyRequests());
        properties.setRegistrationEnabled(false);
        try {
            send(post("/api/user/register"), peer(), Map.of("username", "closed_user", "password", PASSWORD))
                    .andExpect(status().isForbidden());
        } finally { properties.setRegistrationEnabled(true); }
    }

    @Test
    void writeLimitPreventsMutationsButDoesNotPreventLogout() throws Exception {
        Account account = account();
        String peer = peer();
        for (int i = 0; i < 3; i++) send(post((i % 2 == 0 ? "/api" : "") + "/article/publish")
                        .header("Authorization", "Bearer " + account.token()), peer, Map.of("content", "entry"))
                .andExpect(status().isOk());
        send(post("/api/article/publish").header("Authorization", "Bearer " + account.token()), peer, Map.of("content", "denied"))
                .andExpect(status().isTooManyRequests());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM article WHERE user_id = ?", Integer.class, account.id())).isEqualTo(3);
        send(post("/api/user/logout").header("Authorization", "Bearer " + account.token()), peer, null)
                .andExpect(status().isOk());
    }

    @Test
    void publicReadsAreLimitedAndSecurityHeadersAlsoCoverErrors() throws Exception {
        String peer = peer();
        for (int i = 0; i < 5; i++) send(get("/api/plaza/categories"), peer, null).andExpect(status().isOk());
        send(get("/plaza/categories"), peer, null).andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    void writeAccountBudgetCannotBeBypassedByChangingPeers() throws Exception {
        Account account = account();
        for (int i = 0; i < 3; i++) send(post("/api/article/publish").header("Authorization", "Bearer " + account.token()),
                peer(), Map.of("content", "entry")).andExpect(status().isOk());
        send(post("/article/publish").header("Authorization", "Bearer " + account.token()), peer(), Map.of("content", "denied"))
                .andExpect(status().isTooManyRequests());
        assertThat(com.example.personal_blog.utils.UserContext.getUserId()).isNull();
    }

    @Test
    void oversizedJsonIsRejectedBeforeParsingAndCorsIsPreserved() throws Exception {
        send(post("/api/user/login").header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON).content("{" + "x".repeat(2048)), peer(), null)
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value(413))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        send(post("/api/user/login").contentType(MediaType.APPLICATION_JSON).content("{"), peer(), null)
                .andExpect(status().isBadRequest());
    }
}
