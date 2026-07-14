CREATE TABLE IF NOT EXISTS coffee_topic (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  name VARCHAR(64) NOT NULL COMMENT 'Topic name without leading hash sign',
  description VARCHAR(255) NULL COMMENT 'Optional topic description',
  post_count INT NOT NULL DEFAULT 0 COMMENT 'Number of active posts attached to this topic',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'Topic status: 1 enabled, 0 disabled',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_coffee_topic_name (name),
  KEY idx_coffee_topic_status_count (status, post_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Community topics for post discovery';

CREATE TABLE IF NOT EXISTS coffee_post_topic (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  post_id BIGINT NOT NULL COMMENT 'Coffee post id',
  topic_id BIGINT NOT NULL COMMENT 'Coffee topic id',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_coffee_post_topic (post_id, topic_id),
  KEY idx_coffee_post_topic_topic (topic_id, post_id),
  KEY idx_coffee_post_topic_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Many-to-many relation between community posts and topics';

CREATE TABLE IF NOT EXISTS post_draft (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  user_id BIGINT NOT NULL COMMENT 'Draft owner user id',
  title VARCHAR(80) NULL COMMENT 'Draft post title',
  content TEXT NULL COMMENT 'Draft post content',
  images TEXT NULL COMMENT 'Draft image URL list encoded as JSON',
  coffee_name VARCHAR(100) NULL COMMENT 'Draft coffee name',
  coffee_brand VARCHAR(100) NULL COMMENT 'Draft coffee brand or shop name',
  location VARCHAR(100) NULL COMMENT 'Draft location text',
  topics TEXT NULL COMMENT 'Draft topic name list encoded as JSON',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_post_draft_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='One server-side post draft per user';

ALTER TABLE post_report
  MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  MODIFY post_id BIGINT NOT NULL COMMENT 'Reported post id',
  MODIFY reporter_id BIGINT NOT NULL COMMENT 'Reporter user id',
  MODIFY reason VARCHAR(500) NOT NULL COMMENT 'Report reason',
  MODIFY status TINYINT NOT NULL DEFAULT 0 COMMENT 'Report status: 0 pending, 1 ignored, 2 post removed',
  MODIFY create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  COMMENT='Community post reports for moderation review';

SET @schema_name = DATABASE();

SET @add_remark_sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE post_report ADD COLUMN remark VARCHAR(255) NULL COMMENT ''Moderator handling note'' AFTER status',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name
    AND TABLE_NAME = 'post_report'
    AND COLUMN_NAME = 'remark'
);
PREPARE add_remark_stmt FROM @add_remark_sql;
EXECUTE add_remark_stmt;
DEALLOCATE PREPARE add_remark_stmt;

SET @add_handle_time_sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE post_report ADD COLUMN handle_time DATETIME NULL COMMENT ''Moderation handling time'' AFTER remark',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name
    AND TABLE_NAME = 'post_report'
    AND COLUMN_NAME = 'handle_time'
);
PREPARE add_handle_time_stmt FROM @add_handle_time_sql;
EXECUTE add_handle_time_stmt;
DEALLOCATE PREPARE add_handle_time_stmt;
