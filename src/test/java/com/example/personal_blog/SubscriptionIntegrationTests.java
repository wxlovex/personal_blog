package com.example.personal_blog;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.example.personal_blog.utils.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 作者订阅功能的独立集成测试。
 *
 * <p>这些测试只通过真实 HTTP 接口、JdbcTemplate 和 schema/migration 文件观察行为，
 * 不直接依赖订阅功能的实现类，避免把测试写死在某一种内部结构上。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubscriptionIntegrationTests {
    private static final String PASSWORD = "correct-password";
    private static final String TEST_SECRET = "integration-test-only-secret-at-least-32-bytes";
    private static final int CONCURRENCY = 8;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private Account alice;
    private Account bob;
    private Account eve;

    record Account(long id, String token) {}

    record Reply(int status, JsonNode body) {}

    @FunctionalInterface
    interface IndexedTask<T> {
        T run(int index) throws Exception;
    }

    @BeforeEach
    void reset() throws Exception {
        UserContext.clear();
        jdbc.update("DELETE FROM user_subscription");
        jdbc.update("DELETE FROM article_like");
        jdbc.update("DELETE FROM comment");
        jdbc.update("DELETE FROM article");
        jdbc.update("DELETE FROM category");
        jdbc.update("DELETE FROM `user`");
        alice = account("alice", null);
        bob = account("bob", null);
        eve = account("eve", null);
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    // ------------------------------------------------------------------
    // 鉴权、参数与基本语义
    // ------------------------------------------------------------------

    @Test
    void subscriptionWritesRequireValidAuthentication() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                put("/user/" + bob.id() + "/subscription"),
                delete("/user/" + bob.id() + "/subscription"))) {
            send(request, null, null)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401));
        }

        for (String token : List.of("not-a-jwt", expiredToken(alice.id()))) {
            for (MockHttpServletRequestBuilder request : List.of(
                    put("/user/" + bob.id() + "/subscription"),
                    delete("/user/" + bob.id() + "/subscription"))) {
                mvc.perform(request.header("Authorization", "Bearer " + token))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value(401));
            }
        }

        send(get("/user/subscriptions"), null, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        send(get("/article/subscriptions"), null, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        // 公开接口携带无效/过期 Token 时仍按既有约定返回 401，避免静默使用旧身份。
        for (String token : List.of("not-a-jwt", expiredToken(alice.id()))) {
            mvc.perform(get("/user/" + bob.id() + "/subscription")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401));
        }

        // 状态查询是公开接口；游客也能看到作者维度的订阅总数。
        send(get("/user/" + bob.id() + "/subscription"), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorId").value(bob.id()))
                .andExpect(jsonPath("$.data.subscribed").value(false))
                .andExpect(jsonPath("$.data.subscriberCount").value(0));
    }

    @Test
    void subscriptionPathsRejectInvalidAndMissingAuthors() throws Exception {
        for (String invalid : List.of("0", "-1", "abc", "9223372036854775808")) {
            send(get("/user/" + invalid + "/subscription"), null, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
            send(put("/user/" + invalid + "/subscription"), alice, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
            send(delete("/user/" + invalid + "/subscription"), alice, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        long missing = 999_999L;
        send(get("/user/" + missing + "/subscription"), null, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
        send(put("/user/" + missing + "/subscription"), alice, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
        send(delete("/user/" + missing + "/subscription"), alice, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void subscriptionLifecycleIsIdempotentAndCountsAreExact() throws Exception {
        long author = bob.id();

        send(get("/user/" + author + "/subscription"), alice, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorId").value(author))
                .andExpect(jsonPath("$.data.subscribed").value(false))
                .andExpect(jsonPath("$.data.subscriberCount").value(0));

        for (int i = 0; i < 3; i++) {
            send(put("/user/" + author + "/subscription"), alice, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.authorId").value(author))
                    .andExpect(jsonPath("$.data.subscribed").value(true))
                    .andExpect(jsonPath("$.data.subscriberCount").value(1));
        }
        assertThat(rowsFor(author, alice.id())).isEqualTo(1);
        assertThat(subscriberRows(author)).isEqualTo(1);

        // 另一个用户看到的状态和总数必须不同，但作者总数相同。
        send(get("/user/" + author + "/subscription"), eve, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(false))
                .andExpect(jsonPath("$.data.subscriberCount").value(1));
        send(put("/user/" + author + "/subscription"), eve, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(true))
                .andExpect(jsonPath("$.data.subscriberCount").value(2));
        send(put("/user/" + author + "/subscription"), eve, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscriberCount").value(2));
        send(get("/user/" + author + "/subscription"), alice, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(true))
                .andExpect(jsonPath("$.data.subscriberCount").value(2));

        for (int i = 0; i < 2; i++) {
            send(delete("/user/" + author + "/subscription"), alice, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.subscribed").value(false))
                    .andExpect(jsonPath("$.data.subscriberCount").value(1));
        }
        assertThat(rowsFor(author, alice.id())).isZero();
        send(get("/user/" + author + "/subscription"), alice, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(false))
                .andExpect(jsonPath("$.data.subscriberCount").value(1));
        send(delete("/user/" + author + "/subscription"), eve, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(false))
                .andExpect(jsonPath("$.data.subscriberCount").value(0));
        assertThat(subscriberRows(author)).isZero();

        // 自己订阅/取消自己都必须是 400，且不能产生关系行。
        send(put("/user/" + bob.id() + "/subscription"), bob, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        send(delete("/user/" + bob.id() + "/subscription"), bob, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        assertThat(subscriberRows(author)).isZero();
    }

    @Test
    void identityComesOnlyFromJwtAndSelfSubscriptionIsRejected() throws Exception {
        long target = bob.id();

        // 非法附加 subscriberId 查询参数不能覆盖 JWT 中的身份。
        send(put("/user/" + target + "/subscription?subscriberId=" + eve.id()), alice, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(true))
                .andExpect(jsonPath("$.data.subscriberCount").value(1));
        assertThat(jdbc.queryForObject(
                "SELECT subscriber_id FROM user_subscription WHERE author_id = ?", Long.class, target))
                .isEqualTo(alice.id());
        assertThat(rowsFor(target, eve.id())).isZero();

        send(get("/user/" + target + "/subscription"), alice, null)
                .andExpect(jsonPath("$.data.subscribed").value(true))
                .andExpect(jsonPath("$.data.subscriberCount").value(1));
        send(get("/user/" + target + "/subscription"), eve, null)
                .andExpect(jsonPath("$.data.subscribed").value(false))
                .andExpect(jsonPath("$.data.subscriberCount").value(1));

        // 双向订阅是两条独立关系。
        send(put("/user/" + alice.id() + "/subscription?subscriberId=" + eve.id()), bob, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(true))
                .andExpect(jsonPath("$.data.subscriberCount").value(1));
        assertThat(rowsFor(target, alice.id())).isEqualTo(1);
        assertThat(rowsFor(alice.id(), bob.id())).isEqualTo(1);

        JsonNode aliceList = subscriptionsPage(alice, "size", "100");
        assertThat(ids(aliceList, "/data/records")).containsExactly(bob.id());
        JsonNode bobList = subscriptionsPage(bob, "size", "100");
        assertThat(ids(bobList, "/data/records")).containsExactly(alice.id());
        JsonNode eveList = subscriptionsPage(eve, "size", "100");
        assertThat(eveList.at("/data/total").asLong()).isZero();
        assertThat(ids(eveList, "/data/records")).isEmpty();

        send(put("/user/" + alice.id() + "/subscription"), alice, null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void bidirectionalSubscriptionsAreIndependent() throws Exception {
        List<Reply> replies = concurrently(2, index -> {
            Account viewer = index == 0 ? alice : bob;
            Account author = index == 0 ? bob : alice;
            return exchange(put("/user/" + author.id() + "/subscription"), viewer);
        });
        for (Reply reply : replies) {
            assertThat(reply.status()).isEqualTo(200);
            assertThat(reply.body().at("/data/subscribed").asBoolean()).isTrue();
        }

        assertThat(subscriberRows(alice.id())).isEqualTo(1);
        assertThat(subscriberRows(bob.id())).isEqualTo(1);
        assertThat(count("user_subscription")).isEqualTo(2);
        send(get("/user/" + bob.id() + "/subscription"), alice, null)
                .andExpect(jsonPath("$.data.subscribed").value(true))
                .andExpect(jsonPath("$.data.subscriberCount").value(1));
        send(get("/user/" + alice.id() + "/subscription"), bob, null)
                .andExpect(jsonPath("$.data.subscribed").value(true))
                .andExpect(jsonPath("$.data.subscriberCount").value(1));

        // 取消一个方向不改变另一个方向。
        send(delete("/user/" + bob.id() + "/subscription"), alice, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(false))
                .andExpect(jsonPath("$.data.subscriberCount").value(0));
        send(get("/user/" + alice.id() + "/subscription"), bob, null)
                .andExpect(jsonPath("$.data.subscribed").value(true))
                .andExpect(jsonPath("$.data.subscriberCount").value(1));
        assertThat(subscriptionsPage(alice, "size", "100").at("/data/total").asLong()).isZero();
        assertThat(ids(subscriptionsPage(bob, "size", "100"), "/data/records"))
                .containsExactly(alice.id());
    }

    // ------------------------------------------------------------------
    // 并发与幂等
    // ------------------------------------------------------------------

    @Test
    void concurrentSubscribesAndUnsubscribesForOneRelationStayIdempotent() throws Exception {
        long target = bob.id();

        List<Reply> subscribes = concurrently(32,
                index -> exchange(put("/user/" + target + "/subscription"), alice));
        for (Reply reply : subscribes) {
            assertThat(reply.status()).isEqualTo(200);
            assertThat(reply.body().at("/data/subscribed").asBoolean()).isTrue();
            assertThat(reply.body().at("/data/subscriberCount").asLong()).isEqualTo(1);
        }
        assertThat(count("user_subscription")).isEqualTo(1);
        assertThat(rowsFor(target, alice.id())).isEqualTo(1);

        List<Reply> unsubscribes = concurrently(32,
                index -> exchange(delete("/user/" + target + "/subscription"), alice));
        for (Reply reply : unsubscribes) {
            assertThat(reply.status()).isEqualTo(200);
            assertThat(reply.body().at("/data/subscribed").asBoolean()).isFalse();
            assertThat(reply.body().at("/data/subscriberCount").asLong()).isZero();
        }
        assertThat(count("user_subscription")).isZero();
    }

    @Test
    void mixedConcurrentSubscribeAndUnsubscribeConverges() throws Exception {
        long target = bob.id();

        List<Reply> replies = concurrently(40, index -> {
            MockHttpServletRequestBuilder request = index % 2 == 0
                    ? put("/user/" + target + "/subscription")
                    : delete("/user/" + target + "/subscription");
            return exchange(request, alice);
        });
        for (int index = 0; index < replies.size(); index++) {
            Reply reply = replies.get(index);
            assertThat(reply.status()).isEqualTo(200);
            // 订阅请求返回 true，取消请求返回 false；总数只能在 0/1 之间，
            // 不要求响应中的总数与并发操作后的最终数据库状态完全同步。
            assertThat(reply.body().at("/data/subscribed").asBoolean())
                    .isEqualTo(index % 2 == 0);
            assertThat(reply.body().at("/data/subscriberCount").asLong()).isBetween(0L, 1L);
        }

        long rows = subscriberRows(target);
        assertThat(rows).isBetween(0L, 1L);
        JsonNode status = body(send(get("/user/" + target + "/subscription"), alice, null)
                .andExpect(status().isOk()));
        assertThat(status.at("/data/subscriberCount").asLong()).isEqualTo(rows);
        assertThat(status.at("/data/subscribed").asBoolean()).isEqualTo(rows == 1);

        // 交错请求结束后，显式重试仍然保持幂等。
        send(put("/user/" + target + "/subscription"), alice, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(true))
                .andExpect(jsonPath("$.data.subscriberCount").value(1));
        send(delete("/user/" + target + "/subscription"), alice, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(false))
                .andExpect(jsonPath("$.data.subscriberCount").value(0));
        assertThat(count("user_subscription")).isZero();
    }

    @Test
    void concurrentDifferentSubscribersKeepCountsAccurate() throws Exception {
        Account dave = account("dave", null);
        Account frank = account("frank", null);
        long author = eve.id();
        List<Account> subscribers = List.of(alice, bob, dave, frank);

        List<Reply> replies = concurrently(subscribers.size(), index -> {
            Account viewer = subscribers.get(index);
            return exchange(put("/user/" + author + "/subscription"), viewer);
        });
        for (Reply reply : replies) {
            assertThat(reply.status()).isEqualTo(200);
            assertThat(reply.body().at("/data/subscribed").asBoolean()).isTrue();
            assertThat(reply.body().at("/data/subscriberCount").asLong()).isBetween(1L, 4L);
        }
        assertThat(subscriberRows(author)).isEqualTo(4);
        send(get("/user/" + author + "/subscription"), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(false))
                .andExpect(jsonPath("$.data.subscriberCount").value(4));

        replies = concurrently(subscribers.size(), index -> {
            Account viewer = subscribers.get(index);
            return exchange(delete("/user/" + author + "/subscription"), viewer);
        });
        for (Reply reply : replies) {
            assertThat(reply.status()).isEqualTo(200);
            assertThat(reply.body().at("/data/subscribed").asBoolean()).isFalse();
            assertThat(reply.body().at("/data/subscriberCount").asLong()).isBetween(0L, 3L);
        }
        assertThat(subscriberRows(author)).isZero();
    }

    @Test
    void crossUserConcurrentSubscribeAndUnsubscribeKeepsRelationsConsistent() throws Exception {
        List<Account[]> pairs = List.of(
                new Account[]{alice, bob}, new Account[]{bob, alice},
                new Account[]{alice, eve}, new Account[]{eve, alice},
                new Account[]{bob, eve}, new Account[]{eve, bob});

        List<Reply> replies = concurrently(60, index -> {
            Account[] pair = pairs.get(index % pairs.size());
            boolean subscribe = ((index / pairs.size()) % 2) == 0;
            MockHttpServletRequestBuilder request = subscribe
                    ? put("/user/" + pair[1].id() + "/subscription")
                    : delete("/user/" + pair[1].id() + "/subscription");
            return exchange(request, pair[0]);
        });
        for (Reply reply : replies) {
            assertThat(reply.status()).isEqualTo(200);
            assertThat(reply.body().at("/data/subscriberCount").asLong()).isBetween(0L, 2L);
        }

        long total = count("user_subscription");
        assertThat(total).isBetween(0L, 6L);
        long pairRows = 0;
        for (Account[] pair : pairs) {
            Account viewer = pair[0];
            Account author = pair[1];
            long rows = rowsFor(author.id(), viewer.id());
            pairRows += rows;
            JsonNode status = body(send(get("/user/" + author.id() + "/subscription"), viewer, null)
                    .andExpect(status().isOk()));
            assertThat(status.at("/data/subscribed").asBoolean()).isEqualTo(rows == 1);
            assertThat(status.at("/data/subscriberCount").asLong())
                    .isEqualTo(subscriberRows(author.id()));
        }
        assertThat(pairRows).isEqualTo(total);

        // 并发结束后按固定顺序清理，确认所有交叉关系都持有唯一一行。
        for (Account[] pair : pairs) {
            send(delete("/user/" + pair[1].id() + "/subscription"), pair[0], null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.subscribed").value(false));
        }
        assertThat(count("user_subscription")).isZero();
        for (Account account : List.of(alice, bob, eve)) {
            assertThat(subscriberRows(account.id())).isZero();
        }
    }

    // ------------------------------------------------------------------
    // 文章列表排序、筛选与分页
    // ------------------------------------------------------------------

    @Test
    void articleListPrioritizesSubscribedAuthorsBeforePagination() throws Exception {
        Account dave = account("dave", null);

        long sOld = articleAt(alice, "s-old", null, "2026-01-01T00:00:00");
        long sNew = articleAt(alice, "s-new", null, "2026-05-01T00:00:00");
        long sTie = articleAt(alice, "s-tie", null, "2026-05-01T00:00:00");
        long bMid = articleAt(bob, "b-mid", null, "2026-03-01T00:00:00");
        long nNewest = articleAt(eve, "n-newest", null, "2026-06-01T00:00:00");
        long nTie = articleAt(eve, "n-tie", null, "2026-06-01T00:00:00");
        long dOld = articleAt(dave, "d-old", null, "2026-04-01T00:00:00");

        List<Long> chronological = idList(nTie, nNewest, sTie, sNew, dOld, bMid, sOld);
        assertThat(ids(listPage(null, "size", "100"), "/data/records"))
                .containsExactlyElementsOf(chronological);
        assertThat(ids(listPage(eve, "size", "100"), "/data/records"))
                .containsExactlyElementsOf(chronological);

        subscribe(eve, alice);
        subscribe(eve, bob);

        // 游客的默认顺序不受其他人的订阅影响。
        assertThat(ids(listPage(null, "size", "100"), "/data/records"))
                .containsExactlyElementsOf(chronological);

        // 已订阅作者（alice、bob）全部排在其他作者之前；
        // 组内按 create_time DESC、id DESC，未订阅作者的最新文章也必须排在已订阅旧文之后。
        List<Long> prioritized = idList(sTie, sNew, bMid, sOld, nTie, nNewest, dOld);
        assertThat(ids(listPage(eve, "size", "100"), "/data/records"))
                .containsExactlyElementsOf(prioritized);

        List<Long> acrossPages = new ArrayList<>();
        for (int page = 1; page <= 4; page++) {
            JsonNode result = listPage(eve, "page", String.valueOf(page), "size", "2");
            assertThat(result.at("/data/total").asLong()).isEqualTo(7);
            acrossPages.addAll(ids(result, "/data/records"));
        }
        assertThat(acrossPages).containsExactlyElementsOf(prioritized);
        assertThat(ids(listPage(eve, "page", "5", "size", "2"), "/data/records")).isEmpty();
    }

    @Test
    void articleListFiltersCombineWithSubscriptionPriority() throws Exception {
        long java = category(alice, "Java");
        long life = category(bob, "Life");

        long aJava1 = articleAt(alice, "alice-java-one", java, "2026-01-01T00:00:00");
        long aJava2 = articleAt(alice, "alice-java-two", java, "2026-01-02T00:00:00");
        long bJava = articleAt(bob, "bob-java", java, "2026-02-01T00:00:00");
        long eJava = articleAt(eve, "eve-java", java, "2026-03-01T00:00:00");
        long aLife = articleAt(alice, "alice-life", life, "2026-04-01T00:00:00");
        long eLife = articleAt(eve, "eve-life", life, "2026-05-01T00:00:00");

        subscribe(eve, alice);
        subscribe(eve, bob);

        JsonNode javaOnly = listPage(eve, "categoryId", String.valueOf(java), "size", "100");
        assertThat(javaOnly.at("/data/total").asLong()).isEqualTo(4);
        assertThat(ids(javaOnly, "/data/records"))
                .containsExactlyElementsOf(idList(bJava, aJava2, aJava1, eJava));

        assertThat(ids(listPage(eve, "authorId", String.valueOf(eve.id()), "size", "100"),
                "/data/records"))
                .containsExactlyElementsOf(idList(eLife, eJava));

        assertThat(ids(listPage(eve, "keyword", "java", "size", "100"), "/data/records"))
                .containsExactlyElementsOf(idList(bJava, aJava2, aJava1, eJava));
        assertThat(ids(listPage(eve, "categoryId", String.valueOf(life), "keyword", "life",
                "size", "100"), "/data/records"))
                .containsExactlyElementsOf(idList(aLife, eLife));
        assertThat(ids(listPage(eve, "categoryId", String.valueOf(java),
                "authorId", String.valueOf(alice.id()), "size", "100"), "/data/records"))
                .containsExactlyElementsOf(idList(aJava2, aJava1));

        // 筛选后仍然在数据库排序再分页，而不是页内重排。
        assertThat(ids(listPage(eve, "categoryId", String.valueOf(java), "page", "1", "size", "2"),
                "/data/records"))
                .containsExactlyElementsOf(idList(bJava, aJava2));
        assertThat(ids(listPage(eve, "categoryId", String.valueOf(java), "page", "2", "size", "2"),
                "/data/records"))
                .containsExactlyElementsOf(idList(aJava1, eJava));
    }

    @Test
    void cancellingAndResubscribingImmediatelyRestoresArticleOrder() throws Exception {
        long old = articleAt(alice, "alice-old", null, "2026-01-01T00:00:00");
        long fresh = articleAt(bob, "bob-new", null, "2026-03-01T00:00:00");

        assertThat(ids(listPage(eve, "size", "100"), "/data/records"))
                .containsExactlyElementsOf(idList(fresh, old));

        send(put("/user/" + alice.id() + "/subscription"), eve, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(true))
                .andExpect(jsonPath("$.data.subscriberCount").value(1));
        JsonNode prioritized = listPage(eve, "size", "100");
        assertThat(ids(prioritized, "/data/records")).containsExactlyElementsOf(idList(old, fresh));
        assertThat(recordById(prioritized, "/data/records", old).path("subscribed").asBoolean())
                .isTrue();
        assertThat(recordById(prioritized, "/data/records", fresh).path("subscribed").asBoolean())
                .isFalse();

        send(delete("/user/" + alice.id() + "/subscription"), eve, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(false))
                .andExpect(jsonPath("$.data.subscriberCount").value(0));
        JsonNode restored = listPage(eve, "size", "100");
        assertThat(ids(restored, "/data/records")).containsExactlyElementsOf(idList(fresh, old));
        assertThat(recordById(restored, "/data/records", old).path("subscribed").asBoolean())
                .isFalse();

        send(put("/user/" + alice.id() + "/subscription"), eve, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(true))
                .andExpect(jsonPath("$.data.subscriberCount").value(1));
        assertThat(ids(listPage(eve, "size", "100"), "/data/records"))
                .containsExactlyElementsOf(idList(old, fresh));
        assertThat(subscriberRows(alice.id())).isEqualTo(1);
        assertThat(ids(feedPage(eve, "size", "100"), "/data/records"))
                .containsExactlyElementsOf(idList(old));
    }

    @Test
    void newArticleFromSubscribedAuthorAutomaticallyEntersFeed() throws Exception {
        long old = articleAt(alice, "alice-old", null, "2026-01-01T00:00:00");
        long bobArticle = articleAt(bob, "bob-article", null, "2026-02-01T00:00:00");
        subscribe(eve, alice);

        assertThat(ids(feedPage(eve, "size", "100"), "/data/records"))
                .containsExactlyElementsOf(idList(old));
        assertThat(ids(listPage(eve, "size", "100"), "/data/records"))
                .containsExactlyElementsOf(idList(old, bobArticle));

        // 新文章不需要复制历史文章，只需发布后自动进入已订阅作者的文章集合。
        long fresh = article(alice, "alice-new", null);
        JsonNode feed = feedPage(eve, "size", "100");
        assertThat(feed.at("/data/total").asLong()).isEqualTo(2);
        assertThat(ids(feed, "/data/records")).containsExactlyElementsOf(idList(fresh, old));
        assertThat(recordById(feed, "/data/records", fresh).path("subscribed").asBoolean()).isTrue();
        assertThat(ids(listPage(eve, "size", "100"), "/data/records"))
                .containsExactlyElementsOf(idList(fresh, old, bobArticle));
        assertThat(subscriberRows(alice.id())).isEqualTo(1);
        assertThat(count("user_subscription")).isEqualTo(1);
    }

    @Test
    void subscriptionFeedIsScopedFilterablePaginatedAndReadOnly() throws Exception {
        Account dave = account("dave", null);
        long java = category(alice, "Java");
        long life = category(bob, "Life");

        long aJava = articleAt(alice, "alice-java", java, "2026-01-01T00:00:00");
        long aLife = articleAt(alice, "alice-life", life, "2026-04-01T00:00:00");
        long bJava = articleAt(bob, "bob-java", java, "2026-02-01T00:00:00");
        long eJava = articleAt(eve, "eve-java", java, "2026-03-01T00:00:00");
        long dJava = articleAt(dave, "dave-java", java, "2026-05-01T00:00:00");

        subscribe(eve, alice);
        subscribe(eve, bob);
        Map<Long, Long> viewsBefore = viewCounts(aJava, aLife, bJava, eJava, dJava);

        JsonNode feed = feedPage(eve, "size", "100");
        assertThat(feed.at("/data/total").asLong()).isEqualTo(3);
        assertThat(ids(feed, "/data/records"))
                .containsExactlyElementsOf(idList(aLife, bJava, aJava));

        assertThat(ids(feedPage(eve, "page", "1", "size", "2"), "/data/records"))
                .containsExactlyElementsOf(idList(aLife, bJava));
        assertThat(ids(feedPage(eve, "page", "2", "size", "2"), "/data/records"))
                .containsExactlyElementsOf(idList(aJava));

        JsonNode javaOnly = feedPage(eve, "categoryId", String.valueOf(java), "size", "100");
        assertThat(javaOnly.at("/data/total").asLong()).isEqualTo(2);
        assertThat(ids(javaOnly, "/data/records"))
                .containsExactlyElementsOf(idList(bJava, aJava));
        assertThat(ids(feedPage(eve, "authorId", String.valueOf(bob.id()), "size", "100"),
                "/data/records"))
                .containsExactlyElementsOf(idList(bJava));
        assertThat(ids(feedPage(eve, "authorId", String.valueOf(eve.id()), "size", "100"),
                "/data/records"))
                .isEmpty();
        assertThat(ids(feedPage(eve, "keyword", "java", "size", "100"), "/data/records"))
                .containsExactlyElementsOf(idList(bJava, aJava));

        for (String query : List.of("page=0", "size=0", "size=101", "page=abc")) {
            send(get("/article/subscriptions?" + query), eve, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
        assertThat(ids(feedPage(eve, "page", "99", "size", "10"), "/data/records")).isEmpty();
        assertThat(viewCounts(aJava, aLife, bJava, eJava, dJava)).isEqualTo(viewsBefore);

        // 没有订阅的用户返回空分页，不混入其他作者。
        JsonNode empty = feedPage(dave, "size", "100");
        assertThat(empty.at("/data/total").asLong()).isZero();
        assertThat(ids(empty, "/data/records")).isEmpty();

        send(get("/article/subscriptions"), null, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ------------------------------------------------------------------
    // subscribed 字段与排行榜
    // ------------------------------------------------------------------

    @Test
    void subscribedFlagAppearsInListDetailAndRankingWithoutChangingRanking() throws Exception {
        Account dave = account("dave", null);

        long alicePopular = articleAt(alice, "alice-popular", null, "2026-01-01T00:00:00");
        long bobPopular = articleAt(bob, "bob-popular", null, "2026-02-01T00:00:00");
        long aliceLiked = articleAt(alice, "alice-liked", null, "2026-03-01T00:00:00");
        long bobLiked = articleAt(bob, "bob-liked", null, "2026-04-01T00:00:00");
        setCounters(alicePopular, 8, 1);
        setCounters(bobPopular, 11, 0);
        setCounters(aliceLiked, 1, 8);
        setCounters(bobLiked, 2, 9);

        subscribe(eve, alice);

        JsonNode guestViews = ranking(null, "views", 10);
        JsonNode viewerViews = ranking(eve, "views", 10);
        List<Long> expectedViews = idList(bobPopular, alicePopular, bobLiked, aliceLiked);
        assertThat(ids(guestViews, "/data")).containsExactlyElementsOf(expectedViews);
        assertThat(ids(viewerViews, "/data")).containsExactlyElementsOf(expectedViews);
        assertThat(articleById(guestViews, "/data", alicePopular).path("subscribed").asBoolean())
                .isFalse();
        assertThat(articleById(viewerViews, "/data", alicePopular).path("subscribed").asBoolean())
                .isTrue();
        assertThat(articleById(viewerViews, "/data", bobPopular).path("subscribed").asBoolean())
                .isFalse();

        JsonNode guestLikes = ranking(null, "likes", 10);
        JsonNode viewerLikes = ranking(eve, "likes", 10);
        List<Long> expectedLikes = idList(bobLiked, aliceLiked, alicePopular, bobPopular);
        assertThat(ids(guestLikes, "/data")).containsExactlyElementsOf(expectedLikes);
        assertThat(ids(viewerLikes, "/data")).containsExactlyElementsOf(expectedLikes);

        JsonNode list = listPage(eve, "size", "100");
        assertThat(recordById(list, "/data/records", alicePopular).path("subscribed").asBoolean())
                .isTrue();
        assertThat(recordById(list, "/data/records", bobPopular).path("subscribed").asBoolean())
                .isFalse();

        send(get("/article/detail/" + alicePopular), eve, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(true));
        send(get("/article/detail/" + alicePopular), dave, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(false));
        send(get("/article/detail/" + alicePopular), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(false));

        send(delete("/user/" + alice.id() + "/subscription"), eve, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(false));
        JsonNode restored = listPage(eve, "size", "100");
        assertThat(ids(restored, "/data/records"))
                .containsExactlyElementsOf(idList(bobLiked, aliceLiked, bobPopular, alicePopular));
        assertThat(recordById(restored, "/data/records", alicePopular).path("subscribed").asBoolean())
                .isFalse();
    }

    // ------------------------------------------------------------------
    // 用户订阅列表
    // ------------------------------------------------------------------

    @Test
    void subscriptionsListIsOrderedPaginatedAndOmitsSensitiveFields() throws Exception {
        Account dave = account("dave", "dave@example.com");
        Account frank = account("frank", null);
        subscribe(alice, bob);
        subscribe(alice, eve);
        subscribe(alice, dave);
        subscribe(alice, frank);

        subscribedAt(alice.id(), bob.id(), "2026-01-01T00:00:00");
        subscribedAt(alice.id(), eve.id(), "2026-02-01T00:00:00");
        subscribedAt(alice.id(), dave.id(), "2026-02-01T00:00:00");
        subscribedAt(alice.id(), frank.id(), "2026-03-01T00:00:00");

        article(bob, "bob-one", null);
        article(bob, "bob-two", null);
        article(eve, "eve-one", null);

        JsonNode first = subscriptionsPage(alice, "page", "1", "size", "2");
        assertThat(first.at("/data/total").asLong()).isEqualTo(4);
        assertThat(ids(first, "/data/records"))
                .containsExactlyElementsOf(idList(frank.id(), dave.id()));
        for (JsonNode record : first.at("/data/records")) {
            assertThat(record.size()).isEqualTo(4);
            assertThat(record.path("username").asText()).isNotBlank();
            assertThat(record.has("subscribedAt")).isTrue();
            assertThat(record.path("subscribedAt").isNull()).isFalse();
            assertThat(record.path("articleCount").isNumber()).isTrue();
            assertThat(record.has("password")).isFalse();
            assertThat(record.has("email")).isFalse();
        }
        assertThat(recordById(first, "/data/records", dave.id()).has("email")).isFalse();
        assertThat(recordById(first, "/data/records", dave.id()).has("password")).isFalse();
        assertThat(recordById(first, "/data/records", frank.id()).path("articleCount").asLong())
                .isZero();

        JsonNode second = subscriptionsPage(alice, "page", "2", "size", "2");
        assertThat(ids(second, "/data/records"))
                .containsExactlyElementsOf(idList(eve.id(), bob.id()));
        assertThat(recordById(second, "/data/records", eve.id()).path("articleCount").asLong())
                .isEqualTo(1);
        assertThat(recordById(second, "/data/records", bob.id()).path("articleCount").asLong())
                .isEqualTo(2);

        JsonNode third = subscriptionsPage(alice, "page", "3", "size", "2");
        assertThat(third.at("/data/total").asLong()).isEqualTo(4);
        assertThat(ids(third, "/data/records")).isEmpty();
        assertThat(ids(subscriptionsPage(alice, "page", "99", "size", "10"), "/data/records"))
                .isEmpty();

        for (String query : List.of("page=0", "size=0", "size=101", "page=abc")) {
            send(get("/user/subscriptions?" + query), alice, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        // 每个查看者只看到自己的订阅列表。
        JsonNode bobList = subscriptionsPage(bob, "size", "100");
        assertThat(bobList.at("/data/total").asLong()).isZero();
        assertThat(ids(bobList, "/data/records")).isEmpty();
        send(get("/user/subscriptions"), null, null)
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // 数据库约束与迁移脚本
    // ------------------------------------------------------------------

    @Test
    void databaseEnforcesUniqueSelfForeignKeyAndCascadeRules() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO user_subscription(subscriber_id, author_id, create_time) VALUES(?,?,?)",
                alice.id(), bob.id(), Timestamp.valueOf(now));
        Long id = jdbc.queryForObject(
                "SELECT id FROM user_subscription WHERE subscriber_id = ? AND author_id = ?",
                Long.class, alice.id(), bob.id());
        assertThat(id).isNotNull().isPositive();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO user_subscription(subscriber_id, author_id, create_time) VALUES(?,?,?)",
                alice.id(), bob.id(), Timestamp.valueOf(now)))
                .isInstanceOf(DuplicateKeyException.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO user_subscription(subscriber_id, author_id, create_time) VALUES(?,?,?)",
                alice.id(), alice.id(), Timestamp.valueOf(now)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("constraint");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO user_subscription(subscriber_id, author_id, create_time) VALUES(?,?,?)",
                999_999L, bob.id(), Timestamp.valueOf(now)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO user_subscription(subscriber_id, author_id, create_time) VALUES(?,?,?)",
                alice.id(), 999_999L, Timestamp.valueOf(now)))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 再建立一条以 bob 为订阅者的关系，确保删除 bob 时两侧外键都级联。
        jdbc.update("INSERT INTO user_subscription(subscriber_id, author_id, create_time) VALUES(?,?,?)",
                bob.id(), eve.id(), Timestamp.valueOf(now));
        assertThat(count("user_subscription")).isEqualTo(2);

        // 作者/订阅者被删除时，订阅关系由外键级联清理。
        jdbc.update("DELETE FROM `user` WHERE id = ?", bob.id());
        assertThat(subscriberRows(bob.id())).isZero();
        assertThat(count("user_subscription")).isZero();
    }

    @Test
    void schemaAndUpgradeScriptDeclareSubscriptionRules() throws Exception {
        String schema = normalized(Files.readString(
                Path.of("src/main/resources/db/schema.sql")));
        assertThat(schema).contains("create table if not exists user_subscription");
        assertThat(schema).contains("unique (subscriber_id, author_id)");
        assertThat(schema).contains("generated always as (subscriber_id <> author_id)");
        assertThat(schema).contains("check (not_self = true)");
        assertThat(occurrences(schema, "references user (id) on delete cascade")).isEqualTo(2);

        Path upgrade = Path.of("database/upgrade-v3-subscriptions.sql");
        assertThat(upgrade).exists();
        String migration = normalized(Files.readString(upgrade));
        assertThat(migration).contains("create table if not exists user_subscription");
        assertThat(migration).contains("subscriber_id");
        assertThat(migration).contains("author_id");
        assertThat(migration).contains("unique (subscriber_id, author_id)");
        assertThat(migration).contains("generated always as (subscriber_id <> author_id)");
        assertThat(migration).contains("check (not_self = true)");
        assertThat(occurrences(migration, "references user (id) on delete cascade")).isEqualTo(2);
        assertThat(migration).doesNotContain("drop table", "truncate table", "delete from");
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private Account account(String name, String email) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", name);
        payload.put("password", PASSWORD);
        if (email != null) payload.put("email", email);
        long id = body(send(post("/user/register"), null, payload)
                .andExpect(status().isOk())).at("/data/id").asLong();
        String token = body(send(post("/user/login"), null,
                Map.of("username", name, "password", PASSWORD))
                .andExpect(status().isOk())).at("/data").asText();
        return new Account(id, token);
    }

    private long category(Account user, String name) throws Exception {
        return body(send(post("/category"), user, Map.of("name", name))
                .andExpect(status().isOk())).at("/data/id").asLong();
    }

    private long article(Account user, String title, Long categoryId) throws Exception {
        return body(send(post("/article/publish"), user, articleBody(title, categoryId))
                .andExpect(status().isOk())).at("/data/id").asLong();
    }

    private long articleAt(Account user, String title, Long categoryId, String createTime)
            throws Exception {
        long id = article(user, title, categoryId);
        Timestamp timestamp = Timestamp.valueOf(LocalDateTime.parse(createTime));
        jdbc.update("UPDATE article SET create_time = ?, update_time = ? WHERE id = ?",
                timestamp, timestamp, id);
        return id;
    }

    private Map<String, Object> articleBody(String title, Long categoryId) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("content", "正文内容");
        body.put("categoryId", categoryId);
        return body;
    }

    private void subscribe(Account viewer, Account author) throws Exception {
        send(put("/user/" + author.id() + "/subscription"), viewer, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(true));
    }

    private JsonNode listPage(Account viewer, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/article/list");
        addParams(request, params);
        return body(send(request, viewer, null).andExpect(status().isOk()));
    }

    private JsonNode feedPage(Account viewer, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/article/subscriptions");
        addParams(request, params);
        return body(send(request, viewer, null).andExpect(status().isOk()));
    }

    private JsonNode subscriptionsPage(Account viewer, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/user/subscriptions");
        addParams(request, params);
        return body(send(request, viewer, null).andExpect(status().isOk()));
    }

    private JsonNode ranking(Account viewer, String sort, int limit) throws Exception {
        return body(send(get("/article/ranking")
                .param("sort", sort)
                .param("limit", String.valueOf(limit)), viewer, null)
                .andExpect(status().isOk()));
    }

    private void addParams(MockHttpServletRequestBuilder request, String... params) {
        assertThat(params.length % 2).isZero();
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Account user, Object payload)
            throws Exception {
        if (user != null) request.header("Authorization", "Bearer " + user.token());
        if (payload != null) {
            request.contentType("application/json").content(json.writeValueAsBytes(payload));
        }
        return mvc.perform(request);
    }

    private Reply exchange(MockHttpServletRequestBuilder request, Account user) throws Exception {
        if (user != null) request.header("Authorization", "Bearer " + user.token());
        MvcResult result = mvc.perform(request).andReturn();
        byte[] content = result.getResponse().getContentAsByteArray();
        JsonNode parsed = content.length == 0
                ? json.getNodeFactory().nullNode()
                : json.readTree(content);
        return new Reply(result.getResponse().getStatus(), parsed);
    }

    private JsonNode body(ResultActions response) throws Exception {
        return json.readTree(response.andReturn().getResponse().getContentAsByteArray());
    }

    private <T> List<T> concurrently(int operations, IndexedTask<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch ready = new CountDownLatch(Math.min(operations, CONCURRENCY));
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < operations; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("并发测试启动超时");
                    }
                    return task.run(index);
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).as("并发线程就绪").isTrue();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private long rowsFor(long authorId, long subscriberId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_subscription "
                + "WHERE author_id = ? AND subscriber_id = ?", Long.class, authorId, subscriberId);
    }

    private long subscriberRows(long authorId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_subscription WHERE author_id = ?",
                Long.class, authorId);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private void subscribedAt(long subscriberId, long authorId, String time) {
        jdbc.update("UPDATE user_subscription SET create_time = ? "
                        + "WHERE subscriber_id = ? AND author_id = ?",
                Timestamp.valueOf(LocalDateTime.parse(time)), subscriberId, authorId);
    }

    private void setCounters(long articleId, long views, long likes) {
        jdbc.update("UPDATE article SET view_count = ?, like_count = ? WHERE id = ?",
                views, likes, articleId);
    }

    private Map<Long, Long> viewCounts(long... articleIds) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (long id : articleIds) {
            counts.put(id, jdbc.queryForObject(
                    "SELECT view_count FROM article WHERE id = ?", Long.class, id));
        }
        return counts;
    }

    private JsonNode recordById(JsonNode body, String pointer, long id) {
        for (JsonNode record : body.at(pointer)) {
            if (record.path("id").asLong() == id) return record;
        }
        fail("未找到 id 为 %d 的记录，响应为 %s", id, body);
        throw new AssertionError("unreachable");
    }

    private JsonNode articleById(JsonNode body, String pointer, long id) {
        return recordById(body, pointer, id);
    }

    private List<Long> ids(JsonNode body, String pointer) {
        JsonNode array = body.at(pointer);
        assertThat(array.isArray()).as("路径 %s 应为数组，响应为 %s", pointer, body).isTrue();
        List<Long> result = new ArrayList<>();
        array.forEach(node -> result.add(
                node.isObject() ? node.path("id").asLong() : node.asLong()));
        return result;
    }

    private static List<Long> idList(long... ids) {
        return Arrays.stream(ids).boxed().toList();
    }

    private String expiredToken(long userId) {
        return JWT.create()
                .withIssuer("personal-blog")
                .withClaim("userId", userId)
                .withExpiresAt(Instant.now().minusSeconds(120))
                .sign(Algorithm.HMAC256(TEST_SECRET));
    }

    private String normalized(String sql) {
        return sql.toLowerCase(Locale.ROOT)
                .replace("`", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private int occurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
