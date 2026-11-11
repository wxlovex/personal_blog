package com.example.personal_blog;

import com.example.personal_blog.dto.RegisterDTO;
import com.example.personal_blog.security.*;
import com.example.personal_blog.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:account_security;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,COMMENT",
        "blog.mail.enabled=true", "spring.mail.host=127.0.0.1", "blog.security.email-per-hour=10000",
        "blog.security.sensitive-per-minute=3", "blog.security.max-storage-bytes=100",
        "blog.security.max-articles=2", "blog.security.max-comments=2", "blog.security.max-categories=2",
        "blog.security.require-verified-email-for-publication=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountSecurityIntegrationTests {
    static final String PASSWORD = "initial-security-password";
    static final String NEW_PASSWORD = "changed-security-password";
    static final AtomicInteger PEER = new AtomicInteger();
    final BlockingQueue<SimpleMailMessage> messages = new LinkedBlockingQueue<>();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserService users;
    @Autowired AuthSessionService sessions;
    @Autowired JdbcTemplate jdbc;
    @Autowired SecurityProperties security;
    @Autowired AccountMailProperties mailProperties;
    @Autowired AccountQuotaService quota;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @MockitoBean JavaMailSender sender;
    record Account(long id, String username, String email, String token) {}

    @BeforeEach
    void captureMail() {
        doAnswer(call -> { messages.add(call.getArgument(0)); return null; }).when(sender).send(any(SimpleMailMessage.class));
    }
    Account account() {
        String username = "account_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String email = username + "@example.test";
        long id = users.register(new RegisterDTO(username, PASSWORD, email)).id();
        return new Account(id, username, email, sessions.issue(id, username));
    }
    ResultActions send(MockHttpServletRequestBuilder request, String token, Object data) throws Exception {
        request.with(r -> { r.setRemoteAddr("198.18." + (PEER.incrementAndGet() / 250) + "." + (PEER.get() % 250 + 1)); return r; });
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (data != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(data));
        return mvc.perform(request);
    }
    JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
    Map<String, String> password(String value) { return Map.of("currentPassword", value); }
    String mailToken(String email, String subject) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            SimpleMailMessage message = messages.poll(100, TimeUnit.MILLISECONDS);
            if (message != null && Arrays.asList(message.getTo()).contains(email) && message.getSubject().contains(subject)) {
                var match = Pattern.compile("#token=([A-Za-z0-9_-]{43})").matcher(message.getText());
                assertThat(match.find()).isTrue();
                return match.group(1);
            }
        }
        throw new AssertionError("Expected local test email was not queued");
    }
    String requestVerification(Account account) throws Exception {
        send(post("/api/user/email/verification"), account.token(),
                Map.of("email", account.email(), "currentPassword", PASSWORD)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        return mailToken(account.email(), "验证邮箱");
    }
    String verifyAndLogin(Account account) throws Exception {
        String token = requestVerification(account);
        send(post("/api/user/email/confirm"), null, Map.of("token", token)).andExpect(status().isOk());
        return users.login(account.username(), PASSWORD);
    }
    long article(Account account, String content) throws Exception {
        return body(send(post("/api/article/publish"), account.token(), Map.of("title", "t", "content", content))
                .andExpect(status().isOk())).path("data").path("id").asLong();
    }

    @Test
    void passwordChangeRequiresPasswordAndRevokesEverySessionWithoutAffectingOtherUsers() throws Exception {
        Account account = account(); Account other = account();
        String second = sessions.issue(account.id(), account.username());
        send(post("/api/user/password"), account.token(), Map.of("currentPassword", "wrong", "newPassword", NEW_PASSWORD))
                .andExpect(status().isForbidden());
        send(get("/api/user/me"), account.token(), null).andExpect(status().isOk());
        send(post("/user/password"), account.token(), Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))
                .andExpect(status().isOk());
        for (String token : List.of(account.token(), second)) send(get("/api/user/me"), token, null).andExpect(status().isUnauthorized());
        send(get("/api/user/me"), other.token(), null).andExpect(status().isOk());
        send(post("/api/user/login"), null, Map.of("username", account.username(), "password", PASSWORD)).andExpect(status().isUnauthorized());
        send(post("/api/user/login"), null, Map.of("username", account.username(), "password", NEW_PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void sameOrOverlongNewPasswordsDoNotRevokeSessions() throws Exception {
        Account account = account();
        for (String invalid : List.of(PASSWORD, "密".repeat(25), "short")) {
            send(post("/api/user/password"), account.token(), Map.of("currentPassword", PASSWORD, "newPassword", invalid))
                    .andExpect(status().isBadRequest());
        }
        send(get("/api/user/me"), account.token(), null).andExpect(status().isOk());
    }

    @Test
    void allDeviceLogoutRequiresPasswordAndRemovesOutstandingLinks() throws Exception {
        Account account = account();
        String second = sessions.issue(account.id(), account.username());
        requestVerification(account);
        send(post("/api/user/logout-all"), account.token(), password("wrong")).andExpect(status().isForbidden());
        send(post("/user/logout-all"), account.token(), password(PASSWORD)).andExpect(status().isOk());
        for (String token : List.of(account.token(), second)) send(get("/user/me"), token, null).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_token WHERE user_id=?", Integer.class, account.id())).isZero();
    }

    @Test
    void guessingCurrentPasswordIsLimitedAcrossDifferentPeersAndActions() throws Exception {
        Account account = account();
        for (int i = 0; i < 3; i++) send(post("/api/user/logout-all"), account.token(), password("wrong"))
                .andExpect(status().isForbidden());
        send(post("/user/password"), account.token(), Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        send(get("/api/user/me"), account.token(), null).andExpect(status().isOk());
    }

    @Test
    void unverifiedLegacyEmailDoesNotEnableRecoveryAndResponsesDoNotRevealItsExistence() throws Exception {
        Account account = account();
        JsonNode known = body(send(post("/api/user/password/forgot"), null, Map.of("email", account.email())).andExpect(status().isOk()));
        JsonNode missing = body(send(post("/user/password/forgot"), null, Map.of("email", "absent@example.test")).andExpect(status().isOk()));
        assertThat(known).isEqualTo(missing);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_token WHERE user_id=?", Integer.class, account.id())).isZero();
    }

    @Test
    void verificationIsPasswordProtectedHashedPurposeBoundSingleUseAndRevokesSessions() throws Exception {
        Account account = account();
        send(post("/api/user/email/verification"), account.token(), Map.of("email", account.email(), "currentPassword", "wrong"))
                .andExpect(status().isForbidden());
        String secret = requestVerification(account);
        String stored = jdbc.queryForObject("SELECT token_hash FROM account_token WHERE user_id=?", String.class, account.id());
        assertThat(stored).isEqualTo(AccountSecurityService.hash(secret)).doesNotContain(secret);
        send(post("/api/user/password/reset"), null, Map.of("token", secret, "newPassword", NEW_PASSWORD)).andExpect(status().isBadRequest());
        send(post("/user/email/confirm").header("Authorization", "Bearer stale"), null, Map.of("token", secret))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(account.id()));
        send(post("/api/user/email/confirm"), null, Map.of("token", secret)).andExpect(status().isBadRequest());
        send(get("/api/user/me"), account.token(), null).andExpect(status().isUnauthorized());
        String token = users.login(account.username(), PASSWORD);
        send(get("/api/user/me"), token, null).andExpect(jsonPath("$.data.emailVerified").value(true));
    }

    @Test
    void expiredAndSupersededLinksCannotBindAnEmail() throws Exception {
        Account account = account();
        String first = requestVerification(account);
        String second = requestVerification(account);
        send(post("/api/user/email/confirm"), null, Map.of("token", first)).andExpect(status().isBadRequest());
        jdbc.update("UPDATE account_token SET expires_at=? WHERE user_id=?", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1), account.id());
        send(post("/api/user/email/confirm"), null, Map.of("token", second)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verified_email WHERE user_id=?", Integer.class, account.id())).isZero();
    }

    @Test
    void oneVerifiedAddressCannotBeClaimedByAnotherAccountOrCaseVariant() throws Exception {
        Account owner = account(); Account other = account();
        verifyAndLogin(owner);
        send(post("/api/user/email/verification"), other.token(), Map.of("email", owner.email().toUpperCase(Locale.ROOT), "currentPassword", PASSWORD))
                .andExpect(status().isConflict());
    }

    @Test
    void occupiedEmailProbesConsumeTheAccountBudgetAcrossPeersAndTargets() throws Exception {
        List<Account> owners = List.of(account(), account(), account());
        Account other = account();
        Map<Account, String> ownerTokens = new HashMap<>();
        for (Account owner : owners) ownerTokens.put(owner, verifyAndLogin(owner));
        int previousLimit = security.getEmailPerHour();
        security.setEmailPerHour(3);
        try {
            for (int i = 0; i < 3; i++) {
                send(post("/api/user/email/verification"), other.token(),
                        Map.of("email", owners.get(i).email(), "currentPassword", PASSWORD)).andExpect(status().isConflict());
            }
            send(post("/user/email/verification"), other.token(),
                    Map.of("email", other.email(), "currentPassword", PASSWORD))
                    .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_token WHERE user_id=?", Integer.class, other.id())).isZero();
            Account anotherProber = account();
            for (int i = 0; i < 3; i++) {
                send(post("/api/user/email/verification"), anotherProber.token(),
                        Map.of("email", owners.get(0).email(), "currentPassword", PASSWORD)).andExpect(status().isConflict());
            }
            // Rejected probes must not exhaust the legitimate owner's destination email budget.
            send(post("/api/user/email/verification"), ownerTokens.get(owners.get(0)),
                    Map.of("email", owners.get(0).email(), "currentPassword", PASSWORD)).andExpect(status().isOk());
        } finally { security.setEmailPerHour(previousLimit); }
    }

    @Test
    void recoveryRevokesAllSessionsAndTheLinkCannotBeReplayedOrUsedForVerification() throws Exception {
        Account account = account();
        String login = verifyAndLogin(account);
        String second = sessions.issue(account.id(), account.username());
        send(post("/api/user/password/forgot"), null, Map.of("email", account.email())).andExpect(status().isOk());
        String secret = mailToken(account.email(), "重置密码");
        send(post("/api/user/email/confirm"), null, Map.of("token", secret)).andExpect(status().isBadRequest());
        send(post("/user/password/reset"), null, Map.of("token", secret, "newPassword", NEW_PASSWORD)).andExpect(status().isOk());
        send(post("/api/user/password/reset"), null, Map.of("token", secret, "newPassword", NEW_PASSWORD)).andExpect(status().isBadRequest());
        for (String token : List.of(login, second)) send(get("/api/user/me"), token, null).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> users.login(account.username(), PASSWORD)).isInstanceOf(RuntimeException.class);
        assertThat(users.login(account.username(), NEW_PASSWORD)).isNotBlank();
    }

    @Test
    void concurrentConsumersOfTheSameResetLinkHaveExactlyOneWinner() throws Exception {
        Account account = account();
        verifyAndLogin(account);
        send(post("/api/user/password/forgot"), null, Map.of("email", account.email())).andExpect(status().isOk());
        String secret = mailToken(account.email(), "重置密码");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < 4; i++) tasks.add(() -> send(post("/api/user/password/reset"), null,
                    Map.of("token", secret, "newPassword", NEW_PASSWORD)).andReturn().getResponse().getStatus());
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : pool.invokeAll(tasks)) statuses.add(result.get());
            assertThat(statuses).containsExactlyInAnyOrder(200, 400, 400, 400);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void passwordChangesCannotLeaveAnOldPasswordLoginSessionAlive() throws Exception {
        Account account = account();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<JsonNode> login = pool.submit(() -> { start.await(); return body(send(post("/api/user/login"), null,
                    Map.of("username", account.username(), "password", PASSWORD))); });
            Future<Integer> change = pool.submit(() -> { start.await(); return send(post("/api/user/password"), account.token(),
                    Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD)).andReturn().getResponse().getStatus(); });
            start.countDown();
            assertThat(change.get()).isEqualTo(200);
            JsonNode response = login.get();
            if (response.path("code").asInt() == 200) send(get("/api/user/me"), response.path("data").asText(), null)
                    .andExpect(status().isUnauthorized());
            else assertThat(response.path("code").asInt()).isEqualTo(401);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void storageUsesUtf8BytesAndDeletingContentFreesCapacity() throws Exception {
        Account account = account();
        long id = article(account, "中文😀");
        JsonNode usage = body(send(get("/api/user/security"), account.token(), null)).path("data").path("storage");
        assertThat(usage.path("usedBytes").asLong()).isEqualTo(11);
        send(put("/api/article/" + id), account.token(), Map.of("title", "t", "content", "x".repeat(100)))
                .andExpect(status().isConflict());
        send(get("/api/article/detail/" + id), account.token(), null).andExpect(jsonPath("$.data.content").value("中文😀"));
        send(delete("/api/article/" + id), account.token(), null).andExpect(status().isOk());
        assertThat(body(send(get("/api/user/security"), account.token(), null)).at("/data/storage/usedBytes").asLong()).isZero();
    }

    @Test
    void countQuotasArePerAccountAndReleasedByDeletion() throws Exception {
        Account account = account(); Account other = account();
        long first = article(account, "one"); article(account, "two"); article(other, "allowed");
        send(post("/api/article/publish"), account.token(), Map.of("content", "three")).andExpect(status().isConflict());
        send(delete("/api/article/" + first), account.token(), null).andExpect(status().isOk());
        article(account, "now allowed");
        long category = body(send(post("/api/category"), account.token(), Map.of("name", "a")).andExpect(status().isOk())).at("/data/id").asLong();
        send(post("/api/category"), account.token(), Map.of("name", "b")).andExpect(status().isOk());
        send(post("/api/category"), account.token(), Map.of("name", "c")).andExpect(status().isConflict());
        send(delete("/api/category/" + category), account.token(), null).andExpect(status().isOk());
        send(post("/api/category"), account.token(), Map.of("name", "c")).andExpect(status().isOk());
    }

    @Test
    void concurrentWritesCannotOverrunTheArticleQuota() throws Exception {
        Account account = account();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) tasks.add(() -> send(post("/api/article/publish"), account.token(), Map.of("content", "x"))
                    .andReturn().getResponse().getStatus());
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : pool.invokeAll(tasks)) statuses.add(result.get());
            assertThat(statuses.stream().filter(value -> value == 200).count()).isEqualTo(2);
            assertThat(statuses.stream().filter(value -> value == 409).count()).isEqualTo(6);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void publicationRequiresVerifiedEmailWhilePrivateWritingRemainsAvailable() throws Exception {
        Account account = account();
        long id = article(account, "private text");
        send(put("/api/article/" + id + "/publication"), account.token(), Map.of("published", true, "category", "技术"))
                .andExpect(status().isForbidden());
        String token = verifyAndLogin(account);
        send(put("/api/article/" + id + "/publication"), token, Map.of("published", true, "category", "技术"))
                .andExpect(status().isOk());
    }

    @Test
    void disabledMailFailsClosedWithoutIssuingRecoveryCredentials() throws Exception {
        Account account = account();
        mailProperties.setEnabled(false);
        try {
            send(post("/api/user/password/forgot"), null, Map.of("email", account.email())).andExpect(status().isServiceUnavailable());
            send(post("/api/user/email/verification"), account.token(), Map.of("email", account.email(), "currentPassword", PASSWORD))
                    .andExpect(status().isServiceUnavailable());
        } finally { mailProperties.setEnabled(true); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_token WHERE user_id=?", Integer.class, account.id())).isZero();
    }

    @Test
    void disablingMailAlsoBlocksPreviouslyIssuedVerificationAndResetLinks() throws Exception {
        Account verification = account();
        String verifySecret = requestVerification(verification);
        Account recovery = account();
        String login = verifyAndLogin(recovery);
        send(post("/api/user/password/forgot"), null, Map.of("email", recovery.email())).andExpect(status().isOk());
        String resetSecret = mailToken(recovery.email(), "重置密码");
        mailProperties.setEnabled(false);
        try {
            send(post("/api/user/email/confirm"), null, Map.of("token", verifySecret)).andExpect(status().isServiceUnavailable());
            send(post("/user/password/reset"), null, Map.of("token", resetSecret, "newPassword", NEW_PASSWORD))
                    .andExpect(status().isServiceUnavailable());
            send(get("/api/user/me"), login, null).andExpect(status().isOk());
            assertThat(users.login(recovery.username(), PASSWORD)).isNotBlank();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verified_email WHERE user_id=?", Integer.class, verification.id())).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_token WHERE user_id IN (?,?)", Integer.class,
                    verification.id(), recovery.id())).isEqualTo(2);
        } finally { mailProperties.setEnabled(true); }
    }

    @Test
    void publicationAndPublicEditsRemainAvailableWithMailAndVerificationGateDisabled() throws Exception {
        Account account = account();
        mailProperties.setEnabled(false);
        security.setRequireVerifiedEmailForPublication(false);
        try {
            send(get("/api/user/security-options"), null, null).andExpect(jsonPath("$.data.mailEnabled").value(false));
            send(get("/api/user/security"), account.token(), null)
                    .andExpect(jsonPath("$.data.mailEnabled").value(false))
                    .andExpect(jsonPath("$.data.verifiedEmailRequiredForPublication").value(false));
            long id = article(account, "personal entry");
            send(put("/api/article/" + id + "/publication"), account.token(), Map.of("published", true, "category", "日常"))
                    .andExpect(status().isOk());
            send(put("/api/article/" + id), account.token(), Map.of("content", "updated personal entry"))
                    .andExpect(status().isOk());
            send(get("/api/plaza/articles/" + id), null, null).andExpect(jsonPath("$.data.content").value("updated personal entry"));
        } finally {
            mailProperties.setEnabled(true);
            security.setRequireVerifiedEmailForPublication(true);
        }
    }

    @Test
    void changingPasswordInvalidatesPreviouslyIssuedRecoveryLinks() throws Exception {
        Account account = account();
        String token = verifyAndLogin(account);
        send(post("/api/user/password/forgot"), null, Map.of("email", account.email())).andExpect(status().isOk());
        String secret = mailToken(account.email(), "重置密码");
        send(post("/api/user/password"), token, Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD)).andExpect(status().isOk());
        send(post("/api/user/password/reset"), null, Map.of("token", secret, "newPassword", PASSWORD)).andExpect(status().isBadRequest());
    }

    @Test
    void expiredRecoveryLinksLeavePasswordsAndSessionsUnchanged() throws Exception {
        Account account = account();
        String token = verifyAndLogin(account);
        send(post("/api/user/password/forgot"), null, Map.of("email", account.email())).andExpect(status().isOk());
        String secret = mailToken(account.email(), "重置密码");
        jdbc.update("UPDATE account_token SET expires_at=? WHERE user_id=?", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1), account.id());
        send(post("/api/user/password/reset"), null, Map.of("token", secret, "newPassword", NEW_PASSWORD)).andExpect(status().isBadRequest());
        send(get("/api/user/me"), token, null).andExpect(status().isOk());
        assertThat(users.login(account.username(), PASSWORD)).isNotBlank();
    }

    @Test
    void mailboxChangesTakeEffectOnlyAfterConfirmationAndInvalidateOldRecoveryLinks() throws Exception {
        Account account = account();
        String token = verifyAndLogin(account);
        send(post("/api/user/password/forgot"), null, Map.of("email", account.email())).andExpect(status().isOk());
        String reset = mailToken(account.email(), "重置密码");
        String replacement = "new_" + account.email();
        send(post("/api/user/email/verification"), token, Map.of("currentPassword", PASSWORD, "email", replacement)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT email FROM verified_email WHERE user_id=?", String.class, account.id())).isEqualTo(account.email());
        String secret = mailToken(replacement, "验证邮箱");
        send(post("/api/user/email/confirm"), null, Map.of("token", secret)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT email FROM verified_email WHERE user_id=?", String.class, account.id())).isEqualTo(replacement);
        send(post("/api/user/password/reset"), null, Map.of("token", reset, "newPassword", NEW_PASSWORD)).andExpect(status().isBadRequest());
    }

    @Test
    void notesCountTowardQuotaAndLegacyOverLimitContentCanStillShrinkOrBeDeleted() throws Exception {
        Account account = account();
        long id = article(account, "entry");
        long note = body(send(post("/api/comment/publish"), account.token(), Map.of("articleId", id, "content", "note"))
                .andExpect(status().isOk())).at("/data/id").asLong();
        send(post("/api/comment/publish"), account.token(), Map.of("articleId", id, "content", "note2")).andExpect(status().isOk());
        send(post("/api/comment/publish"), account.token(), Map.of("articleId", id, "content", "note3")).andExpect(status().isConflict());
        send(delete("/api/comment/" + note), account.token(), null).andExpect(status().isOk());
        send(post("/api/comment/publish"), account.token(), Map.of("articleId", id, "content", "n")).andExpect(status().isOk());
        long limit = security.getMaxStorageBytes();
        security.setMaxStorageBytes(1);
        try {
            send(put("/api/article/" + id), account.token(), Map.of("title", "t", "content", "x")).andExpect(status().isOk());
            send(delete("/api/article/" + id), account.token(), null).andExpect(status().isOk());
        } finally { security.setMaxStorageBytes(limit); }
    }

    @Test
    void recoveryEmailBudgetIsCanonicalAndAppliesToUnregisteredAddressesToo() throws Exception {
        String email = "limited_" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        int limit = security.getEmailPerHour();
        security.setEmailPerHour(1);
        try {
            send(post("/api/user/password/forgot"), null, Map.of("email", email)).andExpect(status().isOk());
            send(post("/user/password/forgot"), null, Map.of("email", email.toUpperCase(Locale.ROOT)))
                    .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        } finally { security.setEmailPerHour(limit); }
    }

    @Test
    void aQueuedWriteRechecksSessionRevocationAfterTakingTheAccountLock() {
        Account account = account();
        var identity = com.auth0.jwt.JWT.decode(account.token());
        com.example.personal_blog.utils.UserContext.setUserId(account.id());
        com.example.personal_blog.utils.UserContext.setSessionId(identity.getId());
        jdbc.update("DELETE FROM auth_session WHERE user_id=?", account.id());
        try {
            var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
            assertThatThrownBy(() -> transaction.execute(status -> { quota.lockUser(); return null; }))
                    .isInstanceOf(com.example.personal_blog.common.BusinessException.class).hasMessageContaining("失效");
        } finally { com.example.personal_blog.utils.UserContext.clear(); }
    }
}
