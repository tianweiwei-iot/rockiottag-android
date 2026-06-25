package com.RockiotTag.tag;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.RockiotTag.tag.BuildConfig;
import com.RockiotTag.tag.util.LogUtil;

import com.RockiotTag.tag.util.GlobalExceptionHandler;
import com.RockiotTag.tag.util.LanguageIndicatorHelper;
import com.RockiotTag.tag.util.MemoryLeakDetector;
import com.RockiotTag.tag.util.ToastHelper;

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
        
        // 0. 初始化日志工具（Release 构建关闭 Log.d/v/i）
        LogUtil.init(BuildConfig.DEBUG);
        
        // 1. 注册全局异常处理器
        Thread.setDefaultUncaughtExceptionHandler(new GlobalExceptionHandler(this));
        LogUtil.d(TAG, "Global exception handler registered");
        
        // 2. 注册内存泄漏检测器（仅调试模式）
        if (BuildConfig.DEBUG) {
            MemoryLeakDetector.getInstance().register(this);
            LogUtil.d(TAG, "Memory leak detector registered (DEBUG mode)");
        }
        
        // 3. 延迟初始化非关键组件（优化启动速度）
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            LogUtil.d(TAG, "Delayed initialization started");
            // 可以在这里初始化分析工具、崩溃报告等
        }, 2000);
        
        LogUtil.d(TAG, "Application initialized successfully");

        registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }

            @Override
            public void onActivityStarted(Activity activity) { }

            @Override
            public void onActivityResumed(Activity activity) {
                try {
                    LanguageIndicatorHelper.bind(activity);
                } catch (Throwable t) {
                    Log.e(TAG, "LanguageIndicatorHelper.bind failed", t);
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
                ToastHelper.cancel();
            }

            @Override
            public void onActivityStopped(Activity activity) { }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }

            @Override
            public void onActivityDestroyed(Activity activity) { }
        });
    }
    
}
