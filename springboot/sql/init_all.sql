-- BangBangAgro database initialization script.
-- First install: import this file into MySQL 8.0+.
-- This file contains schema and required migrations only. Demo data and maintenance scripts are not included.
-- Generated from springboot/sql organized scripts.


-- ============================================================
-- Begin: 00_schema\000_base_schema.sql
-- ============================================================
-- MySQL dump 10.13  Distrib 8.0.44, for Win64 (x86_64)
--
-- Host: localhost    Database: smart-agriculture
-- ------------------------------------------------------
-- Server version	8.0.44

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `smart-agriculture`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `smart-agriculture` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `smart-agriculture`;



--
-- Table structure for table `agent_user_memory`
--

DROP TABLE IF EXISTS `agent_user_memory`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_user_memory` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` int NOT NULL COMMENT 'sys_user.id',
  `preferences` text COMMENT '用户偏好或显式「记住」的要点',
  `conversation_summary` text COMMENT '近期多轮对话滚动摘要',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_user_memory_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent 用户记忆';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crop_yield_config`
--

DROP TABLE IF EXISTS `crop_yield_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crop_yield_config` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `crop_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作物名称',
  `yield_per_mu` decimal(10,2) DEFAULT NULL COMMENT '每亩产量（公斤）',
  `unit_price` decimal(10,2) DEFAULT NULL COMMENT '销售单价（元/公斤）',
  `cost_per_mu` decimal(10,2) DEFAULT NULL COMMENT '每亩成本（元）',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_crop_name` (`crop_name`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='作物产量配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `decision_record`
--

DROP TABLE IF EXISTS `decision_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `decision_record` (
  `id` int NOT NULL AUTO_INCREMENT,
  `field_id` int NOT NULL,
  `field_name` varchar(64) NOT NULL,
  `action_type` varchar(32) NOT NULL,
  `action_name` varchar(128) NOT NULL,
  `priority` varchar(16) DEFAULT NULL,
  `decision_status` varchar(16) NOT NULL,
  `auto_executable` tinyint DEFAULT '0',
  `explanation` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `executed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=107 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `device_action_log`
--

DROP TABLE IF EXISTS `device_action_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `device_action_log` (
  `id` int NOT NULL AUTO_INCREMENT,
  `field_id` int NOT NULL,
  `field_name` varchar(64) NOT NULL,
  `device_code` varchar(32) NOT NULL,
  `action_type` varchar(32) NOT NULL,
  `target_value` varchar(32) DEFAULT NULL,
  `action_status` varchar(16) NOT NULL,
  `feedback` varchar(255) DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `disease_detect_record`
--

DROP TABLE IF EXISTS `disease_detect_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `disease_detect_record` (
  `id` int NOT NULL AUTO_INCREMENT,
  `field_id` int NOT NULL,
  `field_name` varchar(64) NOT NULL,
  `crop_name` varchar(64) DEFAULT NULL,
  `disease_name` varchar(64) DEFAULT NULL,
  `confidence` double DEFAULT NULL,
  `image_url` varchar(255) DEFAULT NULL,
  `source` varchar(32) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `farmland_alert`
--

DROP TABLE IF EXISTS `farmland_alert`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `farmland_alert` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `farmland_id` int DEFAULT NULL COMMENT '农田ID',
  `farmland_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '农田名称',
  `alert_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '预警类型：temperature/soil_humidity/air_humidity/ph/carbon/light',
  `alert_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '预警级别：low/medium/high',
  `current_value` decimal(10,2) DEFAULT NULL COMMENT '当前值',
  `threshold_min` decimal(10,2) DEFAULT NULL COMMENT '阈值下限',
  `threshold_max` decimal(10,2) DEFAULT NULL COMMENT '阈值上限',
  `message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '预警消息',
  `suggestion` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作建议',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '状态：pending/processed',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `process_time` timestamp NULL DEFAULT NULL COMMENT '处理时间',
  `processor` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理人',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_farmland_id` (`farmland_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_create_time` (`create_time`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='农田预警表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `field_prediction_record`
--

DROP TABLE IF EXISTS `field_prediction_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `field_prediction_record` (
  `id` int NOT NULL AUTO_INCREMENT,
  `field_id` int NOT NULL,
  `field_name` varchar(64) NOT NULL,
  `prediction_type` varchar(32) NOT NULL,
  `prediction_value` double DEFAULT NULL,
  `unit` varchar(16) DEFAULT NULL,
  `horizon_hours` int DEFAULT NULL,
  `confidence` double DEFAULT NULL,
  `summary` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=553 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `field_risk_record`
--

DROP TABLE IF EXISTS `field_risk_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `field_risk_record` (
  `id` int NOT NULL AUTO_INCREMENT,
  `field_id` int NOT NULL,
  `field_name` varchar(64) NOT NULL,
  `crop_name` varchar(64) DEFAULT NULL,
  `growth_stage` varchar(64) DEFAULT NULL,
  `water_risk` int DEFAULT NULL,
  `disease_risk` int DEFAULT NULL,
  `env_risk` int DEFAULT NULL,
  `overall_risk` int DEFAULT NULL,
  `priority` varchar(16) DEFAULT NULL,
  `suggested_action` varchar(128) DEFAULT NULL,
  `reason_json` text,
  `predicted_soil_humidity` int DEFAULT NULL,
  `disease_confidence` double DEFAULT NULL,
  `weather_summary` varchar(128) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1289 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `health_index_config`
--

DROP TABLE IF EXISTS `health_index_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `health_index_config` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `indicator` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '指标名称：temperature/air_humidity/soil_humidity/carbon/ph/light',
  `excellent_min` decimal(10,2) DEFAULT NULL COMMENT '优秀区间最小值',
  `excellent_max` decimal(10,2) DEFAULT NULL COMMENT '优秀区间最大值',
  `good_min` decimal(10,2) DEFAULT NULL COMMENT '良好区间最小值',
  `good_max` decimal(10,2) DEFAULT NULL COMMENT '良好区间最大值',
  `weight` decimal(5,2) DEFAULT '1.00' COMMENT '权重（0-1）',
  `threshold_min` decimal(10,2) DEFAULT NULL COMMENT '预警下限',
  `threshold_max` decimal(10,2) DEFAULT NULL COMMENT '预警上限',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_indicator` (`indicator`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='健康指数配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inventory`
--

DROP TABLE IF EXISTS `inventory`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `produce` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '产品',
  `warehouse` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '仓库',
  `region` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '存储区',
  `number` int DEFAULT NULL COMMENT '数量',
  `safe_stock` int DEFAULT NULL COMMENT '安全库存',
  `max_stock` int DEFAULT NULL COMMENT '最大库存',
  `daily_consumption` decimal(10,2) DEFAULT '0.00' COMMENT '日均消耗量',
  `last_outbound_time` timestamp NULL DEFAULT NULL COMMENT '最后出库时间',
  `keeper` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '仓库管理员',
  `remark` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inventory_outbound`
--

DROP TABLE IF EXISTS `inventory_outbound`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_outbound` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `inventory_id` int DEFAULT NULL COMMENT '物资ID',
  `produce` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '产品名称',
  `quantity` int DEFAULT NULL COMMENT '出库数量',
  `outbound_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '出库时间',
  `operator` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_inventory_id` (`inventory_id`) USING BTREE,
  KEY `idx_outbound_time` (`outbound_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='物资出库记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notice`
--

DROP TABLE IF EXISTS `notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notice` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '标题',
  `content` text CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci COMMENT '内容',
  `time` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '发布时间',
  `user` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '发布人',
  `img` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '封面',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `purchase`
--

DROP TABLE IF EXISTS `purchase`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `product` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '产品',
  `price` decimal(10,2) DEFAULT NULL COMMENT '价格（元）',
  `number` int DEFAULT NULL COMMENT '采购数量',
  `provider` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '供应商',
  `address` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '地址',
  `phone` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '电话',
  `purchaser` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '采购人',
  `remark` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sales`
--

DROP TABLE IF EXISTS `sales`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `product` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '产品',
  `price` decimal(10,2) DEFAULT NULL COMMENT '价格（元）',
  `number` int DEFAULT NULL COMMENT '采购数量',
  `buyer` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '采购商',
  `address` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '地址',
  `phone` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '电话',
  `shipper` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '出货人',
  `remark` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sensor_reading`
--

DROP TABLE IF EXISTS `sensor_reading`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sensor_reading` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `temperature` double DEFAULT NULL COMMENT '温度（℃）',
  `humidity` double DEFAULT NULL COMMENT '湿度（%）',
  `led` int DEFAULT '0' COMMENT 'LED状态（0-关闭，1-开启）',
  `device_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '设备名称',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE,
  KEY `idx_device_name` (`device_name`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=72874 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='传感器数据表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `statistic`
--

DROP TABLE IF EXISTS `statistic`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `statistic` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `farm` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '农田名称',
  `area` int DEFAULT NULL COMMENT '面积',
  `address` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '地址',
  `district` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '所属区县',
  `crop` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '作物名称',
  `number` int DEFAULT NULL COMMENT '数量',
  `state` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '生长状态',
  `temperature` decimal(11,1) DEFAULT NULL COMMENT '温度（℃）',
  `airhumidity` int DEFAULT NULL COMMENT '空气湿度（%）',
  `soilhumidity` int DEFAULT NULL COMMENT '土壤湿度（%）',
  `carbon` int DEFAULT NULL COMMENT 'CO2含量ppm',
  `ph` decimal(11,2) DEFAULT NULL COMMENT '土壤PH值',
  `light` int DEFAULT NULL COMMENT '光照强度(lux)',
  `health_index` int DEFAULT NULL COMMENT '健康指数（0-100）',
  `health_index_update_time` timestamp NULL DEFAULT NULL COMMENT '健康指数更新时间',
  `filllight` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '补光灯状态',
  `monitor` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '摄像头状态',
  `pump` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '水泵状态',
  `keeper` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL COMMENT '农田负责人',
  `center_lng` decimal(10,6) DEFAULT NULL COMMENT '中心经度',
  `center_lat` decimal(10,6) DEFAULT NULL COMMENT '中心纬度',
  `coordinates` text CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci COMMENT '区域坐标JSON（多边形顶点数组）',
  `centerLng` decimal(10,6) DEFAULT NULL,
  `centerLat` decimal(10,6) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_dict`
--

DROP TABLE IF EXISTS `sys_dict`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称',
  `value` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内容',
  `type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '类型',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=281 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_file`
--

DROP TABLE IF EXISTS `sys_file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_file` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件名称',
  `type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件类型',
  `size` bigint DEFAULT NULL COMMENT '文件大小(kb)',
  `url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '下载链接',
  `md5` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件md5',
  `is_delete` tinyint(1) DEFAULT '0' COMMENT '是否删除',
  `enable` tinyint(1) DEFAULT '1' COMMENT '是否禁用链接',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=73 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_menu`
--

DROP TABLE IF EXISTS `sys_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称',
  `path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '路径',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图标',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '描述',
  `pid` int DEFAULT NULL COMMENT '父级id',
  `page_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '页面路径',
  `sort_num` int DEFAULT NULL COMMENT '排序',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=48 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '名称',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '描述',
  `flag` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '唯一标识',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role_menu`
--

DROP TABLE IF EXISTS `sys_role_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_menu` (
  `role_id` int NOT NULL COMMENT '角色id',
  `menu_id` int NOT NULL COMMENT '菜单id',
  PRIMARY KEY (`role_id`,`menu_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='角色菜单关系表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户名',
  `password` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '昵称',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '电话',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '地址',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `avatar_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像',
  `role` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '角色',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `weather_snapshot`
--

DROP TABLE IF EXISTS `weather_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `weather_snapshot` (
  `id` int NOT NULL AUTO_INCREMENT,
  `city_name` varchar(64) NOT NULL,
  `weather_type` varchar(32) NOT NULL,
  `temperature` int DEFAULT NULL,
  `humidity` int DEFAULT NULL,
  `rain_probability` int DEFAULT NULL,
  `wind_level` varchar(16) DEFAULT NULL,
  `advisory` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- Schema-only export generated from .claude/worktrees/naughty-benz-db727f/smart-agriculture.sql; data rows are intentionally excluded.

-- End: 00_schema\000_base_schema.sql


-- ============================================================
-- Begin: 00_schema\ai_config_table.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_ai_config` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `provider` varchar(50) NOT NULL DEFAULT 'qwen',
  `base_url` varchar(512) NOT NULL,
  `api_key` varchar(512) NOT NULL DEFAULT '',
  `model_name` varchar(100) NOT NULL DEFAULT 'qwen-max',
  `chat_model_name` varchar(100) NOT NULL DEFAULT '',
  `chat_base_url` varchar(512) NOT NULL DEFAULT '',
  `chat_api_key` varchar(512) NOT NULL DEFAULT '',
  `temperature` decimal(4,2) NOT NULL DEFAULT 0.42,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- End: 00_schema\ai_config_table.sql


-- ============================================================
-- Begin: 00_schema\agent_decision_chain.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS `agent_decision_chain` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `chain_id` varchar(64) NOT NULL,
  `user_id` int DEFAULT NULL,
  `trigger_source` varchar(32) NOT NULL,
  `user_question` text,
  `step_index` int NOT NULL,
  `step_type` varchar(32) NOT NULL,
  `step_content` text,
  `step_detail` json DEFAULT NULL,
  `model_name` varchar(64) DEFAULT NULL,
  `round_number` int DEFAULT NULL,
  `duration_ms` int DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_chain_id` (`chain_id`),
  KEY `idx_trigger_source` (`trigger_source`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- End: 00_schema\agent_decision_chain.sql


-- ============================================================
-- Begin: 00_schema\agent_task_queue.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS `agent_task_queue` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` varchar(64) NOT NULL,
  `chain_id` varchar(64) DEFAULT NULL,
  `task_type` varchar(32) NOT NULL,
  `task_status` varchar(16) NOT NULL DEFAULT 'pending',
  `priority` varchar(16) NOT NULL DEFAULT 'medium',
  `risk_level` varchar(16) NOT NULL DEFAULT 'low',
  `auto_execute` tinyint NOT NULL DEFAULT 0,
  `farm_name` varchar(125) DEFAULT NULL,
  `action_type` varchar(64) NOT NULL,
  `action_params` json DEFAULT NULL,
  `reasoning` text,
  `knowledge_refs` json DEFAULT NULL,
  `confidence_score` decimal(5,2) DEFAULT NULL,
  `approved_by` int DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `executed_at` datetime DEFAULT NULL,
  `execution_result` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `expires_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id` (`task_id`),
  KEY `idx_status` (`task_status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- End: 00_schema\agent_task_queue.sql


-- ============================================================
-- Begin: 00_schema\agent_user_memory.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS `agent_user_memory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `preferences` text,
  `conversation_summary` text,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_user_memory_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- End: 00_schema\agent_user_memory.sql


-- ============================================================
-- Begin: 00_schema\auto_patrol_log.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS `auto_patrol_log` (
  `id` int NOT NULL AUTO_INCREMENT,
  `patrol_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `trigger_type` varchar(20) NOT NULL DEFAULT 'scheduled',
  `farm_name` varchar(100) DEFAULT NULL,
  `action_type` varchar(50) DEFAULT NULL,
  `action_detail` text DEFAULT NULL,
  `reason` text DEFAULT NULL,
  `result` varchar(20) NOT NULL DEFAULT 'success',
  `ai_report` text DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_patrol_time` (`patrol_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- End: 00_schema\auto_patrol_log.sql


-- ============================================================
-- Begin: 00_schema\chat_tables.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS `chat_message` (
  `id` int NOT NULL AUTO_INCREMENT,
  `from_user_id` int DEFAULT NULL,
  `from_username` varchar(100) DEFAULT NULL,
  `from_avatar` varchar(500) DEFAULT NULL,
  `to_user_id` int DEFAULT NULL,
  `to_group_id` int DEFAULT NULL,
  `content` text,
  `message_type` varchar(20) DEFAULT 'text',
  `is_read` tinyint DEFAULT 0,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_private` (`from_user_id`, `to_user_id`),
  KEY `idx_to_user` (`to_user_id`),
  KEY `idx_group` (`to_group_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `chat_group` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(100) DEFAULT NULL,
  `avatar_url` varchar(500) DEFAULT NULL,
  `owner_id` int DEFAULT NULL,
  `owner_name` varchar(100) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `chat_group_member` (
  `id` int NOT NULL AUTO_INCREMENT,
  `group_id` int DEFAULT NULL,
  `user_id` int DEFAULT NULL,
  `username` varchar(100) DEFAULT NULL,
  `join_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_user` (`group_id`, `user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- End: 00_schema\chat_tables.sql


-- ============================================================
-- Begin: 00_schema\farmland_alert_create.sql
-- ============================================================
-- =====================================================================
-- farmland_alert 建表参考 (仅全新环境使用)
--
-- 背景: 仓库内没有 farmland_alert 的建表 DDL (该表只存在于运行库)。
--   add_score_attribution.sql 里的 ALTER TABLE farmland_alert 在已部署库
--   可直接执行; 但若在全新空库部署, 表不存在会报错, 此时先跑本文件建表。
--   本文件依据 entity/FarmlandAlert.java 字段重建, 若你的运行库结构有出入,
--   以运行库为准, 本文件仅作参考。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `farmland_alert` (
  `id`                INT          NOT NULL AUTO_INCREMENT,
  `farmland_id`       INT          DEFAULT NULL COMMENT '农田ID',
  `farmland_name`     VARCHAR(100) DEFAULT NULL COMMENT '农田名称',
  `alert_type`        VARCHAR(50)  DEFAULT NULL COMMENT 'temperature/soil_humidity/air_humidity/ph/carbon/light/visual/iot_visual',
  `alert_level`       VARCHAR(20)  DEFAULT NULL COMMENT 'low/medium/high',
  `current_value`     DECIMAL(10,2) DEFAULT NULL COMMENT '当前值',
  `threshold_min`     DECIMAL(10,2) DEFAULT NULL COMMENT '阈值下限',
  `threshold_max`     DECIMAL(10,2) DEFAULT NULL COMMENT '阈值上限',
  `message`           TEXT         DEFAULT NULL COMMENT '预警消息',
  `suggestion`        TEXT         DEFAULT NULL COMMENT '操作建议',
  `status`            VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending/processed',
  `create_time`       DATETIME     DEFAULT NULL COMMENT '创建时间',
  `process_time`      DATETIME     DEFAULT NULL COMMENT '处理时间',
  `processor`         VARCHAR(100) DEFAULT NULL COMMENT '处理人(自由文本, 保留向后兼容)',
  `processor_user_id` INT          DEFAULT NULL COMMENT '处理人 sys_user.id (归因用)',
  PRIMARY KEY (`id`),
  KEY `idx_status_create` (`status`, `create_time`),
  KEY `idx_farmland` (`farmland_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='农田预警';

-- End: 00_schema\farmland_alert_create.sql


-- ============================================================
-- Begin: 00_schema\friend_request_table.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS `friend_request` (
  `id` int NOT NULL AUTO_INCREMENT,
  `from_user_id` int NOT NULL,
  `to_user_id` int NOT NULL,
  `status` varchar(10) NOT NULL DEFAULT 'pending',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_to_user` (`to_user_id`),
  KEY `idx_from_user` (`from_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- End: 00_schema\friend_request_table.sql


-- ============================================================
-- Begin: 00_schema\knowledge_document.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS `knowledge_document` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL,
  `category` varchar(64) NOT NULL,
  `content` text NOT NULL,
  `content_chunk` text,
  `embedding` json DEFAULT NULL,
  `metadata` json DEFAULT NULL,
  `source` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- End: 00_schema\knowledge_document.sql


-- ============================================================
-- Begin: 00_schema\sensor_event.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS `sensor_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `farm_name` varchar(125) NOT NULL,
  `event_type` varchar(32) NOT NULL,
  `metric_name` varchar(32) NOT NULL,
  `current_value` decimal(10,2) NOT NULL,
  `threshold_value` decimal(10,2) NOT NULL,
  `severity` varchar(16) NOT NULL,
  `handled` tinyint DEFAULT 0,
  `chain_id` varchar(64) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_handled` (`handled`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- End: 00_schema\sensor_event.sql


-- ============================================================
-- Begin: 00_schema\spring_ai_chat_memory.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS `SPRING_AI_CHAT_MEMORY` (
  `conversation_id` varchar(256) NOT NULL,
  `index` int NOT NULL,
  `content` text NOT NULL,
  `type` varchar(10) NOT NULL,
  `timestamp` timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`conversation_id`, `index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- End: 00_schema\spring_ai_chat_memory.sql


-- ============================================================
-- Begin: 00_schema\add_score_tables.sql
-- ============================================================
-- =====================================================================
-- 周滚动员工评分 — 评分快照 + 调整审计表
-- 运行一次即可 (CREATE TABLE IF NOT EXISTS 幂等)
-- =====================================================================

CREATE TABLE IF NOT EXISTS `user_score_snapshot` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`        INT          NOT NULL COMMENT 'FK sys_user.id',
  `window_start`   DATETIME     NOT NULL COMMENT '窗口起点(含) = now - 7d',
  `window_end`     DATETIME     NOT NULL COMMENT '窗口终点(不含) = now',
  `attendance_sub` DECIMAL(6,2) DEFAULT NULL COMMENT '出勤子分 0-100, NULL=该维度本周无数据',
  `alert_sub`      DECIMAL(6,2) DEFAULT NULL COMMENT '预警响应子分',
  `ai_sub`         DECIMAL(6,2) DEFAULT NULL COMMENT '智能体作业子分',
  `approval_sub`   DECIMAL(6,2) DEFAULT NULL COMMENT '审批把关子分',
  `knowledge_sub`  DECIMAL(6,2) DEFAULT NULL COMMENT '知识沉淀子分',
  `total`          DECIMAL(6,2) NOT NULL COMMENT '加权总分 0-100',
  `grade`          CHAR(1)      NOT NULL COMMENT '评级 S/A/B/C/D',
  `commentary`     TEXT         DEFAULT NULL COMMENT 'AI 综合评语',
  `data_thin`      TINYINT      NOT NULL DEFAULT 0 COMMENT '1=本周作业事件过少, 评级仅供参考',
  `is_override`    TINYINT      NOT NULL DEFAULT 0 COMMENT '1=管理员已覆写总分',
  `computed_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_window` (`user_id`, `window_end`),
  KEY `idx_window_end`  (`window_end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='周滚动员工评分快照(每窗口追加, 保留历史)';

CREATE TABLE IF NOT EXISTS `score_adjustment` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT,
  `user_id`      INT           NOT NULL,
  `window_start` DATETIME      NOT NULL,
  `admin_id`     INT           NOT NULL COMMENT '操作管理员 sys_user.id',
  `old_total`    DECIMAL(6,2)  NOT NULL,
  `new_total`    DECIMAL(6,2)  NOT NULL,
  `reason`       VARCHAR(500)  NOT NULL,
  `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_window` (`user_id`, `window_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评分人工覆写审计';

-- End: 00_schema\add_score_tables.sql


-- ============================================================
-- Begin: 01_migrations\add_user_status.sql
-- ============================================================
-- Add user status: 0 enabled, 1 disabled.
ALTER TABLE `sys_user`
  ADD COLUMN `status` int NOT NULL DEFAULT 0;

-- End: 01_migrations\add_user_status.sql


-- ============================================================
-- Begin: 01_migrations\add_last_login_time.sql
-- ============================================================
-- Add last login time for attendance/statistics features.
ALTER TABLE `sys_user`
  ADD COLUMN `last_login_time` datetime DEFAULT NULL COMMENT 'last login time' AFTER `status`;

-- End: 01_migrations\add_last_login_time.sql


-- ============================================================
-- Begin: 01_migrations\add_security_question.sql
-- ============================================================
-- Add password recovery security question fields.
ALTER TABLE `sys_user`
  ADD COLUMN `security_question` varchar(100) DEFAULT NULL COMMENT 'security question' AFTER `last_login_time`,
  ADD COLUMN `security_answer` varchar(100) DEFAULT NULL COMMENT 'security answer' AFTER `security_question`;

-- End: 01_migrations\add_security_question.sql


-- ============================================================
-- Begin: 01_migrations\add_last_login_ip.sql
-- ============================================================
-- Add last login IP and coarse region fields.
ALTER TABLE `sys_user`
  ADD COLUMN `last_login_ip` VARCHAR(64) DEFAULT NULL COMMENT 'last login IP' AFTER `last_login_time`;

ALTER TABLE `sys_user`
  ADD COLUMN `last_login_region` VARCHAR(100) DEFAULT NULL COMMENT 'last login IP region' AFTER `last_login_ip`;

-- End: 01_migrations\add_last_login_ip.sql


-- ============================================================
-- Begin: 01_migrations\add_dual_model_fields.sql
-- ============================================================
ALTER TABLE `sys_ai_config`
  ADD COLUMN `chat_model_name` varchar(100) NOT NULL DEFAULT '' AFTER `model_name`,
  ADD COLUMN `chat_base_url` varchar(512) NOT NULL DEFAULT '' AFTER `chat_model_name`,
  ADD COLUMN `chat_api_key` varchar(512) NOT NULL DEFAULT '' AFTER `chat_base_url`;

-- End: 01_migrations\add_dual_model_fields.sql


-- ============================================================
-- Begin: 01_migrations\add_ai_action_policy.sql
-- ============================================================
-- Add per-user AI write policy and task owner fields.
ALTER TABLE sys_ai_config
    ADD COLUMN ai_action_policy VARCHAR(20) DEFAULT 'semi_approval'
    COMMENT 'AI action policy: full_auto, semi_approval, full_approval';

ALTER TABLE agent_task_queue
    ADD COLUMN user_id INT NULL COMMENT 'requesting user id';

-- End: 01_migrations\add_ai_action_policy.sql


-- ============================================================
-- Begin: 01_migrations\add_score_attribution.sql
-- ============================================================
-- =====================================================================
-- 周滚动员工评分 — 为 3 张源表补充"操作人 userId"归位列
-- 类型 INT, 对齐 sys_user.id (IdType.AUTO Integer); 不加物理 FK (项目惯例)
-- 在已部署的运行库直接执行即可 (表已存在)。
-- =====================================================================

ALTER TABLE `farmland_alert`
  ADD COLUMN `processor_user_id` INT DEFAULT NULL COMMENT '处理人 sys_user.id (归因用)' AFTER `processor`;

ALTER TABLE `auto_patrol_log`
  ADD COLUMN `operator_id` INT DEFAULT NULL COMMENT '手动触发操作员 sys_user.id' AFTER `trigger_type`;

ALTER TABLE `knowledge_document`
  ADD COLUMN `created_by` INT DEFAULT NULL COMMENT '创建人 sys_user.id' AFTER `source`,
  ADD COLUMN `updated_by` INT DEFAULT NULL COMMENT '最后修改人 sys_user.id' AFTER `created_by`;

-- ---- 可选: 加速周聚合查询(数据量大时取消注释执行, 每条仅运行一次) ----
-- CREATE INDEX idx_adc_user_type_time ON agent_decision_chain (user_id, step_type, created_at);
-- CREATE INDEX idx_atq_approver_time  ON agent_task_queue (approved_by, approved_at);
-- CREATE INDEX idx_fa_proc_time       ON farmland_alert (processor_user_id, status, process_time);
-- CREATE INDEX idx_apl_operator_time  ON auto_patrol_log (operator_id, patrol_time);
-- CREATE INDEX idx_kd_creator_time    ON knowledge_document (created_by, created_at);

-- End: 01_migrations\add_score_attribution.sql


-- ============================================================
-- Begin: 01_migrations\add_sensor_soil_fan_buzzer.sql
-- ============================================================
-- Add fields uploaded by the STM32 ZET6 firmware: soil humidity, fan, and buzzer.
ALTER TABLE sensor_reading
    ADD COLUMN soil_humidity INT NULL COMMENT 'soil humidity percent' AFTER humidity;

ALTER TABLE sensor_reading
    ADD COLUMN fan TINYINT NULL COMMENT 'fan status: 0 off, 1 on' AFTER led;

ALTER TABLE sensor_reading
    ADD COLUMN buzzer TINYINT NULL COMMENT 'buzzer status: 0 silent, 1 alarm' AFTER fan;

-- End: 01_migrations\add_sensor_soil_fan_buzzer.sql


-- ============================================================
-- Begin: 01_migrations\uid_friend_tables.sql
-- ============================================================
ALTER TABLE `sys_user`
  ADD COLUMN `uid` varchar(20) DEFAULT NULL UNIQUE;

ALTER TABLE `chat_group`
  ADD COLUMN `group_number` varchar(10) DEFAULT NULL UNIQUE;

CREATE TABLE IF NOT EXISTS `friendship` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `friend_id` int NOT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_friend` (`user_id`, `friend_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_friend_id` (`friend_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS fill_user_uid;
DELIMITER $$
CREATE PROCEDURE fill_user_uid()
BEGIN
  DECLARE done int DEFAULT 0;
  DECLARE uid_val varchar(20);
  DECLARE v_id int;
  DECLARE cur CURSOR FOR SELECT id FROM sys_user WHERE uid IS NULL OR uid = '';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO v_id;
    IF done THEN
      LEAVE read_loop;
    END IF;
    REPEAT
      SET uid_val = LPAD(FLOOR(100000 + RAND() * 900000), 6, '0');
    UNTIL (SELECT COUNT(*) FROM sys_user WHERE uid = uid_val) = 0 END REPEAT;
    UPDATE sys_user SET uid = uid_val WHERE id = v_id;
  END LOOP;
  CLOSE cur;
END$$
DELIMITER ;

CALL fill_user_uid();
DROP PROCEDURE IF EXISTS fill_user_uid;

DROP PROCEDURE IF EXISTS fill_group_number;
DELIMITER $$
CREATE PROCEDURE fill_group_number()
BEGIN
  DECLARE done int DEFAULT 0;
  DECLARE group_num varchar(10);
  DECLARE v_id int;
  DECLARE cur CURSOR FOR SELECT id FROM chat_group WHERE group_number IS NULL OR group_number = '';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO v_id;
    IF done THEN
      LEAVE read_loop;
    END IF;
    REPEAT
      SET group_num = LPAD(FLOOR(1000000000 + RAND() * 9000000000), 10, '0');
    UNTIL (SELECT COUNT(*) FROM chat_group WHERE group_number = group_num) = 0 END REPEAT;
    UPDATE chat_group SET group_number = group_num WHERE id = v_id;
  END LOOP;
  CLOSE cur;
END$$
DELIMITER ;

CALL fill_group_number();
DROP PROCEDURE IF EXISTS fill_group_number;

-- End: 01_migrations\uid_friend_tables.sql
