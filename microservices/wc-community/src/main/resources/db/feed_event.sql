CREATE TABLE IF NOT EXISTS feed_event (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NULL,
  session_id VARCHAR(64) NULL,
  post_id BIGINT NOT NULL,
  event_type VARCHAR(20) NOT NULL,
  source VARCHAR(40) NULL,
  dwell_ms BIGINT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_feed_event_user_time (user_id, create_time),
  KEY idx_feed_event_session_time (session_id, create_time),
  KEY idx_feed_event_post_type (post_id, event_type),
  KEY idx_feed_event_type_time (event_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
