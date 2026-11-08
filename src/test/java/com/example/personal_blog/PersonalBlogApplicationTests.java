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
    void guestsCanBrowseButCannotWriteAndContextDoesNotLeak() throws Exception {
        long id = article(alice, "Guest article", null);
        send(get("/article/list"), null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].authorName").value("alice"))
                .andExpect(jsonPath("$.data.records[0].liked").value(false));
        send(get("/article/detail/" + id), null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("正文内容"))
                .andExpect(jsonPath("$.data.viewCount").value(1));
        send(get("/category/list"), null, null).andExpect(status().isOk());
        send(get("/article/ranking"), null, null).andExpect(status().isOk());
        send(post("/article/publish"), null, articleBody("Denied", null)).andExpect(status().isUnauthorized());
        send(put("/article/" + id + "/like"), null, null).andExpect(status().isUnauthorized());
        send(get("/user/me"), alice, null).andExpect(status().isOk());
        send(get("/user/me"), null, null).andExpect(status().isUnauthorized());
        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    void classificationSupportsOwnershipUniquenessAndCounts() throws Exception {
        long category = category(alice, "Java");
        send(post("/category"), bob, Map.of("name", " Java ")).andExpect(status().isConflict());
        send(put("/category/" + category), bob, Map.of("name", "Hijacked")).andExpect(status().isForbidden());
        send(delete("/category/" + category), bob, null).andExpect(status().isForbidden());
        send(put("/category/" + category), alice, Map.of("name", "Backend", "description", "Server side"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Backend"));
        article(bob, "Classified", category);
        send(get("/category/list"), null, null).andExpect(jsonPath("$.data[0].articleCount").value(1));
        send(delete("/category/" + category), alice, null).andExpect(status().isConflict());
        send(post("/category"), alice, Map.of("name", "   ")).andExpect(status().isBadRequest());
    }

    @Test
    void unusedCategoryCanBeDeletedAndMissingCategoryCannotBeUsed() throws Exception {
        long category = category(alice, "Unused");
        send(delete("/category/" + category), alice, null).andExpect(status().isOk());
        send(post("/article/publish"), alice, articleBody("Invalid category", category))
                .andExpect(status().isNotFound());
        send(delete("/category/" + category), alice, null).andExpect(status().isNotFound());
    }

    @Test
    void articleEditingPreservesCountersAndCanClearCategory() throws Exception {
        long category = category(alice, "Tech");
        long id = article(alice, "Original", category);
        send(put("/article/" + id + "/like"), bob, null).andExpect(status().isOk());
        send(get("/article/detail/" + id), null, null).andExpect(status().isOk());
        send(put("/article/" + id), bob, articleBody("Intrusion", null)).andExpect(status().isForbidden());
        send(put("/article/" + id), alice, articleBody("Edited", null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Edited"))
                .andExpect(jsonPath("$.data.categoryId").isEmpty())
                .andExpect(jsonPath("$.data.viewCount").value(1))
                .andExpect(jsonPath("$.data.likeCount").value(1));
        send(delete("/category/" + category), alice, null).andExpect(status().isOk());
    }

    @Test
    void searchFiltersAndPaginationWorkTogether() throws Exception {
        long java = category(alice, "Java");
        long life = category(bob, "Life");
        long expected = article(alice, "Java concurrency", java);
        article(bob, "Java basics", java);
        article(alice, "Travel diary", life);
        send(get("/article/list").param("categoryId", "" + java).param("authorId", "" + alice.id())
                        .param("keyword", "concurrency"), null, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(expected))
                .andExpect(jsonPath("$.data.records[0].summary").value("正文内容"))
                .andExpect(jsonPath("$.data.records[0].content").isEmpty());
        send(get("/article/list").param("page", "2").param("size", "2"), null, null)
                .andExpect(jsonPath("$.data.records.length()").value(1));
        send(get("/article/list").param("page", "99"), null, null)
                .andExpect(jsonPath("$.data.records").isEmpty());
    }

    @Test
    void paginationAndBodyValidationReturn400() throws Exception {
        for (String url : List.of("/article/list?page=0", "/article/list?size=101",
                "/article/list?size=-1", "/article/list?categoryId=-1",
                "/article/list?page=abc", "/article/ranking?limit=0",
                "/article/ranking?limit=101", "/article/ranking?sort=unexpected",
                "/article/detail/-1")) {
            send(get(url), null, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        }
        send(post("/article/publish"), alice, Map.of("title", " ", "content", "text"))
                .andExpect(status().isBadRequest());
        send(post("/article/publish"), alice, Map.of("title", "T".repeat(51), "content", "text"))
                .andExpect(status().isBadRequest());
        send(post("/comment/publish"), alice, Map.of("content", "missing article"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/user/login").contentType("application/json").content("{bad"))
                .andExpect(status().isBadRequest());
        send(get("/does-not-exist"), null, null).andExpect(status().isNotFound());
    }

    @Test
    void likesAreIdempotentAndPersonalized() throws Exception {
        long id = article(alice, "Like me", null);
        for (int i = 0; i < 3; i++) {
            send(put("/article/" + id + "/like"), bob, null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.liked").value(true))
                    .andExpect(jsonPath("$.data.likeCount").value(1));
        }
        assertThat(count("article_like")).isEqualTo(1);
        send(get("/article/list"), bob, null).andExpect(jsonPath("$.data.records[0].liked").value(true));
        send(get("/article/list"), eve, null).andExpect(jsonPath("$.data.records[0].liked").value(false));
        send(get("/article/ranking?sort=likes"), bob, null).andExpect(jsonPath("$.data[0].liked").value(true));
        for (int i = 0; i < 3; i++) {
            send(delete("/article/" + id + "/like"), bob, null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.liked").value(false))
                    .andExpect(jsonPath("$.data.likeCount").value(0));
        }
        assertThat(count("article_like")).isZero();
    }

    @Test
    void databaseRejectsDuplicateLikeRelations() throws Exception {
        long id = article(alice, "Unique like", null);
        send(put("/article/" + id + "/like"), bob, null).andExpect(status().isOk());
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO article_like(article_id,user_id,create_time) VALUES(?,?,CURRENT_TIMESTAMP)", id, bob.id()))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void rankingUsesCountsAndSupportsCategoryAndLimit() throws Exception {
        long category = category(alice, "Rank");
        long popular = article(alice, "Many views", category);
        long loved = article(bob, "Many likes", category);
        long other = article(eve, "Other category", null);
        for (int i = 0; i < 3; i++) send(get("/article/detail/" + popular), null, null).andExpect(status().isOk());
        send(put("/article/" + loved + "/like"), alice, null).andExpect(status().isOk());
        send(put("/article/" + loved + "/like"), eve, null).andExpect(status().isOk());
        send(put("/article/" + other + "/like"), bob, null).andExpect(status().isOk());
        send(get("/article/ranking?sort=views&limit=1"), null, null)
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].id").value(popular));
        send(get("/article/ranking?sort=likes&categoryId=" + category), null, null)
                .andExpect(jsonPath("$.data.length()").value(2)).andExpect(jsonPath("$.data[0].id").value(loved));
        // Listing and ranking themselves must not increase page views.
        assertThat(counter(popular, "view_count")).isEqualTo(3);
    }

    @Test
    void rankingTiesHaveDeterministicIdOrder() throws Exception {
        long first = article(alice, "First", null);
        long second = article(alice, "Second", null);
        jdbc.update("UPDATE article SET create_time = '2026-01-01 00:00:00'");
        send(get("/article/ranking"), null, null).andExpect(jsonPath("$.data[0].id").value(second))
                .andExpect(jsonPath("$.data[1].id").value(first));
    }

    @Test
    void commentsIncludeAuthorAndSupportPaginationAndModeration() throws Exception {
        long id = article(alice, "Comments", null);
        long comment = body(send(post("/comment/publish"), bob, Map.of("articleId", id, "content", "Hello"))
                .andExpect(status().isOk())).at("/data/id").asLong();
        send(get("/comment/" + id), null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].authorName").value("bob"));
        send(delete("/comment/" + comment), eve, null).andExpect(status().isForbidden());
        send(delete("/comment/" + comment), alice, null).andExpect(status().isOk());
        assertThat(count("comment")).isZero();
    }

    @Test
    void authorCanDeleteOwnCommentAndMissingResourcesAre404() throws Exception {
        long id = article(alice, "Comment owner", null);
        long comment = body(send(post("/comment/publish"), bob, Map.of("articleId", id, "content", "Hi"))
                .andExpect(status().isOk())).at("/data/id").asLong();
        send(delete("/comment/" + comment), bob, null).andExpect(status().isOk());
        for (String path : List.of("/article/detail/999999", "/comment/999999")) {
            send(get(path), null, null).andExpect(status().isNotFound());
        }
        send(put("/article/999999/like"), alice, null).andExpect(status().isNotFound());
        send(post("/comment/publish"), alice, Map.of("articleId", 999999, "content", "Missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingArticleChecksOwnershipAndCleansRelations() throws Exception {
        long id = article(alice, "Delete me", null);
        send(put("/article/" + id + "/like"), bob, null).andExpect(status().isOk());
        send(post("/comment/publish"), bob, Map.of("articleId", id, "content", "Comment"))
                .andExpect(status().isOk());
        send(delete("/article/" + id), bob, null).andExpect(status().isForbidden());
        send(delete("/article/" + id), alice, null).andExpect(status().isOk());
        assertThat(count("article")).isZero();
        assertThat(count("article_like")).isZero();
        assertThat(count("comment")).isZero();
    }

    @Test
    void concurrentViewIncrementsAreNotLost() throws Exception {
        long id = article(alice, "Concurrent views", null);
        parallel(40, i -> articleService.detail(id));
        assertThat(counter(id, "view_count")).isEqualTo(40);
    }

    @Test
    void concurrentRetriesAndDifferentUsersKeepLikeCountsConsistent() throws Exception {
        long id = article(alice, "Concurrent likes", null);
        long[] users = {alice.id(), bob.id(), eve.id()};
        parallel(30, i -> {
            UserContext.setUserId(users[i % users.length]);
            try { return articleService.like(id, true); }
            finally { UserContext.clear(); }
        });
        assertThat(counter(id, "like_count")).isEqualTo(3);
        assertThat(count("article_like")).isEqualTo(3);
        parallel(30, i -> {
            UserContext.setUserId(users[i % users.length]);
            try { return articleService.like(id, false); }
            finally { UserContext.clear(); }
        });
        assertThat(counter(id, "like_count")).isZero();
        assertThat(count("article_like")).isZero();
    }

    @Test
    void mixedLikeAndUnlikeRequestsLeaveAuditableCounts() throws Exception {
        long id = article(alice, "Mixed likes", null);
        parallel(40, i -> {
            UserContext.setUserId(bob.id());
            try { return articleService.like(id, i % 2 == 0); }
            finally { UserContext.clear(); }
        });
        assertThat(counter(id, "like_count")).isEqualTo(count("article_like"));
        assertThat(counter(id, "like_count")).isBetween(0L, 1L);
    }

    @Test
    void corsPreflightAllowsConfiguredFrontendWithoutAuthentication() throws Exception {
        mvc.perform(options("/article/1/like").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/article/1/like").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "PUT"))
                .andExpect(status().isForbidden());
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
