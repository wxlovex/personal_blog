-- MySQL 8.0.16+; run after upgrade-v2.sql for the original three-table application.
-- Existing v2 installations only need this additive migration.
-- Back up first and confirm user.id is a signed BIGINT and the user table uses InnoDB.
-- No existing users, articles, comments, likes or counters are changed.
-- Re-running is safe for a table created by this script; IF NOT EXISTS does not repair other schemas.
CREATE TABLE IF NOT EXISTS user_subscription (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscriber_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    not_self BOOLEAN GENERATED ALWAYS AS (subscriber_id <> author_id),
    create_time DATETIME NOT NULL,
    CONSTRAINT uk_subscription_subscriber_author UNIQUE (subscriber_id, author_id),
    CONSTRAINT ck_subscription_not_self CHECK (not_self = TRUE),
    CONSTRAINT fk_subscription_subscriber FOREIGN KEY (subscriber_id) REFERENCES `user` (id) ON DELETE CASCADE,
    CONSTRAINT fk_subscription_author FOREIGN KEY (author_id) REFERENCES `user` (id) ON DELETE CASCADE,
    INDEX idx_subscription_feed (subscriber_id, create_time, author_id),
    INDEX idx_subscription_author (author_id)
);
