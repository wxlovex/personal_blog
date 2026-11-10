package com.example.personal_blog;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class PrivateCategoryMigrationTests {
    @Test
    void sharedCategoriesAreCopiedWithoutLosingExistingRecordsOrHistory() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:private-category-migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER,COMMENT", "sa", "")) {
            String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"))
                    .replace("uk_category_user_name UNIQUE (user_id, name)", "uk_category_name UNIQUE (name)");
            run(connection, schema);
            run(connection, """
                    INSERT INTO `user` (id,username,password,create_time) VALUES
                    (1,'alice','hash',CURRENT_TIMESTAMP),(2,'bob','hash',CURRENT_TIMESTAMP),(3,'carol','hash',CURRENT_TIMESTAMP);
                    INSERT INTO category (id,user_id,name,description,create_time) VALUES
                    (10,1,'Shared','Keep this description',CURRENT_TIMESTAMP),(11,2,'Own','Own category',CURRENT_TIMESTAMP);
                    INSERT INTO article (id,user_id,category_id,title,content,create_time,update_time) VALUES
                    (20,2,10,'Bob shared','Bob content',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
                    (21,3,10,'Carol shared','Carol content',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
                    (22,1,10,'Alice original','Alice content',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
                    (23,2,11,'Bob own','Bob own content',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
                    (24,3,NULL,'No category','Unclassified content',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
                    INSERT INTO comment (article_id,user_id,content,create_time) VALUES(20,1,'Historical note',CURRENT_TIMESTAMP);
                    INSERT INTO article_like (article_id,user_id,create_time) VALUES(20,1,CURRENT_TIMESTAMP);
                    INSERT INTO user_subscription (subscriber_id,author_id,create_time) VALUES(1,2,CURRENT_TIMESTAMP);
                    """);
            String migration = Files.readString(Path.of("database/upgrade-v4-private-journal.sql"));
            String mysqlDdl = "ALTER TABLE category DROP INDEX uk_category_name,\n    ADD CONSTRAINT uk_category_user_name UNIQUE (user_id, name);";
            assertThat(migration).contains(mysqlDdl);
            // Only the MySQL-specific DDL/transaction spelling changes for H2; execute the real data migration.
            migration = migration.replace(mysqlDdl, "ALTER TABLE category DROP CONSTRAINT uk_category_name;\n"
                    + "ALTER TABLE category ADD CONSTRAINT uk_category_user_name UNIQUE (user_id, name);")
                    .replace("START TRANSACTION;", "BEGIN;");
            run(connection, migration);
            assertThat(count(connection, "SELECT COUNT(*) FROM article")).isEqualTo(5);
            assertThat(count(connection, "SELECT COUNT(*) FROM category")).isEqualTo(4);
            assertThat(count(connection, "SELECT COUNT(*) FROM article a JOIN category c ON c.id=a.category_id WHERE c.user_id<>a.user_id")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM article WHERE id=20 AND content='Bob content'")).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM article WHERE id=22 AND category_id=10")).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM article WHERE id=24 AND category_id IS NULL")).isEqualTo(1);
            for (String table : new String[]{"comment", "article_like", "user_subscription"})
                assertThat(count(connection, "SELECT COUNT(*) FROM " + table)).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM category WHERE name='Shared' AND description='Keep this description'")).isEqualTo(3);
            run(connection, "INSERT INTO category(user_id,name,description,create_time) VALUES(1,'Own','Allowed duplicate across accounts',CURRENT_TIMESTAMP);");
            assertThatThrownBy(() -> run(connection,
                    "INSERT INTO category(user_id,name,create_time) VALUES(1,'Own',CURRENT_TIMESTAMP);"))
                    .isInstanceOf(org.springframework.jdbc.datasource.init.ScriptStatementFailedException.class);
        }
    }

    private void run(Connection connection, String sql) {
        ScriptUtils.executeSqlScript(connection, new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
    }

    private long count(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next(); return result.getLong(1);
        }
    }
}
