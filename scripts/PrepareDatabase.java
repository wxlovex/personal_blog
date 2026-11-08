import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Initializes only a new personal_blog schema and creates an isolated test schema. */
class PrepareDatabase {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        Map<String, String> config = new HashMap<>();
        for (String line : Files.readAllLines(root.resolve(".env.render"))) {
            int separator = line.indexOf('=');
            if (separator < 1 || line.stripLeading().startsWith("#")) continue;
            String value = line.substring(separator + 1).strip();
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            config.put(line.substring(0, separator).strip(), value);
        }
        String url = config.getOrDefault("DB_URL", "");
        if (!url.startsWith("jdbc:mysql://") || !url.contains("sslMode=VERIFY_IDENTITY")) {
            throw new IllegalArgumentException("A JDBC MySQL URL with VERIFY_IDENTITY is required.");
        }
        int query = url.indexOf('?');
        int slash = url.indexOf('/', "jdbc:mysql://".length());
        if (query < 0 || slash < 0 || !url.substring(slash + 1, query).equals("personal_blog")) {
            throw new IllegalArgumentException("Only the personal_blog database can be initialized.");
        }
        String serverUrl = url.substring(0, slash + 1) + url.substring(query);
        Properties credentials = new Properties();
        credentials.setProperty("user", config.getOrDefault("DB_USERNAME", ""));
        credentials.setProperty("password", config.getOrDefault("DB_PASSWORD", ""));
        credentials.setProperty("connectTimeout", "10000");
        credentials.setProperty("socketTimeout", "30000");
        try (Connection server = DriverManager.getConnection(serverUrl, credentials);
             Statement statement = server.createStatement()) {
            String version;
            try (ResultSet result = statement.executeQuery("SELECT VERSION()")) {
                result.next();
                version = result.getString(1);
            }
            if (version.toLowerCase(Locale.ROOT).contains("tidb")) {
                try (ResultSet check = statement.executeQuery("SELECT @@tidb_enable_check_constraint")) {
                    check.next();
                    if (!check.getBoolean(1)) {
                        // Enforce rather than silently discarding the schema's integrity constraints.
                        statement.execute("SET GLOBAL tidb_enable_check_constraint = ON");
                    }
                }
                try (ResultSet check = statement.executeQuery("SELECT @@tidb_enable_foreign_key")) {
                    check.next();
                    if (!check.getBoolean(1)) throw new IllegalStateException("Foreign keys must be enabled.");
                }
            }
            statement.execute("CREATE DATABASE IF NOT EXISTS personal_blog CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            try (Connection database = DriverManager.getConnection(url, credentials);
                 Statement ddl = database.createStatement()) {
                Set<String> expectedTables = Set.of("user", "article", "comment", "category", "article_like", "user_subscription");
                List<String> existingTables = new ArrayList<>();
                try (ResultSet tables = ddl.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema = 'personal_blog'")) {
                    while (tables.next()) existingTables.add(tables.getString(1));
                }
                for (String table : existingTables) {
                    if (!expectedTables.contains(table)) throw new IllegalStateException("Unexpected table in target database.");
                    try (ResultSet rows = ddl.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
                        rows.next();
                        if (rows.getLong(1) != 0) throw new IllegalStateException("Existing business data found; initialization stopped.");
                    }
                }
                String schema = Files.readString(root.resolve("src/main/resources/db/schema.sql"))
                        .replaceAll("(?m)^\\s*--.*$", "");
                for (String sql : schema.split(";")) {
                    if (!sql.isBlank()) ddl.execute(sql);
                }
                try (ResultSet tables = ddl.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'personal_blog'")) {
                    tables.next();
                    if (tables.getInt(1) != expectedTables.size()) throw new IllegalStateException("Expected six blog tables.");
                }
            }
            String verificationDatabase = "blog_verify_" + Long.toHexString(System.currentTimeMillis());
            statement.execute("CREATE DATABASE " + verificationDatabase + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            Files.writeString(root.resolve("target/render-test-db-url.txt"),
                    url.substring(0, slash + 1) + verificationDatabase + url.substring(query));
            System.out.println("Initialized six blog tables over verified TLS; isolated test database prepared.");
        } catch (SQLException e) {
            System.err.println("Database operation failed: SQLState=" + e.getSQLState() + ", code=" + e.getErrorCode());
            String message = e.getMessage();
            for (String value : List.of(credentials.getProperty("password"), credentials.getProperty("user"), url, serverUrl)) {
                if (!value.isEmpty()) message = message.replace(value, "[redacted]");
            }
            System.err.println(message);
            System.exit(1);
        }
    }
}
