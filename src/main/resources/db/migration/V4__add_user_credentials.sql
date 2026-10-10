ALTER TABLE app_users
    ADD COLUMN password_hash VARCHAR(100) NOT NULL DEFAULT '!';

ALTER TABLE app_users
    ALTER COLUMN password_hash DROP DEFAULT;
