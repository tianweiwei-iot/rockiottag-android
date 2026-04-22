-- 数据库迁移脚本
-- 添加设备历史记录表和设备地址字段

USE rockiot_tag;

-- 1. 创建设备历史记录表
CREATE TABLE IF NOT EXISTS device_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    device_num VARCHAR(100) NOT NULL,
    latitude DOUBLE,
    longitude DOUBLE,
    battery INT,
    timestamp BIGINT,
    address VARCHAR(500),
    created_at DATETIME,
    INDEX idx_user_device (user_id, device_num),
    INDEX idx_timestamp (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 为devices表添加address字段
ALTER TABLE devices ADD COLUMN IF NOT EXISTS address VARCHAR(500);

-- 3. 查看表结构
DESCRIBE device_history;
DESCRIBE devices;

-- 4. 查询设备历史记录数量
SELECT COUNT(*) AS '历史记录总数' FROM device_history;

-- 5. 查询每个设备的历史记录数量
SELECT 
    device_num AS '设备号',
    COUNT(*) AS '历史记录数量'
FROM device_history
GROUP BY device_num;
