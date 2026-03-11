-- communitySport.sql
-- Target DB: MySQL 8.0.37
-- Charset: utf8mb4

CREATE DATABASE IF NOT EXISTS `communitySport` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `communitySport`;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `sys_user_role`;
DROP TABLE IF EXISTS `auth_refresh_token`;
DROP TABLE IF EXISTS `sys_user`;
DROP TABLE IF EXISTS `sys_role`;
DROP TABLE IF EXISTS `sys_config`;

DROP TABLE IF EXISTS `user_profile`;
DROP TABLE IF EXISTS `coach_profile`;
DROP TABLE IF EXISTS `coach_application`;
DROP TABLE IF EXISTS `staff_profile`;
DROP TABLE IF EXISTS `user_address`;

DROP TABLE IF EXISTS `wallet_transaction`;
DROP TABLE IF EXISTS `wallet_topup_request`;
DROP TABLE IF EXISTS `wallet_account`;
DROP TABLE IF EXISTS `user_signin_log`;

DROP TABLE IF EXISTS `venue_verification_log`;
DROP TABLE IF EXISTS `venue_booking`;
DROP TABLE IF EXISTS `venue_timeslot`;
DROP TABLE IF EXISTS `venue`;
DROP TABLE IF EXISTS `venue_type`;

DROP TABLE IF EXISTS `equipment_review`;
DROP TABLE IF EXISTS `equipment_order_item`;
DROP TABLE IF EXISTS `equipment_order`;
DROP TABLE IF EXISTS `equipment_cart_item`;
DROP TABLE IF EXISTS `equipment`;
DROP TABLE IF EXISTS `equipment_category`;

DROP TABLE IF EXISTS `course_review`;
DROP TABLE IF EXISTS `coach_course_booking`;
DROP TABLE IF EXISTS `coach_course_session`;
DROP TABLE IF EXISTS `coach_course`;

DROP TABLE IF EXISTS `coach_video_purchase`;
DROP TABLE IF EXISTS `coach_video`;

DROP TABLE IF EXISTS `complaint_message`;
DROP TABLE IF EXISTS `complaint`;

DROP TABLE IF EXISTS `favorite`;
DROP TABLE IF EXISTS `home_banner`;
DROP TABLE IF EXISTS `notice`;

SET FOREIGN_KEY_CHECKS = 1;

-- =========================
-- 1) Auth & RBAC
-- =========================

CREATE TABLE `sys_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` VARCHAR(50) NOT NULL COMMENT '角色编码(ADMIN/STAFF/USER/COACH)',
  `name` VARCHAR(100) NOT NULL COMMENT '角色名称',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名(登录账号)',
  `password_hash` VARCHAR(100) NOT NULL COMMENT '密码(BCrypt)',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
  `avatar_url` VARCHAR(255) DEFAULT NULL COMMENT '头像',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(1启用,0禁用)',
  `last_login_at` DATETIME DEFAULT NULL COMMENT '最后登录时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_username` (`username`),
  UNIQUE KEY `uk_sys_user_phone` (`phone`),
  UNIQUE KEY `uk_sys_user_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表(统一账号体系)';

CREATE TABLE `sys_user_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_role` (`user_id`, `role_id`),
  KEY `idx_sys_user_role_role_id` (`role_id`),
  CONSTRAINT `fk_sys_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_sys_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联表';

CREATE TABLE `auth_refresh_token` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `token_hash` VARCHAR(255) NOT NULL COMMENT 'RefreshToken哈希(建议SHA-256后存储)',
  `device_id` VARCHAR(100) DEFAULT NULL COMMENT '设备ID(可选)',
  `expires_at` DATETIME NOT NULL COMMENT '过期时间',
  `revoked` TINYINT NOT NULL DEFAULT 0 COMMENT '是否吊销(1是,0否)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `last_used_at` DATETIME DEFAULT NULL COMMENT '最后使用时间',
  PRIMARY KEY (`id`),
  KEY `idx_auth_refresh_token_user_id` (`user_id`),
  KEY `idx_auth_refresh_token_expires_at` (`expires_at`),
  CONSTRAINT `fk_auth_refresh_token_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Refresh Token表(双Token模式)';

CREATE TABLE `sys_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `cfg_key` VARCHAR(100) NOT NULL COMMENT '配置键',
  `cfg_value` VARCHAR(255) NOT NULL COMMENT '配置值',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_config_key` (`cfg_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- =========================
-- 2) Profiles & Address
-- =========================

CREATE TABLE `user_profile` (
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
  `gender` VARCHAR(10) DEFAULT NULL COMMENT '性别',
  `birthday` DATE DEFAULT NULL COMMENT '生日',
  `contact_phone` VARCHAR(20) DEFAULT NULL COMMENT '联系方式',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_user_profile_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='普通用户资料';

CREATE TABLE `coach_application` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '申请人用户ID',
  `specialty` VARCHAR(100) DEFAULT NULL COMMENT '擅长领域',
  `intro` TEXT COMMENT '自我介绍',
  `cert_files` JSON DEFAULT NULL COMMENT '资质证明(图片/文件URL数组)',
  `audit_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态(PENDING/APPROVED/REJECTED)',
  `audit_remark` VARCHAR(255) DEFAULT NULL COMMENT '审核意见/驳回原因',
  `audited_by` BIGINT DEFAULT NULL COMMENT '审核管理员ID',
  `audited_at` DATETIME DEFAULT NULL COMMENT '审核时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coach_application_user_id` (`user_id`),
  KEY `idx_coach_application_status` (`audit_status`),
  CONSTRAINT `fk_coach_application_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教练认证申请表(用户申请->管理员审核->成为教练)';

CREATE TABLE `coach_profile` (
  `user_id` BIGINT NOT NULL COMMENT '教练用户ID',
  `rating_avg` DECIMAL(3,2) NOT NULL DEFAULT 0.00 COMMENT '平均评分',
  `rating_count` INT NOT NULL DEFAULT 0 COMMENT '评分次数',
  `service_status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '服务状态(ACTIVE/SUSPENDED)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_coach_profile_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教练资料表(审核通过后生成/启用)';

CREATE TABLE `staff_profile` (
  `user_id` BIGINT NOT NULL COMMENT '员工用户ID',
  `real_name` VARCHAR(50) DEFAULT NULL COMMENT '姓名',
  `department` VARCHAR(50) DEFAULT NULL COMMENT '部门',
  `position` VARCHAR(50) DEFAULT NULL COMMENT '职位',
  `region` VARCHAR(100) DEFAULT NULL COMMENT '负责区域',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_staff_profile_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工资料表';

CREATE TABLE `user_address` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `receiver_name` VARCHAR(50) NOT NULL COMMENT '收货人',
  `receiver_phone` VARCHAR(20) NOT NULL COMMENT '收货手机号',
  `province` VARCHAR(50) DEFAULT NULL COMMENT '省',
  `city` VARCHAR(50) DEFAULT NULL COMMENT '市',
  `district` VARCHAR(50) DEFAULT NULL COMMENT '区',
  `detail` VARCHAR(255) NOT NULL COMMENT '详细地址',
  `is_default` TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认(1是,0否)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_address_user_id` (`user_id`),
  CONSTRAINT `fk_user_address_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址表';

-- =========================
-- 3) Wallet & Sign-in
-- =========================

CREATE TABLE `wallet_account` (
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `balance` INT NOT NULL DEFAULT 0 COMMENT '钱包余额(整数金额)',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_wallet_account_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包账户表';

CREATE TABLE `wallet_topup_request` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `request_no` VARCHAR(64) NOT NULL COMMENT '申请单号',
  `user_id` BIGINT NOT NULL COMMENT '申请人用户ID(用户/教练/员工)',
  `amount` INT NOT NULL COMMENT '申请金额(1-9999)',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态(PENDING/APPROVED/REJECTED)',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '申请说明/管理员备注',
  `requested_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `processed_by` BIGINT DEFAULT NULL COMMENT '处理管理员ID',
  `processed_at` DATETIME DEFAULT NULL COMMENT '处理时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wallet_topup_request_no` (`request_no`),
  KEY `idx_wallet_topup_user_id` (`user_id`),
  KEY `idx_wallet_topup_status` (`status`),
  CONSTRAINT `fk_wallet_topup_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包充值申请(管理员审核发放)';

CREATE TABLE `wallet_transaction` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `txn_no` VARCHAR(64) NOT NULL COMMENT '流水号',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `txn_type` VARCHAR(50) NOT NULL COMMENT '类型(TOPUP/SIGNIN/BOOKING_PAY/REFUND/ADJUST等)',
  `direction` VARCHAR(10) NOT NULL COMMENT '方向(IN/OUT)',
  `amount` INT NOT NULL COMMENT '金额(整数金额)',
  `ref_type` VARCHAR(50) DEFAULT NULL COMMENT '关联业务类型',
  `ref_id` BIGINT DEFAULT NULL COMMENT '关联业务ID',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wallet_txn_no` (`txn_no`),
  KEY `idx_wallet_txn_user_id` (`user_id`),
  KEY `idx_wallet_txn_created_at` (`created_at`),
  CONSTRAINT `fk_wallet_txn_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包流水表';

CREATE TABLE `user_signin_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `signin_date` DATE NOT NULL COMMENT '签到日期',
  `daily_reward` INT NOT NULL COMMENT '每日签到奖励(默认100)',
  `streak_bonus` INT NOT NULL DEFAULT 0 COMMENT '连续签到奖励(如连续7天额外300)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_signin_user_date` (`user_id`, `signin_date`),
  KEY `idx_user_signin_date` (`signin_date`),
  CONSTRAINT `fk_user_signin_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签到日志(用于审计/统计，Redis负责实时连续签到计算)';

-- =========================
-- 4) Venues
-- =========================

CREATE TABLE `venue_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` VARCHAR(100) NOT NULL COMMENT '类型名称',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_venue_type_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场地类型';

CREATE TABLE `venue` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type_id` BIGINT NOT NULL COMMENT '场地类型ID',
  `name` VARCHAR(100) NOT NULL COMMENT '场地名称',
  `area` VARCHAR(100) DEFAULT NULL COMMENT '场地区域',
  `address` VARCHAR(255) DEFAULT NULL COMMENT '场地地址',
  `spec` VARCHAR(255) DEFAULT NULL COMMENT '规格/尺寸',
  `open_time_desc` VARCHAR(255) DEFAULT NULL COMMENT '开放时间描述',
  `price_per_hour` INT NOT NULL DEFAULT 0 COMMENT '时价(整数金额)',
  `contact_phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
  `cover_url` VARCHAR(255) DEFAULT NULL COMMENT '封面图',
  `description` TEXT COMMENT '场地详情',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/MAINTENANCE/DISABLED)',
  `click_count` INT NOT NULL DEFAULT 0 COMMENT '点击次数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_venue_type_id` (`type_id`),
  KEY `idx_venue_status` (`status`),
  CONSTRAINT `fk_venue_type` FOREIGN KEY (`type_id`) REFERENCES `venue_type` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场地信息';

CREATE TABLE `venue_timeslot` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `venue_id` BIGINT NOT NULL COMMENT '场地ID',
  `start_time` DATETIME NOT NULL COMMENT '开始时间',
  `end_time` DATETIME NOT NULL COMMENT '结束时间',
  `price` INT NOT NULL COMMENT '该时段价格(整数金额)',
  `status` VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT '状态(AVAILABLE/BOOKED/BLOCKED)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_venue_timeslot` (`venue_id`, `start_time`, `end_time`),
  KEY `idx_venue_timeslot_status` (`status`),
  CONSTRAINT `fk_venue_timeslot_venue` FOREIGN KEY (`venue_id`) REFERENCES `venue` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场地可预约时段';

CREATE TABLE `venue_booking` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `booking_no` VARCHAR(64) NOT NULL COMMENT '预约单号',
  `user_id` BIGINT NOT NULL COMMENT '预约用户ID',
  `venue_id` BIGINT NOT NULL COMMENT '场地ID',
  `timeslot_id` BIGINT NOT NULL COMMENT '预约时段ID',
  `amount` INT NOT NULL COMMENT '应付金额(整数金额)',
  `status` VARCHAR(20) NOT NULL DEFAULT 'CREATED' COMMENT '状态(CREATED/PAID/CANCELED/REFUNDED/USED)',
  `verification_code` VARCHAR(32) NOT NULL COMMENT '核销码',
  `paid_at` DATETIME DEFAULT NULL COMMENT '支付时间(钱包扣款)',
  `canceled_at` DATETIME DEFAULT NULL COMMENT '取消时间',
  `used_at` DATETIME DEFAULT NULL COMMENT '核销时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_venue_booking_no` (`booking_no`),
  UNIQUE KEY `uk_venue_booking_timeslot` (`timeslot_id`),
  KEY `idx_venue_booking_user_id` (`user_id`),
  KEY `idx_venue_booking_status` (`status`),
  CONSTRAINT `fk_venue_booking_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_venue_booking_venue` FOREIGN KEY (`venue_id`) REFERENCES `venue` (`id`),
  CONSTRAINT `fk_venue_booking_timeslot` FOREIGN KEY (`timeslot_id`) REFERENCES `venue_timeslot` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场地预约单';

CREATE TABLE `venue_verification_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `booking_id` BIGINT NOT NULL COMMENT '预约单ID',
  `staff_user_id` BIGINT NOT NULL COMMENT '核销员工ID',
  `verified_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '核销时间',
  `result` VARCHAR(20) NOT NULL COMMENT '结果(SUCCESS/FAILED)',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_venue_verify_booking_id` (`booking_id`),
  CONSTRAINT `fk_venue_verify_booking` FOREIGN KEY (`booking_id`) REFERENCES `venue_booking` (`id`),
  CONSTRAINT `fk_venue_verify_staff` FOREIGN KEY (`staff_user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场地核销记录';

-- =========================
-- 5) Equipment Shop
-- =========================

CREATE TABLE `equipment_category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` VARCHAR(100) NOT NULL COMMENT '分类名称',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_equipment_category_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='器材分类';

CREATE TABLE `equipment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `category_id` BIGINT NOT NULL COMMENT '分类ID',
  `name` VARCHAR(100) NOT NULL COMMENT '器材名称',
  `spec` VARCHAR(255) DEFAULT NULL COMMENT '规格',
  `purpose` VARCHAR(255) DEFAULT NULL COMMENT '用途',
  `price` INT NOT NULL COMMENT '单价(整数金额)',
  `stock` INT NOT NULL DEFAULT 0 COMMENT '库存',
  `cover_url` VARCHAR(255) DEFAULT NULL COMMENT '封面图',
  `description` TEXT COMMENT '详情',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ON_SALE' COMMENT '状态(ON_SALE/OFF_SALE)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_equipment_category_id` (`category_id`),
  KEY `idx_equipment_status` (`status`),
  CONSTRAINT `fk_equipment_category` FOREIGN KEY (`category_id`) REFERENCES `equipment_category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='体育器材';

CREATE TABLE `equipment_cart_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `equipment_id` BIGINT NOT NULL COMMENT '器材ID',
  `quantity` INT NOT NULL COMMENT '数量',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cart_user_equipment` (`user_id`, `equipment_id`),
  CONSTRAINT `fk_cart_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_cart_equipment` FOREIGN KEY (`equipment_id`) REFERENCES `equipment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车';

CREATE TABLE `equipment_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no` VARCHAR(64) NOT NULL COMMENT '订单号',
  `user_id` BIGINT NOT NULL COMMENT '下单用户ID',
  `total_amount` INT NOT NULL COMMENT '订单总额(整数金额)',
  `status` VARCHAR(20) NOT NULL DEFAULT 'CREATED' COMMENT '状态(CREATED/PAID/SHIPPED/RECEIVED/CANCELED/REFUNDED)',
  `receiver_name` VARCHAR(50) NOT NULL COMMENT '收货人',
  `receiver_phone` VARCHAR(20) NOT NULL COMMENT '收货手机号',
  `receiver_address` VARCHAR(255) NOT NULL COMMENT '收货地址',
  `logistics_company` VARCHAR(50) DEFAULT NULL COMMENT '物流公司',
  `tracking_no` VARCHAR(50) DEFAULT NULL COMMENT '快递单号',
  `paid_at` DATETIME DEFAULT NULL COMMENT '支付时间(钱包扣款)',
  `shipped_at` DATETIME DEFAULT NULL COMMENT '发货时间',
  `received_at` DATETIME DEFAULT NULL COMMENT '收货时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_equipment_order_no` (`order_no`),
  KEY `idx_equipment_order_user_id` (`user_id`),
  KEY `idx_equipment_order_status` (`status`),
  CONSTRAINT `fk_equipment_order_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='器材订单';

CREATE TABLE `equipment_order_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `equipment_id` BIGINT NOT NULL COMMENT '器材ID',
  `equipment_name` VARCHAR(100) NOT NULL COMMENT '器材名称(快照)',
  `price` INT NOT NULL COMMENT '成交单价(快照)',
  `quantity` INT NOT NULL COMMENT '数量',
  `subtotal` INT NOT NULL COMMENT '小计',
  PRIMARY KEY (`id`),
  KEY `idx_equipment_order_item_order_id` (`order_id`),
  CONSTRAINT `fk_equipment_order_item_order` FOREIGN KEY (`order_id`) REFERENCES `equipment_order` (`id`),
  CONSTRAINT `fk_equipment_order_item_equipment` FOREIGN KEY (`equipment_id`) REFERENCES `equipment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='器材订单明细';

CREATE TABLE `equipment_review` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `equipment_id` BIGINT NOT NULL COMMENT '器材ID',
  `user_id` BIGINT NOT NULL COMMENT '评价用户ID',
  `rating` INT NOT NULL COMMENT '评分(1-5)',
  `content` VARCHAR(500) DEFAULT NULL COMMENT '评价内容',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_equipment_review_equipment_id` (`equipment_id`),
  KEY `idx_equipment_review_user_id` (`user_id`),
  CONSTRAINT `fk_equipment_review_equipment` FOREIGN KEY (`equipment_id`) REFERENCES `equipment` (`id`),
  CONSTRAINT `fk_equipment_review_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='器材评价';

-- =========================
-- 6) Coach Course & Video
-- =========================

CREATE TABLE `coach_course` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `coach_user_id` BIGINT NOT NULL COMMENT '教练用户ID',
  `title` VARCHAR(100) NOT NULL COMMENT '课程名称',
  `category` VARCHAR(50) DEFAULT NULL COMMENT '课程类型',
  `duration_minutes` INT DEFAULT NULL COMMENT '时长(分钟)',
  `price` INT NOT NULL COMMENT '价格(整数金额)',

  `cover_url` VARCHAR(255) DEFAULT NULL COMMENT '封面',
  `venue_id` BIGINT DEFAULT NULL COMMENT '上课场地(可选)',
  `capacity` INT NOT NULL DEFAULT 1 COMMENT '招生人数',
  `outline` TEXT COMMENT '课程大纲',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ON_SALE' COMMENT '状态(ON_SALE/OFF_SALE)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_course_coach_user_id` (`coach_user_id`),
  CONSTRAINT `fk_course_coach` FOREIGN KEY (`coach_user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_course_venue` FOREIGN KEY (`venue_id`) REFERENCES `venue` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教练课程';

CREATE TABLE `coach_course_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `course_id` BIGINT NOT NULL COMMENT '课程ID',
  `start_time` DATETIME NOT NULL COMMENT '开始时间',
  `end_time` DATETIME NOT NULL COMMENT '结束时间',
  `capacity` INT NOT NULL COMMENT '容量',
  `enrolled_count` INT NOT NULL DEFAULT 0 COMMENT '已报名人数',
  `status` VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '状态(OPEN/CLOSED/CANCELED)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_course_session_course_id` (`course_id`),
  KEY `idx_course_session_time` (`start_time`),
  CONSTRAINT `fk_course_session_course` FOREIGN KEY (`course_id`) REFERENCES `coach_course` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程排期';

CREATE TABLE `coach_course_booking` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `booking_no` VARCHAR(64) NOT NULL COMMENT '预约单号',
  `user_id` BIGINT NOT NULL COMMENT '预约用户ID',
  `course_session_id` BIGINT NOT NULL COMMENT '课程排期ID',
  `amount` INT NOT NULL COMMENT '金额(整数金额)',
  `status` VARCHAR(30) NOT NULL DEFAULT 'PENDING_COACH' COMMENT '状态(PENDING_COACH/ACCEPTED/REJECTED/PAID/CANCELED/REFUNDED/USED)',
  `coach_decision_at` DATETIME DEFAULT NULL COMMENT '教练处理时间',
  `reject_reason` VARCHAR(255) DEFAULT NULL COMMENT '拒单原因',
  `verification_code` VARCHAR(32) NOT NULL COMMENT '核销码',
  `paid_at` DATETIME DEFAULT NULL COMMENT '支付时间(钱包扣款)',
  `used_at` DATETIME DEFAULT NULL COMMENT '核销时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_booking_no` (`booking_no`),
  KEY `idx_course_booking_user_id` (`user_id`),
  KEY `idx_course_booking_session_id` (`course_session_id`),
  KEY `idx_course_booking_status` (`status`),
  CONSTRAINT `fk_course_booking_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_course_booking_session` FOREIGN KEY (`course_session_id`) REFERENCES `coach_course_session` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程预约';

CREATE TABLE `course_review` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `course_id` BIGINT NOT NULL COMMENT '课程ID',
  `coach_user_id` BIGINT NOT NULL COMMENT '教练用户ID',
  `user_id` BIGINT NOT NULL COMMENT '评价用户ID',
  `booking_id` BIGINT NOT NULL COMMENT '预约ID',
  `rating` INT NOT NULL COMMENT '评分(1-5)',
  `content` VARCHAR(500) DEFAULT NULL COMMENT '评价内容',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_review_booking_id` (`booking_id`),
  KEY `idx_course_review_course_id` (`course_id`),
  KEY `idx_course_review_coach_user_id` (`coach_user_id`),
  CONSTRAINT `fk_course_review_course` FOREIGN KEY (`course_id`) REFERENCES `coach_course` (`id`),
  CONSTRAINT `fk_course_review_coach` FOREIGN KEY (`coach_user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_course_review_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_course_review_booking` FOREIGN KEY (`booking_id`) REFERENCES `coach_course_booking` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程评价';

CREATE TABLE `venue_review` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `venue_id` BIGINT NOT NULL COMMENT '场地ID',
  `user_id` BIGINT NOT NULL COMMENT '评价用户ID',
  `booking_id` BIGINT NOT NULL COMMENT '预约ID',
  `rating` INT NOT NULL COMMENT '评分(1-5)',
  `content` VARCHAR(500) DEFAULT NULL COMMENT '评价内容',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_venue_review_booking_id` (`booking_id`),
  KEY `idx_venue_review_venue_id` (`venue_id`),
  CONSTRAINT `fk_venue_review_venue` FOREIGN KEY (`venue_id`) REFERENCES `venue` (`id`),
  CONSTRAINT `fk_venue_review_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_venue_review_booking` FOREIGN KEY (`booking_id`) REFERENCES `venue_booking` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场地评价';

CREATE TABLE `coach_video` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `coach_user_id` BIGINT NOT NULL COMMENT '教练用户ID',
  `title` VARCHAR(100) NOT NULL COMMENT '视频标题',
  `category` VARCHAR(50) DEFAULT NULL COMMENT '视频分类',
  `price` INT NOT NULL DEFAULT 0 COMMENT '价格(整数金额)',
  `cover_url` VARCHAR(255) DEFAULT NULL COMMENT '封面',
  `video_url` VARCHAR(255) NOT NULL COMMENT '视频地址',
  `description` TEXT COMMENT '视频简介',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ON_SALE' COMMENT '状态(ON_SALE/OFF_SALE)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_video_category` (`category`),
  KEY `idx_video_status` (`status`),
  KEY `idx_video_coach_user_id` (`coach_user_id`),
  CONSTRAINT `fk_video_coach` FOREIGN KEY (`coach_user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教练教学视频';

CREATE TABLE `coach_video_purchase` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `purchase_no` VARCHAR(64) NOT NULL COMMENT '购买单号',
  `user_id` BIGINT NOT NULL COMMENT '购买用户ID',
  `video_id` BIGINT NOT NULL COMMENT '视频ID',
  `amount` INT NOT NULL COMMENT '金额(整数金额)',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PAID' COMMENT '状态(PAID/REFUNDED)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_video_purchase_no` (`purchase_no`),
  UNIQUE KEY `uk_video_purchase_user_video` (`user_id`, `video_id`),
  CONSTRAINT `fk_video_purchase_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_video_purchase_video` FOREIGN KEY (`video_id`) REFERENCES `coach_video` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教练视频购买记录';

-- =========================
-- 7) Complaints & Notices
-- =========================

CREATE TABLE `complaint` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `complaint_no` VARCHAR(64) NOT NULL COMMENT '投诉单号',
  `user_id` BIGINT NOT NULL COMMENT '投诉用户ID',
  `complaint_type` VARCHAR(30) NOT NULL COMMENT '类型(VENUE/EQUIPMENT/COURSE/OTHER)',
  `content` TEXT NOT NULL COMMENT '投诉内容',
  `attachments` JSON DEFAULT NULL COMMENT '附件(图片/文件URL数组)',
  `status` VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED' COMMENT '状态(SUBMITTED/ASSIGNED/IN_PROGRESS/RESOLVED)',
  `assigned_staff_id` BIGINT DEFAULT NULL COMMENT '指派员工ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `resolved_at` DATETIME DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_complaint_no` (`complaint_no`),
  KEY `idx_complaint_user_id` (`user_id`),
  KEY `idx_complaint_status` (`status`),
  CONSTRAINT `fk_complaint_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_complaint_staff` FOREIGN KEY (`assigned_staff_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投诉工单';

CREATE TABLE `complaint_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `complaint_id` BIGINT NOT NULL COMMENT '投诉ID',
  `sender_user_id` BIGINT NOT NULL COMMENT '发送人ID',
  `sender_role` VARCHAR(20) NOT NULL COMMENT '发送人角色(USER/STAFF/ADMIN)',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `attachments` JSON DEFAULT NULL COMMENT '附件',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_complaint_message_complaint_id` (`complaint_id`),
  CONSTRAINT `fk_complaint_message_complaint` FOREIGN KEY (`complaint_id`) REFERENCES `complaint` (`id`),
  CONSTRAINT `fk_complaint_message_sender` FOREIGN KEY (`sender_user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投诉沟通记录/二次反馈';

CREATE TABLE `notice` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title` VARCHAR(200) NOT NULL COMMENT '标题',
  `notice_type` VARCHAR(50) NOT NULL COMMENT '类型(ACTIVITY/SYSTEM/VENUE_ADJUST/POLICY)',
  `content` TEXT NOT NULL COMMENT '内容',
  `cover_url` VARCHAR(255) DEFAULT NULL COMMENT '封面',
  `published` TINYINT NOT NULL DEFAULT 1 COMMENT '是否发布(1是,0否)',
  `publish_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `publisher_user_id` BIGINT NOT NULL COMMENT '发布人(管理员)',
  PRIMARY KEY (`id`),
  KEY `idx_notice_publish_at` (`publish_at`),
  CONSTRAINT `fk_notice_publisher` FOREIGN KEY (`publisher_user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告/活动';

CREATE TABLE `home_banner` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title` VARCHAR(100) DEFAULT NULL COMMENT '标题',
  `image_url` VARCHAR(255) NOT NULL COMMENT '图片URL',
  `link_url` VARCHAR(255) DEFAULT NULL COMMENT '跳转URL',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用(1是,0否)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页轮播图';

CREATE TABLE `favorite` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `target_type` VARCHAR(20) NOT NULL COMMENT '收藏类型(VENUE/COACH/COURSE/EQUIPMENT/VIDEO)',
  `target_id` BIGINT NOT NULL COMMENT '收藏目标ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite_user_target` (`user_id`, `target_type`, `target_id`),
  KEY `idx_favorite_target` (`target_type`, `target_id`),
  CONSTRAINT `fk_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏';

-- =========================
-- 根据需求新增的表
-- =========================
CREATE TABLE IF NOT EXISTS `course_consultation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `consultation_no` VARCHAR(64) NOT NULL COMMENT '咨询单号',
  `course_id` BIGINT NOT NULL COMMENT '课程ID',
  `user_id` BIGINT NOT NULL COMMENT '提问用户ID',
  `coach_user_id` BIGINT NOT NULL COMMENT '教练用户ID',
  `status` VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '状态(OPEN/CLOSED)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_consultation_no` (`consultation_no`),
  KEY `idx_course_consultation_user` (`user_id`, `updated_at`),
  KEY `idx_course_consultation_coach` (`coach_user_id`, `updated_at`),
  KEY `idx_course_consultation_course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程咨询/互动反馈主表';

CREATE TABLE IF NOT EXISTS `course_consultation_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `consultation_id` BIGINT NOT NULL COMMENT '咨询ID',
  `sender_user_id` BIGINT NOT NULL COMMENT '发送者用户ID',
  `sender_role` VARCHAR(20) NOT NULL COMMENT '发送者角色(USER/COACH/STAFF)',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_consultation_message_consultation` (`consultation_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程咨询消息记录';

CREATE TABLE IF NOT EXISTS staff_inspection_report (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `staff_user_id` BIGINT NOT NULL COMMENT '员工用户ID',
  `region` VARCHAR(100) DEFAULT NULL COMMENT '负责区域(快照)',
  `target_type` VARCHAR(20) NOT NULL COMMENT '关联对象类型(VENUE/EQUIPMENT/OTHER)',
  `venue_id` BIGINT DEFAULT NULL COMMENT '场地ID(target_type=VENUE)',
  `equipment_id` BIGINT DEFAULT NULL COMMENT '器材ID(target_type=EQUIPMENT)',
  `issue_type` VARCHAR(20) NOT NULL COMMENT '问题类型(MAINTENANCE/REPAIR/SHORTAGE/DAMAGE/OTHER)',
  `content` VARCHAR(1000) NOT NULL COMMENT '上报内容',
  `attachments` VARCHAR(2000) DEFAULT NULL COMMENT '附件URL数组(JSON)',
  `status` VARCHAR(20) NOT NULL COMMENT '状态(SUBMITTED/IN_PROGRESS/RESOLVED)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `resolved_at` DATETIME DEFAULT NULL COMMENT '解决时间',
  PRIMARY KEY (`id`),
  KEY `idx_staff_created_at` (`staff_user_id`, `created_at`),
  KEY `idx_region` (`region`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工巡检/维护上报表';

CREATE TABLE IF NOT EXISTS `user_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '接收用户ID',
  `msg_type` VARCHAR(50) NOT NULL COMMENT '消息类型(COURSE_BOOKING/COURSE_REMINDER/SYSTEM等)',
  `title` VARCHAR(200) NOT NULL COMMENT '标题',
  `content` TEXT NOT NULL COMMENT '内容',
  `ref_type` VARCHAR(50) DEFAULT NULL COMMENT '关联业务类型',
  `ref_id` BIGINT DEFAULT NULL COMMENT '关联业务ID',
  `read_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读(1是,0否)',
  `read_at` DATETIME DEFAULT NULL COMMENT '阅读时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_message_user_id` (`user_id`, `created_at`),
  KEY `idx_user_message_read_flag` (`user_id`, `read_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内信/消息中心';

CREATE TABLE IF NOT EXISTS `coach_withdraw_request` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `request_no` VARCHAR(64) NOT NULL COMMENT '申请单号',
  `coach_user_id` BIGINT NOT NULL COMMENT '教练用户ID',
  `amount` INT NOT NULL COMMENT '提现金额(整数金额)',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态(PENDING/APPROVED/REJECTED)',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注/审核说明',
  `requested_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `processed_by` BIGINT DEFAULT NULL COMMENT '处理管理员ID',
  `processed_at` DATETIME DEFAULT NULL COMMENT '处理时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coach_withdraw_request_no` (`request_no`),
  KEY `idx_coach_withdraw_coach_id` (`coach_user_id`),
  KEY `idx_coach_withdraw_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教练提现申请';

-- =========================
-- 8) Seed Data
-- =========================

INSERT INTO `sys_role` (`id`, `code`, `name`) VALUES
  (1, 'ADMIN', '管理员'),
  (2, 'STAFF', '员工'),
  (3, 'USER', '用户'),
  (4, 'COACH', '体育教练');

-- 管理员账号：admin / 123456
-- 注意：这里的 password_hash 后续应由后端使用 BCryptPasswordEncoder 存储/更新。
-- 目前按你的要求先忽略哈希值，写入占位符，保证 SQL 可直接执行。
INSERT INTO `sys_user` (`id`, `username`, `password_hash`, `nickname`, `status`) VALUES
  (1, 'admin', 'CHANGE_ME_BCRYPT', '管理员', 1);

INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (1, 1);

INSERT INTO `wallet_account` (`user_id`, `balance`) VALUES (1, 0);

INSERT INTO `sys_config` (`cfg_key`, `cfg_value`, `remark`) VALUES
  ('signin.daily_reward', '100', '每日签到奖励'),
  ('signin.week_streak_days', '7', '连续签到天数(触发额外奖励)'),
  ('signin.week_bonus', '300', '连续签到额外奖励');

INSERT INTO `venue_type` (`id`, `name`) VALUES
  (1, '篮球场'),
  (2, '羽毛球场'),
  (3, '乒乓球室');

INSERT INTO `venue` (`id`, `type_id`, `name`, `area`, `address`, `spec`, `open_time_desc`, `price_per_hour`, `contact_phone`, `status`, `description`) VALUES
  (1, 1, '社区篮球场A', '一区', 'XX社区体育中心1号', '标准全场', '08:00-22:00', 50, '13800000000', 'ACTIVE', '室外篮球场，夜间照明良好'),
  (2, 2, '社区羽毛球馆B', '二区', 'XX社区体育中心2号', '4片场地', '09:00-21:00', 35, '13800000001', 'ACTIVE', '室内羽毛球馆，提供更衣室');

INSERT INTO `equipment_category` (`id`, `name`) VALUES
  (1, '球类'),
  (2, '护具');

INSERT INTO `equipment` (`id`, `category_id`, `name`, `spec`, `purpose`, `price`, `stock`, `status`) VALUES
  (1, 1, '标准篮球', '7号球', '篮球训练/比赛', 120, 50, 'ON_SALE'),
  (2, 2, '护膝', '均码', '运动防护', 59, 100, 'ON_SALE');

INSERT INTO `notice` (`id`, `title`, `notice_type`, `content`, `published`, `publisher_user_id`) VALUES
  (1, '系统上线公告', 'SYSTEM', '社区运动场地管理系统已上线，欢迎体验。', 1, 1);

SET FOREIGN_KEY_CHECKS = 1;
