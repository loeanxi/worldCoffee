-- ============================================================
-- WorldCoffee 微服务功能完善 DDL
-- 包含：商品评价、退款/退货、积分/会员等级
-- ============================================================

-- 1. 商品评价表
CREATE TABLE IF NOT EXISTS product_review (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '评价主键',
  order_id BIGINT NOT NULL COMMENT '订单ID',
  order_item_id BIGINT NOT NULL COMMENT '订单项ID（一个订单项只能评价一次）',
  product_id BIGINT NOT NULL COMMENT '商品ID',
  user_id BIGINT NOT NULL COMMENT '评价用户ID',
  rating INT NOT NULL COMMENT '星级 1-5',
  content TEXT NULL COMMENT '评价文字',
  images VARCHAR(1000) NULL COMMENT '评价图片JSON数组',
  is_anonymous TINYINT NOT NULL DEFAULT 0 COMMENT '是否匿名 0否1是',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1正常 0隐藏',
  admin_reply VARCHAR(500) NULL COMMENT '商家回复',
  admin_reply_time DATETIME NULL COMMENT '回复时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_review_order_item (order_item_id),
  KEY idx_product_review_product (product_id, status, create_time),
  KEY idx_product_review_user (user_id, create_time),
  KEY idx_product_review_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品评价表';

-- 2. 退款记录表
CREATE TABLE IF NOT EXISTS refund_record (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '退款主键',
  refund_no VARCHAR(64) NOT NULL COMMENT '退款单号',
  order_no VARCHAR(64) NOT NULL COMMENT '关联订单号',
  user_id BIGINT NOT NULL COMMENT '申请人ID',
  type TINYINT NOT NULL COMMENT '退款类型 1仅退款 2退货退款',
  reason VARCHAR(500) NOT NULL COMMENT '退款原因',
  amount DECIMAL(10,2) NOT NULL COMMENT '退款金额',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0申请中 1审核中 2退款成功 3退款拒绝 4已取消',
  admin_remark VARCHAR(500) NULL COMMENT '审核备注',
  handle_time DATETIME NULL COMMENT '处理时间',
  tracking_no VARCHAR(64) NULL COMMENT '退货物流单号',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_refund_record_refund_no (refund_no),
  KEY idx_refund_record_order (order_no),
  KEY idx_refund_record_user (user_id, status, create_time),
  KEY idx_refund_record_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款记录表';

-- 3. 订单表新增退款状态字段
ALTER TABLE coffee_order ADD COLUMN refund_status INT NOT NULL DEFAULT 0 COMMENT '退款状态 0无 1退款中 2已退款' AFTER status;

-- 4. 积分流水表
CREATE TABLE IF NOT EXISTS point_record (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '积分流水主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  type TINYINT NOT NULL COMMENT '变动类型 1消费获得 2评价获得 3兑换消耗 4退款扣除 5管理员调整',
  change_amount INT NOT NULL COMMENT '变动数量（正数增加负数减少）',
  balance_after INT NOT NULL COMMENT '变动后余额',
  source_id BIGINT NULL COMMENT '来源ID（订单ID/评价ID等）',
  source_type VARCHAR(30) NULL COMMENT '来源类型 ORDER/REVIEW/EXCHANGE',
  remark VARCHAR(100) NULL COMMENT '备注',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_point_record_user (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分流水表';

-- 5. 积分兑换规则表
CREATE TABLE IF NOT EXISTS point_exchange_rule (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '兑换规则主键',
  name VARCHAR(50) NOT NULL COMMENT '规则名称',
  type TINYINT NOT NULL COMMENT '类型 1优惠券',
  required_points INT NOT NULL COMMENT '所需积分',
  coupon_id BIGINT NULL COMMENT '关联优惠券ID',
  stock INT NOT NULL DEFAULT -1 COMMENT '库存 -1无限',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1启用 0停用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_point_exchange_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分兑换规则表';

-- 6. sys_user 表新增积分和等级字段
ALTER TABLE sys_user ADD COLUMN points INT NOT NULL DEFAULT 0 COMMENT '可用积分' AFTER avatar;
ALTER TABLE sys_user ADD COLUMN total_points INT NOT NULL DEFAULT 0 COMMENT '累计积分' AFTER points;
ALTER TABLE sys_user ADD COLUMN member_level TINYINT NOT NULL DEFAULT 1 COMMENT '会员等级 1普通 2白银 3黄金 4铂金 5钻石' AFTER total_points;
