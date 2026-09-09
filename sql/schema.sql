-- =============================================================================
-- InfiniteChat 数据库 Schema
-- =============================================================================
-- 数据库名：InfiniteChat
-- 依赖：MySQL 8.0+（使用了 utf8mb4_0900_ai_ci 排序规则，MySQL 5.7 不支持）
-- 本地开发环境：MySQL 容器映射到宿主机 49152 端口，详见项目根目录 helpme.md
--
-- 执行方式：
--   mysql -h 127.0.0.1 -P 49152 -u root -p < sql/schema.sql
--
-- 【警告】脚本第一条语句是 DROP DATABASE IF EXISTS InfiniteChat，
--         会连同库中所有数据一起删除。请仅在初始化全新环境时执行。
--
-- 约定说明：
--   * 所有主键均为 BIGINT，由应用层雪花算法生成
--     （Common 模块 SnowflakeUtil / MyBatis-Plus IdType.ASSIGN_ID），
--     表上没有 AUTO_INCREMENT，插入时由业务代码显式赋值。
--   * 所有金额字段单位均为「分」，用 BIGINT 存储，避免浮点精度问题。
--   * 时间字段统一为 created_time / updated_time，由 MySQL 自动维护，
--     updated_time 带 ON UPDATE CURRENT_TIMESTAMP。
--   * 删除统一用 is_delete / status 字段做逻辑标记，不做物理删除。
--
-- 表清单：
--   user                 用户表
--   user_balance         用户余额表
--   balance_log          余额变动记录表
--   session              会话表（单聊 / 群聊）
--   user_session         用户会话关系表
--   message              消息表
--   friend               好友关系表
--   apply_friend         好友申请表
--   red_packet           红包主表
--   red_packet_receive   红包领取记录表
-- =============================================================================


-- 判断数据库是否存在，如果存在则删除
DROP DATABASE IF EXISTS InfiniteChat;


-- 创建新的数据库
CREATE DATABASE InfiniteChat;


-- 使用 InfiniteChat 数据库
USE InfiniteChat;


-- 用户表
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`
(
    `user_id`      BIGINT       NOT NULL COMMENT '用户ID（自增）',
    `phone`        CHAR(11)              DEFAULT NULL COMMENT '用户手机号',
    `email`        VARCHAR(128) NOT NULL COMMENT '用户邮箱',
    `password`     VARCHAR(256) NOT NULL COMMENT '用户密码',
    `nickname`     VARCHAR(128) NOT NULL COMMENT '用户昵称',
    `avatar`       VARCHAR(512)          DEFAULT NULL COMMENT '用户头像url',
    `gender`       TINYINT(1)   NOT NULL DEFAULT 2 COMMENT '性别 0 女 1 男 2 未知',
    `description`  TEXT                  DEFAULT NULL COMMENT '个性签名',
    `state`        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '状态 0 正常 1 封禁 2 注销',
    `role`         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '角色类型 0 普通用户 1 管理员 2 超级管理员',
    `created_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete`    TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标记（0未删 1已删）',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `idx_phone` (`phone`),
    UNIQUE KEY `idx_email` (`email`),
    KEY `idx_state` (`state`),
    KEY `idx_role` (`role`),
    KEY `idx_create_time` (`created_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户表'
  ROW_FORMAT = Dynamic;


-- 用户余额表
DROP TABLE IF EXISTS `user_balance`;
CREATE TABLE `user_balance`
(
    `user_id`      BIGINT         NOT NULL COMMENT '用户 ID',
    `balance` bigint NOT NULL DEFAULT 0 COMMENT '余额（单位：分）',
    `created_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户余额表'
  ROW_FORMAT = Dynamic;


-- 余额变动记录表
DROP TABLE IF EXISTS `balance_log`;
CREATE TABLE `balance_log`
(
    `balance_log_id` BIGINT         NOT NULL COMMENT '记录 ID',
    `user_id`        BIGINT         NOT NULL COMMENT '用户 ID',
    `amount` bigint NOT NULL COMMENT '变动金额（单位：分），正数为增加，负数为减少',
    `type`           TINYINT        NOT NULL COMMENT '变动类型：0 发送红包，1 领取红包，2 红包退回',
    `related_id`     BIGINT         NULL     DEFAULT NULL COMMENT '关联 ID，如红包 ID',
    `created_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`balance_log_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '余额变动记录表'
  ROW_FORMAT = Dynamic;


-- 会话表
DROP TABLE IF EXISTS `session`;
CREATE TABLE `session`
(
    `session_id`   BIGINT       NOT NULL COMMENT '会话 ID',
    `name`         VARCHAR(255)     DEFAULT NULL COMMENT '名称',
    `type`         TINYINT      NOT NULL COMMENT '类别：0 单聊，1 群聊',
    `status`       TINYINT      NOT NULL COMMENT '状态：0 正常，1 删除',
    `created_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`session_id`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '会话表'
  ROW_FORMAT = Dynamic;


-- 用户会话关系表
DROP TABLE IF EXISTS `user_session`;
CREATE TABLE `user_session`
(
    `user_id`      BIGINT   NOT NULL COMMENT '用户 id',
    `session_id`   BIGINT   NOT NULL COMMENT '会话 id',
    `role`         TINYINT  NOT NULL COMMENT '角色：0 群主，1 管理员，2 普通用户',
    `status`       TINYINT  NOT NULL COMMENT '状态：0 正常，1 删除',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`user_id`, `session_id`),
    INDEX `idx_session_id` (`session_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户会话关系表'
  ROW_FORMAT = Dynamic;


-- 消息表
DROP TABLE IF EXISTS `message`;
CREATE TABLE `message`
(
    `message_id`   BIGINT   NOT NULL COMMENT '消息 id',
    `sender_id`    BIGINT   NOT NULL COMMENT '发送者 id',
    `session_id`   BIGINT   NOT NULL COMMENT '会话 id',
    `type`         TINYINT  NOT NULL COMMENT '消息类型: 0 文本消息，1 图片消息，3 红包，4 表情包',
    `content`      text     NOT NULL COMMENT '消息内容',
    `reply_id`     BIGINT            DEFAULT NULL COMMENT '消息引用 id',
    `session_type` TINYINT  NOT NULL COMMENT '会话类型: 0 单聊，1 群聊',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`message_id`) USING BTREE,
    INDEX `idx_session_time` (`session_id`, `created_time`),
    INDEX `idx_sender_time` (`sender_id`, `created_time`),
    INDEX `idx_reply_id` (`reply_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '消息表'
  ROW_FORMAT = Dynamic;


-- 好友关系表
DROP TABLE IF EXISTS `friend`;
CREATE TABLE `friend`
(
    `user_id`      BIGINT   NOT NULL COMMENT '用户 ID',
    `friend_id`    BIGINT   NOT NULL COMMENT '好友 ID',
    `status`       TINYINT  NOT NULL DEFAULT 0 COMMENT '好友状态：0好友，1拉黑，2删除',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`user_id`, `friend_id`),
    INDEX `idx_friend_id` (`friend_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '好友关系表'
  ROW_FORMAT = Dynamic;


-- 好友申请表
DROP TABLE IF EXISTS `apply_friend`;
CREATE TABLE `apply_friend` (
                                `apply_friend_id` bigint NOT NULL COMMENT '申请 ID',
                                `sender_id` bigint NOT NULL COMMENT '发送者用户ID',
                                `receiver_id` bigint NOT NULL COMMENT '接收者用户ID',
                                `message` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '请求添加好友' COMMENT '申请信息',
                                `status` tinyint NOT NULL DEFAULT '0' COMMENT '申请状态：0未读，1通过，2拒绝，3已读，4过期',
                                `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                PRIMARY KEY (`apply_friend_id`),
                                UNIQUE KEY `uk_sender_receiver` (`sender_id`,`receiver_id`),
                                KEY `idx_sender_status` (`sender_id`,`status`),
                                KEY `idx_receiver_status` (`receiver_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='好友申请表';


-- 红包表
DROP TABLE IF EXISTS `red_packet`;
CREATE TABLE `red_packet`
(
    `red_packet_id`           BIGINT         NOT NULL COMMENT '红包 ID',
    `sender_id`               BIGINT         NOT NULL COMMENT '发送者用户 ID',
    `session_id`              BIGINT         NOT NULL COMMENT '会话 ID（单聊或群聊）',
    `red_packet_wrapper_text` VARCHAR(50)    NOT NULL DEFAULT '恭喜发财，大吉大利' COMMENT '红包封面文案',
    `red_packet_type`         TINYINT        NOT NULL COMMENT '红包类型：0 普通红包，1 拼手气红包',
    `total_amount` bigint NOT NULL COMMENT '红包总金额(单位：分)',
    `total_count`             INT            NOT NULL COMMENT '红包总个数',
    `status`                  TINYINT        NOT NULL DEFAULT 0 COMMENT '状态：0 未领取完，1 已领取完，2 已过期',
    `created_time`            DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`            DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`red_packet_id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_sender_id` (`sender_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '红包主表'
  ROW_FORMAT = Dynamic;


-- 红包领取记录表
DROP TABLE IF EXISTS `red_packet_receive`;
CREATE TABLE `red_packet_receive`
(
    `red_packet_receive_id` BIGINT         NOT NULL COMMENT '记录 ID',
    `red_packet_id`         BIGINT         NOT NULL COMMENT '红包 ID',
    `receiver_id`           BIGINT         NOT NULL COMMENT '领取者用户 ID',
    `amount` bigint NOT NULL COMMENT '领取金额（单位：分）',
    `received_at`           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
    `created_time`          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`red_packet_receive_id`),
    INDEX `idx_red_packet_id` (`red_packet_id`),
    INDEX `idx_receiver_id` (`receiver_id`),
    INDEX `idx_red_packet_receiver` (`red_packet_id`, `receiver_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '红包领取记录表'
  ROW_FORMAT = Dynamic;
