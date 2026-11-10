package com.example.personal_blog;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.support.EncodedResource;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class PlazaMigrationTests {
    @Test
    void upgradingExistingArticlesKeepsAllOfThemPrivateAndPreservesContent() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:plaza-migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "");
             var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE article(id BIGINT PRIMARY KEY,title VARCHAR(50),content TEXT)");
            sql.execute("INSERT INTO article VALUES(1,'Existing title','Private body'),(2,'Another title','Keep me private')");
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new FileSystemResource("database/upgrade-v5-plaza.sql"), StandardCharsets.UTF_8));
            try (var result = sql.executeQuery("SELECT COUNT(*) FROM article WHERE published=FALSE AND plaza_category IS NULL AND published_time IS NULL")) {
                result.next(); assertThat(result.getInt(1)).isEqualTo(2);
            }
            try (var result = sql.executeQuery("SELECT content FROM article WHERE id=1")) {
                result.next(); assertThat(result.getString(1)).isEqualTo("Private body");
            }
            assertThatThrownBy(() -> sql.execute("UPDATE article SET published=TRUE WHERE id=1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThat(sql.executeUpdate("UPDATE article SET published=TRUE,plaza_category='技术',published_time=CURRENT_TIMESTAMP WHERE id=1")).isEqualTo(1);
        }
    }
}
