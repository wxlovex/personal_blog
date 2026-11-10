-- Run once before starting the security update. Additive; journal data is unchanged.
-- Previous JWTs have no registered session and will require login again.
CREATE TABLE IF NOT EXISTS auth_session (
    session_id CHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE,
    INDEX idx_session_user_expiry (user_id, expires_at)
);
