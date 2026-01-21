-- 创建数据库表结构

-- 用户表
CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    phone VARCHAR(20),
    avatar VARCHAR(200),
    role VARCHAR(20) NOT NULL DEFAULT 'user',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 分类表
CREATE TABLE IF NOT EXISTS category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 标签表
CREATE TABLE IF NOT EXISTS tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 文章表
CREATE TABLE IF NOT EXISTS article (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    html_content LONGTEXT,
    user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    comment_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id),
    FOREIGN KEY (category_id) REFERENCES category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 文章标签关联表
CREATE TABLE IF NOT EXISTS article_tag (
    article_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (article_id, tag_id),
    FOREIGN KEY (article_id) REFERENCES article(id),
    FOREIGN KEY (tag_id) REFERENCES tag(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 工具表
CREATE TABLE IF NOT EXISTS tool (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    url VARCHAR(200) NOT NULL,
    user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    comment_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id),
    FOREIGN KEY (category_id) REFERENCES category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 工具标签关联表
CREATE TABLE IF NOT EXISTS tool_tag (
    tool_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (tool_id, tag_id),
    FOREIGN KEY (tool_id) REFERENCES tool(id),
    FOREIGN KEY (tag_id) REFERENCES tag(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 评论表
CREATE TABLE IF NOT EXISTS comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content TEXT NOT NULL,
    user_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    parent_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id),
    FOREIGN KEY (parent_id) REFERENCES comment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 插入初始数据

-- 插入管理员用户（密码：admin123）
INSERT INTO user (username, password, email, role, avatar) VALUES ('admin', '$2a$10$1qAz2wSx3eDc4rFv5tGb5eDc4rFv5tGb5eDc4rFv5tGb5eDc4rFv5tGb', 'admin@example.com', 'admin', 'admin-avatar.png') ON DUPLICATE KEY UPDATE username=username;

-- 插入测试用户（密码：test123）
INSERT INTO user (username, password, email, role, avatar) VALUES ('test', '$2a$10$1qAz2wSx3eDc4rFv5tGb5eDc4rFv5tGb5eDc4rFv5tGb5eDc4rFv5tGb', 'test@example.com', 'user', 'test-avatar.png') ON DUPLICATE KEY UPDATE username=username;

-- 插入文章分类
INSERT INTO category (name, type) VALUES ('技术分享', 'article') ON DUPLICATE KEY UPDATE name=name;
INSERT INTO category (name, type) VALUES ('生活随笔', 'article') ON DUPLICATE KEY UPDATE name=name;
INSERT INTO category (name, type) VALUES ('学习笔记', 'article') ON DUPLICATE KEY UPDATE name=name;
INSERT INTO category (name, type) VALUES ('其他', 'article') ON DUPLICATE KEY UPDATE name=name;

-- 插入工具分类
INSERT INTO category (name, type) VALUES ('开发工具', 'tool') ON DUPLICATE KEY UPDATE name=name;
INSERT INTO category (name, type) VALUES ('设计工具', 'tool') ON DUPLICATE KEY UPDATE name=name;
INSERT INTO category (name, type) VALUES ('在线服务', 'tool') ON DUPLICATE KEY UPDATE name=name;
INSERT INTO category (name, type) VALUES ('其他工具', 'tool') ON DUPLICATE KEY UPDATE name=name;

-- 插入标签
INSERT INTO tag (name) VALUES ('Vue') ON DUPLICATE KEY UPDATE name=name;
INSERT INTO tag (name) VALUES ('React') ON DUPLICATE KEY UPDATE name=name;
INSERT INTO tag (name) VALUES ('Spring Boot') ON DUPLICATE KEY UPDATE name=name;
INSERT INTO tag (name) VALUES ('JavaScript') ON DUPLICATE KEY UPDATE name=name;
INSERT INTO tag (name) VALUES ('TypeScript') ON DUPLICATE KEY UPDATE name=name;
INSERT INTO tag (name) VALUES ('Markdown') ON DUPLICATE KEY UPDATE name=name;
INSERT INTO tag (name) VALUES ('开发工具') ON DUPLICATE KEY UPDATE name=name;
INSERT INTO tag (name) VALUES ('设计工具') ON DUPLICATE KEY UPDATE name=name;
