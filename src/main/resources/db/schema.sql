-- New installations only. Existing installations: apply the appropriate v2/v3/v4/v5/v6/v7 upgrades in database/.
-- Use a dedicated database with utf8mb4 encoding in MySQL.
CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(32) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(254),
    create_time DATETIME NOT NULL,
    CONSTRAINT uk_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS auth_session (
    session_id CHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE,
    INDEX idx_session_user_expiry (user_id, expires_at)
);

CREATE TABLE IF NOT EXISTS verified_email (
    user_id BIGINT PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    verified_at DATETIME NOT NULL,
    CONSTRAINT uk_verified_email UNIQUE (email),
    CONSTRAINT fk_verified_email_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS account_token (
    token_hash CHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    email VARCHAR(254) NOT NULL,
    expires_at DATETIME NOT NULL,
    CONSTRAINT uk_account_token_purpose UNIQUE (user_id, purpose),
    CONSTRAINT fk_account_token_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(40) NOT NULL,
    description VARCHAR(200) NOT NULL DEFAULT '',
    create_time DATETIME NOT NULL,
    CONSTRAINT uk_category_user_name UNIQUE (user_id, name),
    CONSTRAINT fk_category_user FOREIGN KEY (user_id) REFERENCES `user` (id)
);

CREATE TABLE IF NOT EXISTS article (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category_id BIGINT,
    title VARCHAR(50) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    like_count BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    plaza_category VARCHAR(20),
    published_time DATETIME,
    CONSTRAINT ck_article_publication CHECK (
        (published = FALSE AND plaza_category IS NULL AND published_time IS NULL) OR
        (published = TRUE AND plaza_category IS NOT NULL AND plaza_category IN ('技术','日常','读书','娱乐') AND published_time IS NOT NULL)
    ),
    CONSTRAINT fk_article_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_article_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT ck_article_views CHECK (view_count >= 0),
    CONSTRAINT ck_article_likes CHECK (like_count >= 0),
    INDEX idx_article_created (create_time, id),
    INDEX idx_article_public_time (published, published_time, id),
    INDEX idx_article_plaza (published, plaza_category, published_time, id),
    INDEX idx_article_author (user_id, create_time, id),
    INDEX idx_article_category (category_id, create_time, id),
    INDEX idx_article_views (view_count, create_time, id),
    INDEX idx_article_likes (like_count, create_time, id)
);

CREATE TABLE IF NOT EXISTS comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    article_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    create_time DATETIME NOT NULL,
    CONSTRAINT fk_comment_article FOREIGN KEY (article_id) REFERENCES article (id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    INDEX idx_comment_article_created (article_id, create_time, id)
);

CREATE TABLE IF NOT EXISTS article_like (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    article_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL,
    CONSTRAINT uk_like_article_user UNIQUE (article_id, user_id),
    CONSTRAINT fk_like_article FOREIGN KEY (article_id) REFERENCES article (id) ON DELETE CASCADE,
    CONSTRAINT fk_like_user FOREIGN KEY (user_id) REFERENCES `user` (id)
);

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
