package com.example.personal_blog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:plaza;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,COMMENT")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlazaIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    record Account(long id, String token) {}
    private Account alice;
    private Account bob;

    @BeforeEach
    void reset() throws Exception {
        for (String table : List.of("user_subscription", "article_like", "comment", "article", "category", "`user`"))
            jdbc.update("DELETE FROM " + table);
        alice = account("alice"); bob = account("bob");
    }

    @Test
    void savingAndEditingArePrivateUnlessTheOwnerExplicitlyPublishes() throws Exception {
        long id = data(send(post("/api/article/publish"), alice,
                Map.of("title", "Default private", "content", "Secret", "published", true, "plazaCategory", "技术")))
                .get("id").asLong();
        send(get("/api/article/detail/" + id), alice, null)
                .andExpect(jsonPath("$.data.published").value(false));
        send(put("/api/article/" + id), alice,
                Map.of("title", "Still private", "content", "Secret", "published", true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.published").value(false));
        send(get("/api/plaza/articles"), null, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        send(get("/api/plaza/articles/" + id), null, null).andExpect(status().isNotFound());
        send(get("/api/article/detail/" + id), bob, null).andExpect(status().isNotFound());
        send(get("/api/plaza/categories").header("Authorization", "Bearer expired-token"), null, null)
                .andExpect(status().isOk()).andExpect(content().json("{\"code\":200,\"message\":\"success\",\"data\":[\"技术\",\"日常\",\"读书\",\"娱乐\"]}"));
    }

    @Test
    void publicListsSearchOnlyPublishedTitlesAndNeverExposePrivateCategoriesOrNotes() throws Exception {
        long privateCategory = data(send(post("/api/category"), alice, Map.of("name", "绝不公开的私人分类"))).get("id").asLong();
        long expected = data(send(post("/api/article/publish"), alice,
                Map.of("title", "100%_阅读", "content", "正文", "categoryId", privateCategory))).get("id").asLong();
        long second = article(bob, "阅读日常", "other author's public text");
        long privateId = article(bob, "100%_阅读私密", "Secret text");
        long bodyOnly = article(alice, "Other title", "100%_阅读 only in body");
        publish(alice, expected, "读书"); publish(bob, second, "日常"); publish(alice, bodyOnly, "读书");
        send(post("/api/comment/publish"), alice, Map.of("articleId", expected, "content", "不能公开的补记"))
                .andExpect(status().isOk());
        for (String prefix : List.of("", "/api")) {
            send(get(prefix + "/plaza/articles").param("keyword", " %_ ").param("category", "读书"), null, null)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.records[0].id").value(expected))
                    .andExpect(jsonPath("$.data.records[0].content").isEmpty())
                    .andExpect(jsonPath("$.data.records[0].categoryId").doesNotExist())
                    .andExpect(jsonPath("$.data.records[0].categoryName").doesNotExist());
            send(get(prefix + "/plaza/articles/" + expected).header("Authorization", "Bearer expired-token"), null, null)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").value("正文"))
                    .andExpect(jsonPath("$.data.category").value("读书"))
                    .andExpect(jsonPath("$.data.authorName").value("alice"))
                    .andExpect(jsonPath("$.data.categoryId").doesNotExist())
                    .andExpect(jsonPath("$.data.comments").doesNotExist())
                    .andExpect(header().string("Cache-Control", "no-store"));
            send(get(prefix + "/plaza/articles/" + privateId), alice, null).andExpect(status().isNotFound());
        }
        // Sharing never grants access to the author's private routes or supplementary notes.
        send(get("/api/article/detail/" + expected), bob, null).andExpect(status().isNotFound());
        send(get("/api/comment/" + expected), bob, null).andExpect(status().isNotFound());
        send(get("/api/article/list"), bob, null).andExpect(jsonPath("$.data.total").value(2));
        jdbc.update("UPDATE article SET published_time='2026-01-02 00:00:00' WHERE id IN (?,?)", expected, second);
        jdbc.update("UPDATE article SET published_time='2026-01-01 00:00:00' WHERE id=?", bodyOnly);
        send(get("/api/plaza/articles").param("size", "2"), null, null)
                .andExpect(jsonPath("$.data.total").value(3)).andExpect(jsonPath("$.data.pages").value(2))
                .andExpect(jsonPath("$.data.records[0].id").value(second))
                .andExpect(jsonPath("$.data.records[1].id").value(expected));
    }

    @Test
    void publishingIsReversibleAndPreservesPrivateClassificationAndText() throws Exception {
        long category = data(send(post("/category"), alice, Map.of("name", "Private shelf"))).get("id").asLong();
        JsonNode original = data(send(post("/article/publish"), alice,
                Map.of("title", "Public title", "content", "Original body", "categoryId", category)));
        long id = original.get("id").asLong();
        publish(alice, id, "技术");
        String firstPublishedTime = data(send(get("/article/detail/" + id), alice, null)).get("publishedTime").asText();
        publish(alice, id, "娱乐");
        send(get("/article/detail/" + id), alice, null)
                .andExpect(jsonPath("$.data.categoryId").value(category))
                .andExpect(jsonPath("$.data.title").value("Public title"))
                .andExpect(jsonPath("$.data.content").value("Original body"))
                .andExpect(jsonPath("$.data.createTime").value(original.get("createTime").asText()))
                .andExpect(jsonPath("$.data.publishedTime").value(firstPublishedTime));
        send(put("/article/" + id), alice, Map.of("title", "Edited title", "content", "Updated public body", "categoryId", category))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.published").value(true));
        send(patch("/article/" + id + "/category"), alice, Collections.singletonMap("categoryId", null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.published").value(true));
        send(get("/plaza/articles/" + id), null, null).andExpect(jsonPath("$.data.content").value("Updated public body"));
        send(put("/article/" + id + "/publication"), alice, Map.of("published", false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.published").value(false))
                .andExpect(jsonPath("$.data.plazaCategory").isEmpty()).andExpect(jsonPath("$.data.publishedTime").isEmpty());
        send(get("/plaza/articles/" + id), null, null).andExpect(status().isNotFound());
        send(get("/plaza/articles"), bob, null).andExpect(jsonPath("$.data.total").value(0));
        publish(alice, id, "日常");
        send(delete("/article/" + id), alice, null).andExpect(status().isOk());
        send(get("/plaza/articles/" + id), null, null).andExpect(status().isNotFound());
    }

    @Test
    void withdrawingIgnoresTheCategoryWhilePublishingStillValidatesIt() throws Exception {
        long id = article(alice, "可撤回文章", "正文");
        publish(alice, id, "技术");
        send(put("/api/article/" + id + "/publication"), alice, Map.of("published", false, "category", "不存在"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.published").value(false))
                .andExpect(jsonPath("$.data.plazaCategory").isEmpty());
        send(get("/api/plaza/articles/" + id), null, null).andExpect(status().isNotFound());
        send(put("/api/article/" + id + "/publication"), alice, Map.of("published", true, "category", "不存在"))
                .andExpect(status().isBadRequest());
        send(put("/api/article/" + id + "/publication"), alice, Map.of("published", false, "category", "类".repeat(21)))
                .andExpect(status().isBadRequest());
        send(get("/api/article/detail/" + id), alice, null).andExpect(jsonPath("$.data.published").value(false));
    }

    @Test
    void onlyTheOwnerCanChangeVisibilityAndInvalidPublicationNeverChangesTheArticle() throws Exception {
        long id = article(alice, "Private", "Secret");
        send(put("/api/article/" + id + "/publication"), null, Map.of("published", true, "category", "技术"))
                .andExpect(status().isUnauthorized());
        for (long target : List.of(id, 999999L)) {
            send(put("/api/article/" + target + "/publication"), bob, Map.of("published", true, "category", "技术"))
                    .andExpect(status().isNotFound());
        }
        for (Map<String, Object> invalid : List.<Map<String, Object>>of(Map.of(), Map.of("published", true),
                Map.of("published", true, "category", "其他"), Map.of("published", true, "category", ""))) {
            send(put("/api/article/" + id + "/publication"), alice, invalid).andExpect(status().isBadRequest());
        }
        for (String path : List.of("/plaza/articles?page=0", "/plaza/articles?size=101", "/plaza/articles?category=其他", "/plaza/articles/-1"))
            send(get(path), null, null).andExpect(status().isBadRequest());
        send(get("/article/detail/" + id), alice, null).andExpect(jsonPath("$.data.published").value(false));
    }

    private long article(Account user, String title, String content) throws Exception {
        return data(send(post("/article/publish"), user, Map.of("title", title, "content", content))).get("id").asLong();
    }
    private void publish(Account user, long id, String category) throws Exception {
        ResultActions result = send(put("/api/article/" + id + "/publication"), user, Map.of("published", true, "category", category));
        if (result.andReturn().getResponse().getStatus() != 200)
            throw new AssertionError("Publishing failed", result.andReturn().getResolvedException());
        result.andExpect(status().isOk()).andExpect(jsonPath("$.data.published").value(true));
    }
    private Account account(String name) throws Exception {
        Map<String, Object> payload = Map.of("username", name, "password", "plaza-test-password");
        long id = data(send(post("/user/register"), null, payload)).get("id").asLong();
        return new Account(id, data(send(post("/user/login"), null, payload)).asText());
    }
    private ResultActions send(MockHttpServletRequestBuilder request, Account user, Object body) throws Exception {
        if (user != null) request.header("Authorization", "Bearer " + user.token());
        if (body != null) request.contentType("application/json").content(json.writeValueAsBytes(body));
        return mvc.perform(request);
    }
    private JsonNode data(ResultActions response) throws Exception {
        return json.readTree(response.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).get("data");
    }
}
