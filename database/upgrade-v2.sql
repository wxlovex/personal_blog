-- MySQL 8.x upgrade from the original User/Article/Comment model.
-- Back up first. Stop application writers while migrating.
-- Inspect SHOW CREATE TABLE for all three original tables before running.
-- Prerequisites: BIGINT signed IDs, auto-increment primary keys, InnoDB tables.
-- If equivalent indexes/foreign keys already exist, omit their ADD statements.
-- MySQL DDL auto-commits: this is a reviewed one-time migration, not startup SQL.
-- Historical DATETIME values are preserved. Confirm the old server timezone
-- before converting historical dates to UTC; all new application writes use UTC.

-- Run with the mysql batch client WITHOUT --force so a failed check stops execution.
-- The migration account needs CREATE ROUTINE/ALTER ROUTINE in addition to DDL privileges.
DROP PROCEDURE IF EXISTS blog_v2_preflight;
DELIMITER $$
CREATE PROCEDURE blog_v2_preflight()
BEGIN
    IF EXISTS (SELECT username FROM `user` GROUP BY username HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Migration stopped: duplicate usernames';
    END IF;
    IF EXISTS (SELECT id FROM `user` WHERE username IS NULL OR password IS NULL OR create_time IS NULL)
        OR EXISTS (SELECT id FROM article WHERE user_id IS NULL OR title IS NULL OR content IS NULL OR create_time IS NULL)
        OR EXISTS (SELECT id FROM comment WHERE content IS NULL OR create_time IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Migration stopped: required fields contain NULL';
    END IF;
    IF EXISTS (SELECT a.id FROM article a LEFT JOIN `user` u ON a.user_id = u.id WHERE u.id IS NULL)
        OR EXISTS (SELECT c.id FROM comment c LEFT JOIN article a ON c.article_id = a.id
            LEFT JOIN `user` u ON c.user_id = u.id WHERE a.id IS NULL OR u.id IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Migration stopped: orphan articles or comments';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE()
        AND table_name IN ('category', 'article_like'))
        OR EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
            AND table_name = 'article' AND column_name IN ('category_id', 'update_time')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Migration stopped: already upgraded or partially migrated';
    END IF;
END$$
DELIMITER ;
CALL blog_v2_preflight();
DROP PROCEDURE blog_v2_preflight;

-- All checks passed. Backups and a writer maintenance window are still required.
ALTER TABLE `user` ADD CONSTRAINT uk_user_username UNIQUE (username);

CREATE TABLE category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(40) NOT NULL,
    description VARCHAR(200) NOT NULL DEFAULT '',
    create_time DATETIME NOT NULL,
    CONSTRAINT uk_category_name UNIQUE (name),
    CONSTRAINT fk_category_user FOREIGN KEY (user_id) REFERENCES `user` (id)
);

ALTER TABLE article ADD COLUMN category_id BIGINT NULL;
ALTER TABLE article ADD COLUMN update_time DATETIME NULL;
UPDATE article SET update_time = create_time;
UPDATE article SET view_count = GREATEST(COALESCE(view_count, 0), 0);
-- Original project had no like relation; start the new auditable like count at zero.
UPDATE article SET like_count = 0;
ALTER TABLE article MODIFY view_count BIGINT NOT NULL DEFAULT 0,
    MODIFY like_count BIGINT NOT NULL DEFAULT 0,
    MODIFY content MEDIUMTEXT NOT NULL,
    MODIFY update_time DATETIME NOT NULL,
    ADD CONSTRAINT fk_article_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    ADD CONSTRAINT fk_article_category FOREIGN KEY (category_id) REFERENCES category (id),
    ADD CONSTRAINT ck_article_views CHECK (view_count >= 0),
    ADD CONSTRAINT ck_article_likes CHECK (like_count >= 0),
    ADD INDEX idx_article_created (create_time, id),
    ADD INDEX idx_article_author (user_id, create_time, id),
    ADD INDEX idx_article_category (category_id, create_time, id),
    ADD INDEX idx_article_views (view_count, create_time, id),
    ADD INDEX idx_article_likes (like_count, create_time, id);

ALTER TABLE comment
    ADD CONSTRAINT fk_comment_article FOREIGN KEY (article_id) REFERENCES article (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    ADD INDEX idx_comment_article_created (article_id, create_time, id);

CREATE TABLE article_like (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    article_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL,
    CONSTRAINT uk_like_article_user UNIQUE (article_id, user_id),
    CONSTRAINT fk_like_article FOREIGN KEY (article_id) REFERENCES article (id) ON DELETE CASCADE,
    CONSTRAINT fk_like_user FOREIGN KEY (user_id) REFERENCES `user` (id)
);
