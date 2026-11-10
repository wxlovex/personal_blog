-- MySQL 8: back up the database and apply v2/v3 first if needed.
-- Stop application writes during this upgrade. ALTER TABLE commits implicitly.
-- Existing diary entries, comments and social history are preserved.
ALTER TABLE category DROP INDEX uk_category_name,
    ADD CONSTRAINT uk_category_user_name UNIQUE (user_id, name);

START TRANSACTION;

-- Copy formerly shared classifications into each diary owner's private space.
INSERT INTO category (user_id, name, description, create_time)
SELECT DISTINCT a.user_id, c.name, c.description, c.create_time
FROM article a JOIN category c ON c.id = a.category_id
LEFT JOIN category own ON own.user_id = a.user_id AND own.name = c.name
WHERE c.user_id <> a.user_id
  AND own.id IS NULL;

UPDATE article a
SET category_id = (
    SELECT own.id FROM category own
    WHERE own.user_id = a.user_id
      AND own.name = (SELECT original.name FROM category original WHERE original.id = a.category_id)
)
WHERE EXISTS (SELECT 1 FROM category original WHERE original.id = a.category_id AND original.user_id <> a.user_id);

COMMIT;
