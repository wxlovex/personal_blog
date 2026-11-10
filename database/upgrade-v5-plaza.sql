-- Apply once to a v4 database, after backing up and stopping application writes.
-- Every existing article stays private. This migration publishes no content.
ALTER TABLE article ADD COLUMN published BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE article ADD COLUMN plaza_category VARCHAR(20);
ALTER TABLE article ADD COLUMN published_time DATETIME;
ALTER TABLE article ADD CONSTRAINT ck_article_publication CHECK (
    (published = FALSE AND plaza_category IS NULL AND published_time IS NULL) OR
    (published = TRUE AND plaza_category IS NOT NULL AND plaza_category IN ('技术','日常','读书','娱乐') AND published_time IS NOT NULL)
);
CREATE INDEX idx_article_public_time ON article (published, published_time, id);
CREATE INDEX idx_article_plaza ON article (published, plaza_category, published_time, id);
