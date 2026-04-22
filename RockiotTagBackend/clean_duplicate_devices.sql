-- ===========================================
-- 清理重复设备数据脚本
-- ===========================================

-- 1. 查看有多少重复设备
SELECT 
    user_id, 
    device_num, 
    COUNT(*) as duplicate_count,
    MIN(id) as keep_id,
    MAX(id) as max_id
FROM devices
GROUP BY user_id, device_num
HAVING COUNT(*) > 1;

-- 2. 删除重复设备（保留ID最小的那个）
-- 注意：先备份！
CREATE TABLE devices_backup AS SELECT * FROM devices;

-- 删除重复设备，只保留ID最小的
DELETE d1
FROM devices d1
INNER JOIN (
    SELECT MIN(id) as min_id, user_id, device_num
    FROM devices
    GROUP BY user_id, device_num
    HAVING COUNT(*) > 1
) d2 ON d1.user_id = d2.user_id 
    AND d1.device_num = d2.device_num 
    AND d1.id > d2.min_id;

-- 3. 查看删除后的结果
SELECT user_id, device_num, COUNT(*) as count
FROM devices
GROUP BY user_id, device_num
HAVING COUNT(*) > 1;

-- 4. 添加唯一约束（防止未来重复）
-- 注意：如果之前有重复数据，必须先清理才能添加
ALTER TABLE devices 
ADD CONSTRAINT uk_user_device_num 
UNIQUE (user_id, device_num);

-- 如果约束已存在，可以先删除再添加
-- ALTER TABLE devices DROP CONSTRAINT IF EXISTS uk_user_device_num;
-- ALTER TABLE devices ADD CONSTRAINT uk_user_device_num UNIQUE (user_id, device_num);

-- ===========================================
-- 简单版（只保留ID最小的）
-- ===========================================
-- DELETE FROM devices 
-- WHERE id NOT IN (
--     SELECT MIN(id) 
--     FROM devices 
--     GROUP BY user_id, device_num
-- );
