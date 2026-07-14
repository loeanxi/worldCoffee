CREATE TABLE IF NOT EXISTS sensitive_word (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '敏感词主键ID',
  word VARCHAR(80) NOT NULL COMMENT '敏感词内容',
  category VARCHAR(40) NOT NULL DEFAULT 'general' COMMENT '敏感词分类，如general、spam、illegal',
  action TINYINT NOT NULL DEFAULT 1 COMMENT '命中动作：1进入审核，2直接拒绝',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sensitive_word_word (word),
  KEY idx_sensitive_word_status (status, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容治理敏感词表';

CREATE TABLE IF NOT EXISTS admin_operation_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '操作日志主键ID',
  admin_id BIGINT NULL COMMENT '管理员ID，配置管理员默认为0',
  admin_name VARCHAR(60) NOT NULL COMMENT '管理员名称',
  module VARCHAR(40) NOT NULL COMMENT '操作模块',
  action VARCHAR(60) NOT NULL COMMENT '操作动作',
  target_type VARCHAR(60) NULL COMMENT '目标对象类型',
  target_id BIGINT NULL COMMENT '目标对象ID',
  detail VARCHAR(500) NULL COMMENT '操作详情',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_admin_operation_module_time (module, create_time),
  KEY idx_admin_operation_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理后台操作日志表';
