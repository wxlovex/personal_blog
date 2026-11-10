package com.example.personal_blog;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class AuthSessionMigrationTests {
    @Test
    void migrationIsRepeatablePreservesExistingDataAndCascadesDeletedUsers() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:session_migration;MODE=MySQL;NON_KEYWORDS=USER", "sa", "")) {
            var sql = connection.createStatement();
            sql.execute("CREATE TABLE `user` (id BIGINT PRIMARY KEY, username VARCHAR(32))");
            sql.execute("CREATE TABLE article (id BIGINT PRIMARY KEY, content VARCHAR(100))");
            sql.execute("INSERT INTO `user` VALUES (1, 'existing_user')");
            sql.execute("INSERT INTO article VALUES (1, 'existing private diary')");
            var migration = new FileSystemResource("database/upgrade-v6-auth-sessions.sql");
            ScriptUtils.executeSqlScript(connection, migration);
            ScriptUtils.executeSqlScript(connection, migration);
            var result = sql.executeQuery("SELECT content FROM article WHERE id=1");
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("existing private diary");
            sql.execute("INSERT INTO auth_session VALUES ('00000000-0000-0000-0000-000000000001', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            sql.execute("DELETE FROM `user` WHERE id=1");
            result = sql.executeQuery("SELECT COUNT(*) FROM auth_session");
            result.next();
            assertThat(result.getInt(1)).isZero();
        }
    }
}
