-- Additive and repeatable. Existing email values remain UNVERIFIED and are not copied here.
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
