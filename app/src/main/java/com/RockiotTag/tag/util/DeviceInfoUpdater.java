package com.RockiotTag.tag.util;

import android.widget.TextView;

import com.RockiotTag.tag.R;

/**
 * 设备信息更新工具类
 * 职责：统一处理设备信息的UI更新逻辑
 */
public class DeviceInfoUpdater {
    
    /**
     * 更新电池电量显示
     */
    public static void updateBatteryLevel(TextView batteryText, int battery, android.content.Context context) {
        if (batteryText == null) return;
        
        if (battery > 0) {
            batteryText.setText(context.getString(R.string.battery_level_value, String.valueOf(battery)));
        } else if (battery == 0) {
            batteryText.setText(context.getString(R.string.battery_level_zero));
        } else {
            batteryText.setText(context.getString(R.string.battery_level_unknown));
        }
    }
    
    /**
     * 更新地址显示
     */
    public static void updateAddress(TextView addressText, String address, android.content.Context context) {
        if (addressText == null) return;
        
        if (address == null || address.isEmpty() || "not_reported".equals(address)) {
            addressText.setText(context.getString(R.string.position_empty));
        } else {
            addressText.setText(context.getString(R.string.position_with_address, address));
        }
    }
    
    /**
     * 更新时间显示 - 使用智能格式化
     */
    public static void updateTime(TextView timeText, long timestamp, android.content.Context context) {
        if (timeText == null) return;
        
        if (timestamp <= 0) {
            timeText.setText(context.getString(R.string.last_update_empty));
        } else {
            timeText.setText(context.getString(R.string.last_update_with_time, 
                TimeFormatter.formatSmartTime(context, timestamp)));
        }
    }
    
    /**
     * 更新时间显示（字符串格式）
     */
    public static void updateTime(TextView timeText, String timeStr, android.content.Context context) {
        if (timeText == null) return;
        
        if ("not_reported".equals(timeStr)) {
            timeText.setText(context.getString(R.string.last_update_empty));
        } else {
            try {
                long timestamp = Long.parseLong(timeStr);
                updateTime(timeText, timestamp, context);
            } catch (NumberFormatException e) {
                timeText.setText(context.getString(R.string.last_update_empty));
            }
        }
    }
    
    /**
     * 重置为默认值
     */
    public static void resetToDefault(TextView batteryText, TextView addressText, TextView timeText, 
                                     android.content.Context context) {
        if (addressText != null) {
            addressText.setText(context.getString(R.string.position_empty));
        }
        if (timeText != null) {
            timeText.setText(context.getString(R.string.last_update_empty));
        }
    }
}
