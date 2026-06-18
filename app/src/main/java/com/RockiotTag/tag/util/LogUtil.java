package com.RockiotTag.tag.util;

import android.util.Log;

/**
 * 日志工具类
 * Release 构建自动关闭 Log.d/v/i，保留 Log.e/w 用于错误监控
 * <p>
 * 使用方法：将 Log.d(TAG, msg) 替换为 LogUtil.d(TAG, msg)
 */
public final class LogUtil {

    // DEBUG 模式标志
    // Debug 构建开启日志，Release 构建关闭日志
    // 通过 Application 的 debuggable 标志判断
    private static boolean enableLog = true;

    private LogUtil() {
        // 私有构造，防止实例化
    }

    /**
     * 初始化日志开关
     * 在 Application.onCreate() 中调用
     *
     * @param debug 是否为 Debug 模式
     */
    public static void init(boolean debug) {
        enableLog = debug;
    }

    /**
     * Verbose 级别日志（Release 关闭）
     */
    public static void v(String tag, String msg) {
        if (enableLog) {
            Log.v(tag, msg);
        }
    }

    /**
     * Debug 级别日志（Release 关闭）
     */
    public static void d(String tag, String msg) {
        if (enableLog) {
            Log.d(tag, msg);
        }
    }

    /**
     * Info 级别日志（Release 关闭）
     */
    public static void i(String tag, String msg) {
        if (enableLog) {
            Log.i(tag, msg);
        }
    }

    /**
     * Warn 级别日志（Release 保留）
     */
    public static void w(String tag, String msg) {
        Log.w(tag, msg);
    }

    /**
     * Error 级别日志（Release 保留）
     */
    public static void e(String tag, String msg) {
        Log.e(tag, msg);
    }

    /**
     * Error 级别日志（Release 保留）
     */
    public static void e(String tag, String msg, Throwable tr) {
        Log.e(tag, msg, tr);
    }

    /**
     * Warn 级别日志（Release 保留）
     */
    public static void w(String tag, String msg, Throwable tr) {
        Log.w(tag, msg, tr);
    }

    /**
     * Debug 级别日志（Release 关闭）
     */
    public static void d(String tag, String msg, Throwable tr) {
        if (enableLog) {
            Log.d(tag, msg, tr);
        }
    }

    /**
     * Info 级别日志（Release 关闭）
     */
    public static void i(String tag, String msg, Throwable tr) {
        if (enableLog) {
            Log.i(tag, msg, tr);
        }
    }
}
