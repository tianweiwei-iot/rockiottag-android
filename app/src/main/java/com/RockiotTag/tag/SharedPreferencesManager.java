package com.RockiotTag.tag;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SharedPreferences管理器 - 简化版
 * 
 * 注意：根据新架构，Android端不需要认证，此文件仅用于配置存储
 */
public class SharedPreferencesManager {
    
    /**
     * 加载认证信息（空实现，保留以兼容旧代码）
     * @deprecated Android端不再需要认证
     */
    @Deprecated
    public static void loadAuth(Context context) {
        // 不再需要加载认证信息，空实现以保持兼容
    }
    
    /**
     * 保存选中的设备ID
     */
    public static void saveSelectedDeviceId(Context context, String deviceId) {
        SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        prefs.edit().putString("selected_device_id", deviceId).apply();
    }
    
    /**
     * 获取选中的设备ID
     */
    public static String getSelectedDeviceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        return prefs.getString("selected_device_id", "");
    }
}
