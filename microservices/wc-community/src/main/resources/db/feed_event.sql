CREATE TABLE IF NOT EXISTS feed_event (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  user_id BIGINT NULL COMMENT 'Logged-in user id; null for anonymous traffic',
  session_id VARCHAR(64) NULL COMMENT 'Anonymous local feed session id',
  post_id BIGINT NOT NULL COMMENT 'Post id',
  event_type VARCHAR(20) NOT NULL COMMENT 'Feed event type: IMPRESSION, CLICK, DWELL, DISLIKE',
  source VARCHAR(40) NULL COMMENT 'Event source: recommend, latest, following, search, or topic',
  dwell_ms BIGINT NULL COMMENT 'Dwell duration in milliseconds; only used for DWELL events',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Event creation time',
  PRIMARY KEY (id),
  KEY idx_feed_event_user_time (user_id, create_time),
  KEY idx_feed_event_session_time (session_id, create_time),
  KEY idx_feed_event_post_type (post_id, event_type),
  KEY idx_feed_event_type_time (event_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Feed recommendation behavior events';

ALTER TABLE feed_event
  MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  MODIFY user_id BIGINT NULL COMMENT 'Logged-in user id; null for anonymous traffic',
  MODIFY session_id VARCHAR(64) NULL COMMENT 'Anonymous local feed session id',
  MODIFY post_id BIGINT NOT NULL COMMENT 'Post id',
  MODIFY event_type VARCHAR(20) NOT NULL COMMENT 'Feed event type: IMPRESSION, CLICK, DWELL, DISLIKE',
  MODIFY source VARCHAR(40) NULL COMMENT 'Event source: recommend, latest, following, search, or topic',
  MODIFY dwell_ms BIGINT NULL COMMENT 'Dwell duration in milliseconds; only used for DWELL events',
  MODIFY create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Event creation time',
  COMMENT='Feed recommendation behavior events';
