-- ============================================================================
-- LinkedIn system - schema for every table, created automatically on first start
-- of the MySQL primary (mounted into /docker-entrypoint-initdb.d).
-- Two logical databases (database-per-service): user_db and post_db.
-- Column names/types match the JPA entities (Spring physical naming: camelCase -> snake_case).
-- The services run with spring.jpa.hibernate.ddl-auto=none in docker, so THIS file is the schema.
-- ============================================================================
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS user_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS post_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- user_db ----
USE user_db;

CREATE TABLE IF NOT EXISTS users (
    id                 VARCHAR(36)  NOT NULL,
    email              VARCHAR(255) NOT NULL,
    password           VARCHAR(255) NOT NULL,            -- bcrypt hash, never plain text
    first_name         VARCHAR(255) NOT NULL,
    last_name          VARCHAR(255) NOT NULL,
    headline           VARCHAR(255) NULL,
    about              TEXT         NULL,
    location           VARCHAR(255) NULL,
    profile_photo_url  VARCHAR(255) NULL,
    cover_photo_url    VARCHAR(255) NULL,
    role               VARCHAR(20)  NULL,                -- NORMAL_USER | ADMIN | RECRUITER
    created_at         DATETIME(6)  NULL,
    updated_at         DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_role (role)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_skills (
    user_id  VARCHAR(36)  NOT NULL,
    skill    VARCHAR(255) NULL,
    KEY idx_user_skills_user (user_id),
    KEY idx_user_skills_skill (skill),
    CONSTRAINT fk_user_skills_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS connections (
    id            VARCHAR(36) NOT NULL,
    requester_id  VARCHAR(255) NOT NULL,
    receiver_id   VARCHAR(255) NOT NULL,
    status        VARCHAR(20) NOT NULL,                  -- PENDING | CONNECTED | REJECTED
    created_at    DATETIME(6) NULL,
    updated_at    DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_connections_requester (requester_id, status),
    KEY idx_connections_receiver  (receiver_id, status)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------- post_db ----
USE post_db;

CREATE TABLE IF NOT EXISTS posts (
    id             VARCHAR(36)  NOT NULL,
    author_id      VARCHAR(255) NOT NULL,
    content        TEXT         NULL,
    image_url      VARCHAR(255) NULL,
    like_count     INT          NOT NULL DEFAULT 0,
    comment_count  INT          NOT NULL DEFAULT 0,
    created_at     DATETIME(6)  NULL,
    updated_at     DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_posts_author_created (author_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS likes (
    id          VARCHAR(36)  NOT NULL,
    post_id     VARCHAR(255) NOT NULL,
    user_id     VARCHAR(255) NOT NULL,
    created_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_likes_post_user (post_id, user_id),
    KEY idx_likes_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS comments (
    id          VARCHAR(36)  NOT NULL,
    post_id     VARCHAR(255) NOT NULL,
    author_id   VARCHAR(255) NOT NULL,
    content     TEXT         NOT NULL,
    created_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_comments_post_created (post_id, created_at)
) ENGINE=InnoDB;
