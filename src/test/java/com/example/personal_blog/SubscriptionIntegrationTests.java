package com.example.personal_blog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Legacy social routes must never reveal another journal or alter old relationships. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubscriptionIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    record Account(long id, String token) {}

    @Test
    void socialRoutesAreAuthenticatedTombstonesOnBothApiPrefixes() throws Exception {
        Account viewer = account();
        for (String prefix : List.of("", "/api")) {
            for (var request : List.of(get(prefix + "/article/ranking"), get(prefix + "/article/subscriptions"),
                    get(prefix + "/user/subscriptions"), get(prefix + "/user/999999/subscription"),
                    put(prefix + "/user/999999/subscription"), delete(prefix + "/user/999999/subscription"),
                    put(prefix + "/article/999999/like"), delete(prefix + "/article/999999/like"))) {
                mvc.perform(request).andExpect(status().isUnauthorized());
                mvc.perform(request.header("Authorization", "Bearer " + viewer.token()))
                        .andExpect(status().isGone()).andExpect(jsonPath("$.data").isEmpty());
            }
        }
    }

    @Test
    void historicalSubscriptionDoesNotGrantAccessAndIsNotMutated() throws Exception {
        Account author = account();
        Account viewer = account();
        String created = mvc.perform(post("/api/article/publish").header("Authorization", "Bearer " + author.token())
                .contentType("application/json").content(json.writeValueAsBytes(Map.of("content", "Private history"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long articleId = json.readTree(created).at("/data/id").asLong();
        jdbc.update("INSERT INTO user_subscription(subscriber_id,author_id,create_time) VALUES(?,?,CURRENT_TIMESTAMP)",
                viewer.id(), author.id());
        mvc.perform(get("/api/article/list").header("Authorization", "Bearer " + viewer.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/article/detail/" + articleId).header("Authorization", "Bearer " + viewer.token()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/user/" + author.id() + "/subscription").header("Authorization", "Bearer " + viewer.token()))
                .andExpect(status().isGone());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_subscription WHERE subscriber_id=? AND author_id=?",
                Long.class, viewer.id(), author.id())).isEqualTo(1);
    }

    private Account account() throws Exception {
        String username = "private_" + UUID.randomUUID().toString().substring(0, 12);
        byte[] body = json.writeValueAsBytes(Map.of("username", username, "password", "private-test-password"));
        String registered = mvc.perform(post("/api/user/register").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String loggedIn = mvc.perform(post("/api/user/login").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Account(json.readTree(registered).at("/data/id").asLong(), json.readTree(loggedIn).at("/data").asText());
    }
}
