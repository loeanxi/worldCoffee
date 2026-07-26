-- World Coffee community phase 2 schema.
-- 说明：本脚本补齐收藏夹、不感兴趣、楼中楼评论、视频笔记、笔记关联商品和创作者数据所需字段。

SET @schema_name = DATABASE();

CREATE TABLE IF NOT EXISTS favorite_collection (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '收藏夹主键ID',
  user_id BIGINT NOT NULL COMMENT '收藏夹所属用户ID',
  name VARCHAR(40) NOT NULL COMMENT '收藏夹名称',
  description VARCHAR(120) NULL COMMENT '收藏夹描述',
  item_count INT NOT NULL DEFAULT 0 COMMENT '收藏夹内有效帖子数量',
  is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认收藏夹：1是，0否',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '收藏夹状态：1正常，0删除',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_favorite_collection_user (user_id, status, update_time),
  UNIQUE KEY uk_favorite_collection_default (user_id, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏夹表';

CREATE TABLE IF NOT EXISTS favorite_collection_item (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '收藏夹内容主键ID',
  collection_id BIGINT NOT NULL COMMENT '收藏夹ID',
  user_id BIGINT NOT NULL COMMENT '收藏用户ID，冗余用于快速校验归属',
  post_id BIGINT NOT NULL COMMENT '被收藏的帖子ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏到该收藏夹的时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_favorite_collection_item (collection_id, post_id),
  KEY idx_favorite_collection_item_user (user_id, create_time),
  KEY idx_favorite_collection_item_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏夹帖子明细表';

CREATE TABLE IF NOT EXISTS post_negative_feedback (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '负反馈主键ID',
  user_id BIGINT NULL COMMENT '登录用户ID，匿名访问时为空',
  session_id VARCHAR(64) NULL COMMENT '匿名访问会话ID',
  post_id BIGINT NOT NULL COMMENT '被标记不感兴趣的帖子ID',
  reason_type VARCHAR(30) NOT NULL DEFAULT 'OTHER' COMMENT '原因类型：重复、低质、不喜欢作者等',
  reason VARCHAR(120) NULL COMMENT '用户补充原因',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_post_negative_user_time (user_id, create_time),
  KEY idx_post_negative_session_time (session_id, create_time),
  KEY idx_post_negative_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子不感兴趣负反馈表';

CREATE TABLE IF NOT EXISTS coffee_post_product (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '笔记商品关系主键ID',
  post_id BIGINT NOT NULL COMMENT '社区帖子ID',
  product_id BIGINT NOT NULL COMMENT '商城商品ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关联创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_coffee_post_product (post_id, product_id),
  KEY idx_coffee_post_product_product (product_id, post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记关联商品关系表';

SET @add_post_note_type_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE coffee_post ADD COLUMN note_type VARCHAR(12) NOT NULL DEFAULT ''IMAGE'' COMMENT ''笔记类型：IMAGE图文，VIDEO视频'' AFTER images',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'coffee_post' AND COLUMN_NAME = 'note_type'
);
PREPARE add_post_note_type_stmt FROM @add_post_note_type_sql;
EXECUTE add_post_note_type_stmt;
DEALLOCATE PREPARE add_post_note_type_stmt;

SET @add_post_video_url_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE coffee_post ADD COLUMN video_url VARCHAR(500) NULL COMMENT ''视频笔记播放地址'' AFTER note_type',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'coffee_post' AND COLUMN_NAME = 'video_url'
);
PREPARE add_post_video_url_stmt FROM @add_post_video_url_sql;
EXECUTE add_post_video_url_stmt;
DEALLOCATE PREPARE add_post_video_url_stmt;

SET @add_post_cover_url_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE coffee_post ADD COLUMN cover_url VARCHAR(500) NULL COMMENT ''视频或图文封面地址'' AFTER video_url',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'coffee_post' AND COLUMN_NAME = 'cover_url'
);
PREPARE add_post_cover_url_stmt FROM @add_post_cover_url_sql;
EXECUTE add_post_cover_url_stmt;
DEALLOCATE PREPARE add_post_cover_url_stmt;

SET @add_post_video_duration_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE coffee_post ADD COLUMN video_duration INT NULL COMMENT ''视频时长，单位秒'' AFTER cover_url',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'coffee_post' AND COLUMN_NAME = 'video_duration'
);
PREPARE add_post_video_duration_stmt FROM @add_post_video_duration_sql;
EXECUTE add_post_video_duration_stmt;
DEALLOCATE PREPARE add_post_video_duration_stmt;

SET @add_comment_parent_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE coffee_comment ADD COLUMN parent_id BIGINT NULL COMMENT ''父评论ID，顶层评论为空'' AFTER post_id',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'coffee_comment' AND COLUMN_NAME = 'parent_id'
);
PREPARE add_comment_parent_stmt FROM @add_comment_parent_sql;
EXECUTE add_comment_parent_stmt;
DEALLOCATE PREPARE add_comment_parent_stmt;

SET @add_comment_root_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE coffee_comment ADD COLUMN root_id BIGINT NULL COMMENT ''根评论ID，用于楼中楼聚合'' AFTER parent_id',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'coffee_comment' AND COLUMN_NAME = 'root_id'
);
PREPARE add_comment_root_stmt FROM @add_comment_root_sql;
EXECUTE add_comment_root_stmt;
DEALLOCATE PREPARE add_comment_root_stmt;

SET @add_comment_reply_to_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE coffee_comment ADD COLUMN reply_to_user_id BIGINT NULL COMMENT ''回复目标用户ID，用于@提醒和前端展示'' AFTER root_id',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'coffee_comment' AND COLUMN_NAME = 'reply_to_user_id'
);
PREPARE add_comment_reply_to_stmt FROM @add_comment_reply_to_sql;
EXECUTE add_comment_reply_to_stmt;
DEALLOCATE PREPARE add_comment_reply_to_stmt;

SET @add_draft_note_type_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE post_draft ADD COLUMN note_type VARCHAR(12) NULL COMMENT ''草稿笔记类型：IMAGE图文，VIDEO视频'' AFTER images',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'post_draft' AND COLUMN_NAME = 'note_type'
);
PREPARE add_draft_note_type_stmt FROM @add_draft_note_type_sql;
EXECUTE add_draft_note_type_stmt;
DEALLOCATE PREPARE add_draft_note_type_stmt;

SET @add_draft_video_url_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE post_draft ADD COLUMN video_url VARCHAR(500) NULL COMMENT ''草稿视频地址'' AFTER note_type',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'post_draft' AND COLUMN_NAME = 'video_url'
);
PREPARE add_draft_video_url_stmt FROM @add_draft_video_url_sql;
EXECUTE add_draft_video_url_stmt;
DEALLOCATE PREPARE add_draft_video_url_stmt;

SET @add_draft_cover_url_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE post_draft ADD COLUMN cover_url VARCHAR(500) NULL COMMENT ''草稿封面地址'' AFTER video_url',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'post_draft' AND COLUMN_NAME = 'cover_url'
);
PREPARE add_draft_cover_url_stmt FROM @add_draft_cover_url_sql;
EXECUTE add_draft_cover_url_stmt;
DEALLOCATE PREPARE add_draft_cover_url_stmt;

SET @add_draft_video_duration_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE post_draft ADD COLUMN video_duration INT NULL COMMENT ''草稿视频时长，单位秒'' AFTER cover_url',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'post_draft' AND COLUMN_NAME = 'video_duration'
);
PREPARE add_draft_video_duration_stmt FROM @add_draft_video_duration_sql;
EXECUTE add_draft_video_duration_stmt;
DEALLOCATE PREPARE add_draft_video_duration_stmt;

SET @add_draft_product_ids_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE post_draft ADD COLUMN product_ids TEXT NULL COMMENT ''草稿关联商品ID列表，JSON数组'' AFTER topics',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'post_draft' AND COLUMN_NAME = 'product_ids'
);
PREPARE add_draft_product_ids_stmt FROM @add_draft_product_ids_sql;
EXECUTE add_draft_product_ids_stmt;
DEALLOCATE PREPARE add_draft_product_ids_stmt;

SET @add_comment_parent_idx_sql = (
  SELECT IF(COUNT(*) = 0,
    'CREATE INDEX idx_coffee_comment_parent ON coffee_comment (post_id, parent_id, create_time)',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'coffee_comment' AND INDEX_NAME = 'idx_coffee_comment_parent'
);
PREPARE add_comment_parent_idx_stmt FROM @add_comment_parent_idx_sql;
EXECUTE add_comment_parent_idx_stmt;
DEALLOCATE PREPARE add_comment_parent_idx_stmt;

SET @add_comment_root_idx_sql = (
  SELECT IF(COUNT(*) = 0,
    'CREATE INDEX idx_coffee_comment_root ON coffee_comment (root_id, create_time)',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'coffee_comment' AND INDEX_NAME = 'idx_coffee_comment_root'
);
PREPARE add_comment_root_idx_stmt FROM @add_comment_root_idx_sql;
EXECUTE add_comment_root_idx_stmt;
DEALLOCATE PREPARE add_comment_root_idx_stmt;
