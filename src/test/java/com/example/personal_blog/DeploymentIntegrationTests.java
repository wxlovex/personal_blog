package com.example.personal_blog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:deployment;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,COMMENT")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeploymentIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void sameOriginApiSupportsRealLoginArticleAndCommentsAlongsideLegacyPaths() throws Exception {
        mvc.perform(post("/api/user/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "deploy_user", "password", "deploy-test-password"))))
                .andExpect(status().isOk());
        String login = mvc.perform(post("/api/user/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "deploy_user", "password", "deploy-test-password"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(login).path("data").asText();
        mvc.perform(get("/user/me").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("deploy_user"));
        mvc.perform(get("/api/user/me").header("Authorization", token)).andExpect(status().isOk());
        String published = mvc.perform(post("/api/article/publish").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("title", "Deployment", "content", "Same-origin blog"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode article = json.readTree(published).path("data");
        long articleId = article.path("id").asLong();
        mvc.perform(get("/api/article/detail/" + articleId).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Same-origin blog"));
        mvc.perform(get("/article/list").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(articleId));
        mvc.perform(post("/api/comment/publish").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("articleId", articleId, "content", "Working"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/comment/" + articleId).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].content").value("Working"));
        mvc.perform(get("/api/category/list").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(get("/api/user/subscriptions").header("Authorization", token)).andExpect(status().isGone());
        mvc.perform(post("/api/article/publish").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"denied\",\"content\":\"denied\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientRoutesForwardButUnknownApisAndAssetsStay404() throws Exception {
        for (String path : new String[]{"/", "/about", "/login", "/register", "/publish", "/plaza", "/plaza/123", "/article/123", "/article/123/edit"}) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
        mvc.perform(get("/api/not-found")).andExpect(status().isNotFound());
        mvc.perform(get("/assets/not-found.js")).andExpect(status().isNotFound());
        mvc.perform(get("/api/user/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublicAndDoesNotExposeConfiguration() throws Exception {
        mvc.perform(get("/api/health").header("Authorization", "Bearer stale-token"))
                .andExpect(status().isOk()).andExpect(content().json("{\"status\":\"UP\"}"));
    }
}
