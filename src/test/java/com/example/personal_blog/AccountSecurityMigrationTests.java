package com.example.personal_blog;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class AccountSecurityMigrationTests {
    @Test
    void migrationPreservesDuplicateUnverifiedLegacyEmailsAndIsRepeatable() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:account_migration;MODE=MySQL;NON_KEYWORDS=USER", "sa", "")) {
            var sql = connection.createStatement();
            sql.execute("CREATE TABLE `user` (id BIGINT PRIMARY KEY, email VARCHAR(254))");
            sql.execute("INSERT INTO `user` VALUES (1,'shared@example.test'),(2,'shared@example.test')");
            var migration = new FileSystemResource("database/upgrade-v7-account-security.sql");
            ScriptUtils.executeSqlScript(connection, migration);
            ScriptUtils.executeSqlScript(connection, migration);
            var result = sql.executeQuery("SELECT COUNT(*) FROM verified_email");
            result.next(); assertThat(result.getInt(1)).isZero();
            sql.execute("INSERT INTO verified_email VALUES (1,'verified@example.test',CURRENT_TIMESTAMP)");
            sql.execute("INSERT INTO account_token VALUES ('" + "a".repeat(64) + "',1,'VERIFY_EMAIL','verified@example.test',CURRENT_TIMESTAMP)");
            sql.execute("DELETE FROM `user` WHERE id=1");
            for (String table : new String[]{"verified_email", "account_token"}) {
                result = sql.executeQuery("SELECT COUNT(*) FROM " + table);
                result.next(); assertThat(result.getInt(1)).isZero();
            }
        }
    }
}
