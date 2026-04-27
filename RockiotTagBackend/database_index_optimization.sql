-- ========================================
-- RockiotTag 数据库索引优化脚本
-- 执行时间:2026-04-27
-- 目的:提升查询性能,支持 1000 台设备高并发
-- ========================================

USE rockiot_tag_hswl;

-- ========================================
-- 创建索引(如果已存在会报错,可忽略)
-- ========================================

-- 1. Device 表索引
-- 用途:快速查询用户的设备列表
CREATE INDEX idx_user_device ON devices(user_id, device_num);

-- 用途:根据设备号快速查找设备
CREATE INDEX idx_device_num ON devices(device_num);

-- 用途:按更新时间排序查询
CREATE INDEX idx_updated_at ON devices(updated_at);

-- 2. LocationRecord 表索引
-- 用途:查询指定用户、设备在某个时间段的位置记录(最常用)
CREATE INDEX idx_user_device_time ON location_records(user_id, device_num, timestamp);

-- 用途:查询指定设备的时间段位置记录
CREATE INDEX idx_device_time ON location_records(device_num, timestamp);

-- 用途:按时间戳范围查询
CREATE INDEX idx_timestamp ON location_records(timestamp);

-- 3. DeviceHistory 表索引
-- 用途:查询设备的历史轨迹
CREATE INDEX idx_history_device_time ON device_history(device_num, timestamp);

-- 用途:查询用户设备的历史记录
CREATE INDEX idx_history_user_device ON device_history(user_id, device_num);

-- ========================================
-- 验证索引创建
-- ========================================

-- 查看 Device 表索引
SHOW INDEX FROM devices;

-- 查看 LocationRecord 表索引
SHOW INDEX FROM location_records;

-- 查看 DeviceHistory 表索引
SHOW INDEX FROM device_history;

-- ========================================
-- 完成提示
-- ========================================
SELECT '✅ 数据库索引优化完成！' AS status;
