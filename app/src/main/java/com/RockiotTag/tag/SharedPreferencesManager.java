package com.RockiotTag.tag;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SharedPreferences管理器
 * 注意：根据新架构，Android端不需要认证，此文件仅保留用于其他配置存储
 */
public class SharedPreferencesManager {
    private static final String PREF_NAME = "RockiotTagPrefs";
    
    /**
     * 保存认证信息（已废弃，保留以兼容旧代码）
     * @deprecated Android端不再需要认证
     */
    @Deprecated
    public static void saveAuth(Context context, int userId, String token, String userName, String cid) {
        // 不再需要保存认证信息
    }
    
    /**
     * 加载认证信息（已废弃，保留以兼容旧代码）
     * @deprecated Android端不再需要认证
     */
    @Deprecated
    public static void loadAuth(Context context) {
        // 不再需要加载认证信息
    }
    
    /**
     * 清除认证信息（已废弃，保留以兼容旧代码）
     * @deprecated Android端不再需要认证
     */
    @Deprecated
    public static void clearAuth(Context context) {
        // 不再需要清除认证信息
    }
    
    public static int getUserId(Context context) {
        return 0; // 不再使用
    }
    
    public static String getToken(Context context) {
        return null; // 不再使用
    }
    
    public static String getUserName(Context context) {
        return null; // 不再使用
    }
    
    public static boolean isAuthenticated(Context context) {
        return true; // 始终返回true，因为不需要认证
    }
}
