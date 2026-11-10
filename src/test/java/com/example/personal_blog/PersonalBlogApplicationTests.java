package com.example.personal_blog;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.example.personal_blog.common.BusinessException;
import com.example.personal_blog.service.ArticleService;
import com.example.personal_blog.utils.JwtUtil;
import com.example.personal_blog.utils.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PersonalBlogApplicationTests {
    private static final String PASSWORD = "correct-password";
    private static final String TEST_SECRET = "integration-test-only-secret-at-least-32-bytes";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ArticleService articleService;
    @Autowired JwtUtil jwt;
    private Account alice;
    private Account bob;
    private Account eve;

    record Account(long id, String token) {}

    @BeforeEach
    void reset() throws Exception {
        UserContext.clear();
        jdbc.update("DELETE FROM user_subscription");
        jdbc.update("DELETE FROM article_like");
        jdbc.update("DELETE FROM comment");
        jdbc.update("DELETE FROM article");
        jdbc.update("DELETE FROM category");
        jdbc.update("DELETE FROM `user`");
        alice = account("alice");
        bob = account("bob");
        eve = account("eve");
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void registrationHashesPasswordAndDoesNotExposeIt() throws Exception {
        JsonNode result = body(send(post("/user/register"), null,
                Map.of("username", "new_user", "password", PASSWORD, "email", "a@example.com"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.password").doesNotExist()));
        assertThat(result.at("/data/username").asText()).isEqualTo("new_user");
        String hash = jdbc.queryForObject("SELECT password FROM `user` WHERE username = ?", String.class, "new_user");
        assertThat(hash).isNotEqualTo(PASSWORD);
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, hash)).isTrue();
    }

    @Test
    void duplicateAndInvalidRegistrationHaveUsefulStatusCodes() throws Exception {
        send(post("/user/register"), null, Map.of("username", "alice", "password", PASSWORD))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
        send(post("/user/register"), null, Map.of("username", "x", "password", "short"))
                .andExpect(status().isBadRequest());
        send(post("/user/register"), null, Map.of("username", "valid_user", "password", PASSWORD, "email", "bad-email"))
                .andExpect(status().isBadRequest());
        send(post("/user/register"), null, Map.of("username", "valid_user", "password", "密".repeat(30)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginAndCurrentUserDoNotExposeCredentials() throws Exception {
        send(post("/user/login"), null, Map.of("username", "alice", "password", "wrong"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        send(post("/user/login"), null, Map.of("username", "missing", "password", PASSWORD))
                .andExpect(status().isUnauthorized());
        send(get("/user/me"), alice, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(alice.id()))
                .andExpect(jsonPath("$.data.password").doesNotExist());
        send(get("/user/me"), null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidExpiredAndMalformedTokensAreUnauthorized() throws Exception {
        String expired = JWT.create().withIssuer("personal-blog").withClaim("userId", alice.id())
                .withExpiresAt(Instant.now().minusSeconds(120)).sign(Algorithm.HMAC256(TEST_SECRET));
        String wrongIssuer = JWT.create().withIssuer("other").withClaim("userId", alice.id())
                .withExpiresAt(Instant.now().plusSeconds(3600)).sign(Algorithm.HMAC256(TEST_SECRET));
        for (String token : List.of("bad-token", expired, wrongIssuer)) {
            mvc.perform(get("/user/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        }
        mvc.perform(get("/user/me").header("Authorization", "Basic invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/user/me").header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staleAuthorizationDoesNotPreventLoginOrRegistration() throws Exception {
        mvc.perform(post("/user/login").header("Authorization", "Bearer expired-token")
                        .contentType("application/json")
                        .content(json.writeValueAsBytes(Map.of("username", "alice", "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isString());
        mvc.perform(post("/user/register").header("Authorization", "Bearer expired-token")
                        .contentType("application/json")
                        .content(json.writeValueAsBytes(Map.of("username", "fresh_user", "password", PASSWORD))))
                .andExpect(status().isOk());
        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    void weakJwtConfigurationAndMissingIdentityAreRejected() {
        assertThatThrownBy(() -> new JwtUtil("short", 60)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtUtil(TEST_SECRET, 0)).isInstanceOf(IllegalArgumentException.class);
        String missing = JWT.create().withIssuer("personal-blog")
                .withExpiresAt(Instant.now().plusSeconds(60)).sign(Algorithm.HMAC256(TEST_SECRET));
        assertThatThrownBy(() -> jwt.getUserId(missing)).isInstanceOf(BusinessException.class);
    }

    @Test
    void privateReadsRequireLoginAndDoNotLeakContext() throws Exception {
        long id = article(alice, "Private diary", null);
        for (String prefix : List.of("", "/api")) {
            for (String path : List.of("/article/list", "/article/detail/" + id, "/comment/" + id,
                    "/category/list", "/article/ranking", "/article/subscriptions",
                    "/user/subscriptions", "/user/" + alice.id() + "/subscription")) {
                send(get(prefix + path), null, null).andExpect(status().isUnauthorized())
                        .andExpect(header().string("Cache-Control", "no-store"));
            }
        }
        send(get("/user/me"), alice, null).andExpect(status().isOk());
        send(get("/user/me"), null, null).andExpect(status().isUnauthorized());
        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    void privateListSearchFiltersAndPaginationNeverIncludeOtherAccounts() throws Exception {
        long category = category(alice, "生活");
        long expected = article(alice, "A quiet morning", category);
        article(alice, "Another page", null);
        article(bob, "A quiet morning secret", null);
        send(get("/api/article/list").param("keyword", "quiet").param("categoryId", "" + category), alice, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(expected))
                .andExpect(jsonPath("$.data.records[0].summary").value("正文内容"))
                .andExpect(jsonPath("$.data.records[0].content").isEmpty())
                .andExpect(header().string("Cache-Control", "no-store"));
        send(get("/article/list").param("page", "2").param("size", "1"), alice, null)
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records.length()").value(1));
        send(get("/article/list").param("authorId", "" + bob.id()), alice, null)
                .andExpect(jsonPath("$.data.total").value(0));
        send(get("/article/list").param("keyword", "secret"), alice, null)
                .andExpect(jsonPath("$.data.total").value(0));
        send(get("/article/list").param("page", "99"), alice, null)
                .andExpect(jsonPath("$.data.records").isEmpty());
        send(get("/article/list"), eve, null).andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void diaryOrderUsesCreationTimeThenIdAndEditingDoesNotMoveOldEntries() throws Exception {
        long first = article(alice, "First entry", null);
        long second = article(alice, "Second entry", null);
        long latest = article(alice, "Latest entry", null);
        jdbc.update("UPDATE article SET create_time = '2026-01-01 00:00:00' WHERE id IN (?, ?)", first, second);
        jdbc.update("UPDATE article SET create_time = '2026-01-02 00:00:00' WHERE id = ?", latest);
        send(put("/api/article/" + first), alice, Map.of("title", "Edited first entry", "content", "Updated"))
                .andExpect(status().isOk());
        send(get("/api/article/list").param("size", "2"), alice, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[0].id").value(latest))
                .andExpect(jsonPath("$.data.records[1].id").value(second));
        send(get("/api/article/list").param("size", "2").param("page", "2"), alice, null)
                .andExpect(jsonPath("$.data.records[0].id").value(first));
    }

    @Test
    void titleSearchTreatsWildcardsLiterallyAndDoesNotSearchBody() throws Exception {
        long literal = article(alice, "100%_日常", null);
        send(post("/api/article/publish"), alice, Map.of("title", "Plain title", "content", "100%_日常"))
                .andExpect(status().isOk());
        send(get("/api/article/list").param("keyword", " %_ "), alice, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(literal));
    }

    @Test
    void foreignAndMissingDiaryIdsAreIndistinguishableAndCannotBeModified() throws Exception {
        long id = article(alice, "Do not disclose", null);
        for (long target : List.of(id, 999999L)) {
            send(get("/api/article/detail/" + target), bob, null).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("记录不存在")).andExpect(jsonPath("$.data").isEmpty());
            send(put("/api/article/" + target), bob, articleBody("Intrusion", null))
                    .andExpect(status().isNotFound());
            send(delete("/api/article/" + target), bob, null).andExpect(status().isNotFound());
            send(get("/api/comment/" + target), bob, null).andExpect(status().isNotFound());
            send(post("/api/comment/publish"), bob, Map.of("articleId", target, "content", "Intrusion"))
                    .andExpect(status().isNotFound());
        }
        send(get("/article/detail/" + id), alice, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Do not disclose"))
                .andExpect(jsonPath("$.data.viewCount").value(0));
        assertThat(counter(id, "view_count")).isZero();
        assertThat(count("comment")).isZero();
    }

    @Test
    void categoriesArePrivateAndNamesAreUniqueOnlyWithinAnAccount() throws Exception {
        long category = category(alice, "生活");
        category(bob, "生活");
        send(post("/category"), alice, Map.of("name", " 生活 ")).andExpect(status().isConflict());
        send(put("/category/" + category), bob, Map.of("name", "Intrusion")).andExpect(status().isNotFound());
        send(delete("/category/" + category), bob, null).andExpect(status().isNotFound());
        send(post("/article/publish"), bob, articleBody("Foreign category", category))
                .andExpect(status().isNotFound());
        article(alice, "Classified", category);
        send(get("/category/list"), alice, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(alice.id()))
                .andExpect(jsonPath("$.data[0].articleCount").value(1));
        send(get("/category/list"), bob, null)
                .andExpect(jsonPath("$.data[0].articleCount").value(0));
        send(delete("/category/" + category), alice, null).andExpect(status().isConflict());
        send(post("/category"), alice, Map.of("name", " ")).andExpect(status().isBadRequest());
    }

    @Test
    void unusedCategoryCanBeDeletedAndMissingCategoryCannotBeUsed() throws Exception {
        long category = category(alice, "Unused");
        send(delete("/category/" + category), alice, null).andExpect(status().isOk());
        send(post("/article/publish"), alice, articleBody("Invalid category", category))
                .andExpect(status().isNotFound());
    }

    @Test
    void categoryCanBeAssignedMovedAndClearedWithoutRewritingArticleText() throws Exception {
        long first = category(alice, "生活");
        long second = category(alice, "读书");
        long id = article(alice, "Original title", null);
        String created = body(send(get("/article/detail/" + id), alice, null)).at("/data/createTime").asText();
        jdbc.update("UPDATE article SET view_count = 9, like_count = 3 WHERE id = ?", id);
        for (long categoryId : List.of(first, second)) {
            send(patch("/api/article/" + id + "/category"), alice,
                    Map.of("categoryId", categoryId, "title", "Must be ignored", "content", "Must be ignored"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.categoryId").value(categoryId))
                    .andExpect(jsonPath("$.data.title").value("Original title"))
                    .andExpect(jsonPath("$.data.content").value("正文内容"))
                    .andExpect(jsonPath("$.data.createTime").value(created))
                    .andExpect(jsonPath("$.data.viewCount").value(9))
                    .andExpect(jsonPath("$.data.likeCount").value(3));
        }
        Map<Long, Long> categoryCounts = new HashMap<>();
        body(send(get("/category/list"), alice, null).andExpect(status().isOk())).at("/data")
                .forEach(category -> categoryCounts.put(category.get("id").asLong(), category.get("articleCount").asLong()));
        assertThat(categoryCounts).containsEntry(first, 0L).containsEntry(second, 1L);
        send(patch("/article/" + id + "/category"), alice, Collections.singletonMap("categoryId", null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.categoryId").isEmpty())
                .andExpect(jsonPath("$.data.content").value("正文内容"));
        send(delete("/category/" + second), alice, null).andExpect(status().isOk());
    }

    @Test
    void changingCategoryRequiresOwnershipOfBothArticleAndCategory() throws Exception {
        long mine = category(alice, "Mine");
        long foreign = category(bob, "Foreign");
        long id = article(alice, "Private", mine);
        mvc.perform(options("/api/article/" + id + "/category").header("Origin", "http://127.0.0.1:5173")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5173"));
        send(patch("/api/article/" + id + "/category"), null, Map.of("categoryId", mine))
                .andExpect(status().isUnauthorized());
        send(patch("/api/article/" + id + "/category"), bob, Map.of("categoryId", foreign))
                .andExpect(status().isNotFound());
        for (long target : List.of(foreign, 999999L)) {
            send(patch("/api/article/" + id + "/category"), alice, Map.of("categoryId", target))
                    .andExpect(status().isNotFound());
        }
        send(patch("/api/article/999999/category"), alice, Map.of("categoryId", mine))
                .andExpect(status().isNotFound());
        for (Object invalid : List.of(-1, 0, "not-an-id")) {
            send(patch("/api/article/" + id + "/category"), alice, Map.of("categoryId", invalid))
                    .andExpect(status().isBadRequest());
        }
        send(get("/article/detail/" + id), alice, null)
                .andExpect(jsonPath("$.data.categoryId").value(mine));
    }

    @Test
    void titleIsOptionalAndDerivedFromTheFirstLineWithoutBreakingEmoji() throws Exception {
        send(post("/api/article/publish"), alice, Map.of("content", "今天的小事\n第二段内容"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("今天的小事"));
        send(post("/api/article/publish"), alice, Map.of("title", " ", "content", "x".repeat(49) + "🌱"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("x".repeat(49)));
        send(post("/api/article/publish"), alice, Map.of("content", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownDiaryCanBeEditedAndLegacyCountersArePreservedWithoutTrackingReads() throws Exception {
        long category = category(alice, "Tech");
        long id = article(alice, "Original", category);
        jdbc.update("UPDATE article SET view_count = 7, like_count = 2 WHERE id = ?", id);
        send(get("/article/detail/" + id), alice, null).andExpect(status().isOk());
        send(put("/article/" + id), alice, Map.of("content", "新的第一行\n新的正文"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("新的第一行"))
                .andExpect(jsonPath("$.data.categoryId").isEmpty())
                .andExpect(jsonPath("$.data.viewCount").value(7))
                .andExpect(jsonPath("$.data.likeCount").value(2));
        send(delete("/category/" + category), alice, null).andExpect(status().isOk());
    }

    @Test
    void paginationAndBodyValidationReturn400() throws Exception {
        for (String url : List.of("/article/list?page=0", "/article/list?size=101",
                "/article/list?size=-1", "/article/list?categoryId=-1",
                "/article/list?page=abc", "/article/detail/-1")) {
            send(get(url), alice, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        }
        send(post("/article/publish"), alice, Map.of("title", "T".repeat(51), "content", "text"))
                .andExpect(status().isBadRequest());
        send(post("/comment/publish"), alice, Map.of("content", "missing diary"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/user/login").contentType("application/json").content("{bad"))
                .andExpect(status().isBadRequest());
        send(get("/does-not-exist"), null, null).andExpect(status().isNotFound());
    }

    @Test
    void supplementaryNotesAreOwnerOnlyAndHideHistoricalForeignComments() throws Exception {
        long id = article(alice, "A private page", null);
        long note = body(send(post("/comment/publish"), alice, Map.of("articleId", id, "content", "My later thought"))
                .andExpect(status().isOk())).at("/data/id").asLong();
        jdbc.update("INSERT INTO comment(article_id,user_id,content,create_time) VALUES(?,?,?,CURRENT_TIMESTAMP)",
                id, bob.id(), "Historical public comment");
        send(get("/comment/" + id), alice, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].content").value("My later thought"));
        send(get("/comment/" + id), bob, null).andExpect(status().isNotFound());
        send(delete("/comment/" + note), bob, null).andExpect(status().isNotFound());
        send(delete("/comment/" + note), alice, null).andExpect(status().isOk());
    }

    @Test
    void deletingOwnedDiaryCleansNotesAndHistoricalLikes() throws Exception {
        long id = article(alice, "Delete me", null);
        jdbc.update("INSERT INTO article_like(article_id,user_id,create_time) VALUES(?,?,CURRENT_TIMESTAMP)",
                id, bob.id());
        send(post("/comment/publish"), alice, Map.of("articleId", id, "content", "A note"))
                .andExpect(status().isOk());
        send(delete("/article/" + id), bob, null).andExpect(status().isNotFound());
        send(delete("/article/" + id), alice, null).andExpect(status().isOk());
        assertThat(count("article")).isZero();
        assertThat(count("article_like")).isZero();
        assertThat(count("comment")).isZero();
    }

    @Test
    void serviceAndConcurrentReadsEnforceOwnershipWithoutChangingCounters() throws Exception {
        long id = article(alice, "Concurrent private reads", null);
        assertThatThrownBy(() -> articleService.detail(id)).isInstanceOf(BusinessException.class);
        parallel(20, i -> {
            UserContext.setUserId(i % 2 == 0 ? alice.id() : bob.id());
            try {
                if (i % 2 == 0) assertThat(articleService.detail(id).getTitle()).isEqualTo("Concurrent private reads");
                else assertThatThrownBy(() -> articleService.detail(id)).isInstanceOf(BusinessException.class)
                        .extracting(error -> ((BusinessException) error).getResultCode()).isEqualTo(com.example.personal_blog.common.ResultCode.NOT_FOUND);
                return true;
            } finally { UserContext.clear(); }
        });
        assertThat(counter(id, "view_count")).isZero();
    }

    @Test
    void corsPreflightAllowsConfiguredFrontendWithoutAuthentication() throws Exception {
        for (String origin : List.of("http://localhost:5173", "http://127.0.0.1:5173")) {
            mvc.perform(options("/article/1/like").header("Origin", origin)
                            .header("Access-Control-Request-Method", "PUT")
                            .header("Access-Control-Request-Headers", "authorization,content-type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin));
        }
        mvc.perform(options("/article/1/like").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "PUT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loopbackOriginsSupportBrowserLoginAndAuthenticatedWrites() throws Exception {
        for (String origin : List.of("http://localhost:5173", "http://127.0.0.1:5173")) {
            send(post("/api/user/login").header("Origin", origin), null,
                    Map.of("username", "alice", "password", PASSWORD))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin))
                    .andExpect(jsonPath("$.data").isString());
            long articleId = body(send(post("/api/article/publish").header("Origin", origin), alice,
                    Map.of("title", "Browser origin check", "content", "Created through the local frontend"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin)))
                    .at("/data/id").asLong();
            send(post("/api/comment/publish").header("Origin", origin), alice,
                    Map.of("articleId", articleId, "content", "A browser comment"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin));
        }
    }

    @Test
    void untrustedOriginCannotSubmitLogin() throws Exception {
        send(post("/api/user/login").header("Origin", "https://untrusted.example"), null,
                Map.of("username", "alice", "password", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private Account account(String name) throws Exception {
        long id = body(send(post("/user/register"), null,
                Map.of("username", name, "password", PASSWORD)).andExpect(status().isOk())).at("/data/id").asLong();
        String token = body(send(post("/user/login"), null,
                Map.of("username", name, "password", PASSWORD)).andExpect(status().isOk())).at("/data").asText();
        return new Account(id, token);
    }

    private long category(Account user, String name) throws Exception {
        return body(send(post("/category"), user, Map.of("name", name)).andExpect(status().isOk()))
                .at("/data/id").asLong();
    }

    private long article(Account user, String title, Long categoryId) throws Exception {
        return body(send(post("/article/publish"), user, articleBody(title, categoryId)).andExpect(status().isOk()))
                .at("/data/id").asLong();
    }

    private Map<String, Object> articleBody(String title, Long categoryId) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("content", "正文内容");
        body.put("categoryId", categoryId);
        return body;
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Account user, Object payload) throws Exception {
        if (user != null) request.header("Authorization", "Bearer " + user.token());
        if (payload != null) request.contentType("application/json").content(json.writeValueAsBytes(payload));
        return mvc.perform(request);
    }

    private JsonNode body(ResultActions response) throws Exception {
        return json.readTree(response.andReturn().getResponse().getContentAsByteArray());
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private long counter(long articleId, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM article WHERE id = ?", Long.class, articleId);
    }

    private void parallel(int operations, java.util.function.IntFunction<?> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Object>> jobs = IntStream.range(0, operations)
                    .mapToObj(i -> (Callable<Object>) () -> task.apply(i)).toList();
            for (Future<Object> result : pool.invokeAll(jobs, 45, TimeUnit.SECONDS)) {
                result.get(1, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
