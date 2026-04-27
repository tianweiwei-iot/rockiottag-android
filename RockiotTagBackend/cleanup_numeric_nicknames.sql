-- ========================================
-- 清理供应商自动生成的数字昵称
-- 数据库: rockiot_tag_hswl
-- 执行日期: 2026-04-26
-- ========================================

USE rockiot_tag_hswl;

-- 1. 查看将被影响的设备数量
SELECT COUNT(*) AS affected_devices
FROM devices
WHERE nick_name REGEXP '^0+[0-9]+$';

-- 2. 查看将被影响的具体设备（执行前确认）
SELECT id, device_num, nick_name, created_at
FROM devices
WHERE nick_name REGEXP '^0+[0-9]+$'
ORDER BY created_at DESC;

-- 3. 执行批量更新，将"000000"开头的数字昵称设置为NULL
UPDATE devices
SET nick_name = NULL
WHERE nick_name REGEXP '^0+[0-9]+$';

-- 4. 验证更新结果
SELECT COUNT(*) AS remaining_numeric_nicknames
FROM devices
WHERE nick_name REGEXP '^0+[0-9]+$';

-- 5. 查看更新后的设备列表（确认NULL值）
SELECT id, device_num, nick_name, updated_at
FROM devices
WHERE nick_name IS NULL
ORDER BY updated_at DESC
LIMIT 20;

-- ========================================
-- 说明：
-- 1. REGEXP '^0+[0-9]+$' 匹配以0开头且全部为数字的昵称
--    例如：000000430825、000000430830 等
-- 2. 执行前建议先运行第1、2步确认影响范围
-- 3. 执行第3步进行实际更新
-- 4. 执行第4、5步验证结果
-- ========================================
