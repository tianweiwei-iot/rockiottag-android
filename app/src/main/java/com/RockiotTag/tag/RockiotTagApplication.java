package com.RockiotTag.tag;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.RockiotTag.tag.util.GlobalExceptionHandler;
import com.RockiotTag.tag.util.MemoryLeakDetector;

/**
 * 应用全局Application类
 * 
 * 用于初始化全局配置和注册异常处理器
 */
public class RockiotTagApplication extends Application {
    
    private static final String TAG = "RockiotTagApplication";
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // 1. 注册全局异常处理器
        Thread.setDefaultUncaughtExceptionHandler(new GlobalExceptionHandler(this));
        Log.d(TAG, "Global exception handler registered");
        
        // 2. 注册内存泄漏检测器（仅调试模式）
        if (isDebugMode()) {
            MemoryLeakDetector.getInstance().register(this);
            Log.d(TAG, "Memory leak detector registered (DEBUG mode)");
        }
        
        // 3. 延迟初始化非关键组件（优化启动速度）
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.d(TAG, "Delayed initialization started");
            // 可以在这里初始化分析工具、崩溃报告等
        }, 2000);
        
        Log.d(TAG, "Application initialized successfully");
    }
    
    /**
     * 检查是否为调试模式
     * 避免直接使用 BuildConfig，防止编译问题
     */
    private boolean isDebugMode() {
        try {
            // 方法1: 通过反射检查
            Class<?> buildConfigClass = Class.forName("com.RockiotTag.tag.BuildConfig");
            Object debugField = buildConfigClass.getField("DEBUG").get(null);
            return (Boolean) debugField;
        } catch (Exception e) {
            // 如果反射失败，使用替代方案
            Log.w(TAG, "Cannot access BuildConfig.DEBUG via reflection: " + e.getMessage());
            // 方法2: 检查应用是否可调试
            return (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        }
    }
}
