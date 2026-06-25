package com.RockiotTag.tag.util;

import com.RockiotTag.tag.util.ToastHelper;

import android.content.Context;

import java.util.Objects;

/**
 * 安全执行工具类 - 统一异常处理和错误提示
 * 
 * 优势：
 * 1. 消除重复的 try-catch 代码
 * 2. 统一的错误提示格式
 * 3. 支持默认值返回
 * 4. 可选的错误日志记录
 * 5. 增强的空指针防护
 */
public class SafeExecutor {
    
    private static final String TAG = "SafeExecutor";
    
    /**
     * 函数式接口 - 有返回值的操作（兼容 API 21+）
     */
    public interface Supplier<T> {
        T get();
    }
    
    /**
     * 函数式接口 - 无返回值的操作（兼容 API 21+）
     */
    public interface Action {
        void run();
    }
    
    /**
     * 安全执行操作，失败时返回 null
     * 
     * @param action 要执行的操作
     * @param context 上下文（用于显示 Toast）
     * @param errorMessage 错误提示消息
     * @return 操作结果，失败则返回 null
     */
    public static <T> T execute(Supplier<T> action, Context context, String errorMessage) {
        try {
            return action.get();
        } catch (NullPointerException e) {
            android.util.Log.e(TAG, "NullPointerException: " + errorMessage, e);
            if (context != null) {
                ToastHelper.show(context, "数据为空，请重试");
            }
            return null;
        } catch (Exception e) {
            android.util.Log.e(TAG, errorMessage, e);
            if (context != null) {
                String friendlyMessage = ErrorMessageResolver.resolveErrorMessage(context, e);
                ToastHelper.show(context, friendlyMessage);
            }
            return null;
        }
    }
    
    /**
     * 安全执行操作，失败时返回默认值
     * 
     * @param action 要执行的操作
     * @param context 上下文
     * @param errorMessage 错误提示消息
     * @param defaultValue 默认值
     * @return 操作结果，失败则返回默认值
     */
    public static <T> T executeWithDefault(Supplier<T> action, Context context, 
                                          String errorMessage, T defaultValue) {
        try {
            return action.get();
        } catch (NullPointerException e) {
            android.util.Log.e(TAG, "NullPointerException: " + errorMessage, e);
            if (context != null) {
                ToastHelper.show(context, "数据为空，使用默认值");
            }
            return defaultValue;
        } catch (Exception e) {
            android.util.Log.e(TAG, errorMessage, e);
            if (context != null) {
                String friendlyMessage = ErrorMessageResolver.resolveErrorMessage(context, e);
                ToastHelper.show(context, friendlyMessage);
            }
            return defaultValue;
        }
    }
    
    /**
     * 安全执行操作，不显示 Toast（仅记录日志）
     * 
     * @param action 要执行的操作
     * @param errorMessage 错误日志消息
     * @return 操作结果，失败则返回 null
     */
    public static <T> T executeSilent(Supplier<T> action, String errorMessage) {
        try {
            return action.get();
        } catch (NullPointerException e) {
            android.util.Log.e(TAG, "NullPointerException: " + errorMessage, e);
            return null;
        } catch (Exception e) {
            android.util.Log.e(TAG, errorMessage, e);
            return null;
        }
    }
    
    /**
     * 安全执行无返回值的操作
     * 
     * @param action 要执行的操作
     * @param context 上下文
     * @param errorMessage 错误提示消息
     */
    public static void execute(Action action, Context context, String errorMessage) {
        try {
            action.run();
        } catch (NullPointerException e) {
            android.util.Log.e(TAG, "NullPointerException: " + errorMessage, e);
            if (context != null) {
                ToastHelper.show(context, "数据为空，请重试");
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, errorMessage, e);
            if (context != null) {
                String friendlyMessage = ErrorMessageResolver.resolveErrorMessage(context, e);
                ToastHelper.show(context, friendlyMessage);
            }
        }
    }
    
    /**
     * 安全执行无返回值的操作（静默模式）
     * 
     * @param action 要执行的操作
     * @param errorMessage 错误日志消息
     */
    public static void executeSilent(Action action, String errorMessage) {
        try {
            action.run();
        } catch (NullPointerException e) {
            android.util.Log.e(TAG, "NullPointerException: " + errorMessage, e);
        } catch (Exception e) {
            android.util.Log.e(TAG, errorMessage, e);
        }
    }
    
    /**
     * 安全的对象访问 - 防止 NPE
     * 
     * @param obj 可能为 null 的对象
     * @param defaultValue 默认值
     * @return 对象本身或默认值
     */
    public static <T> T getOrDefault(T obj, T defaultValue) {
        return obj != null ? obj : defaultValue;
    }
    
    /**
     * 安全的字符串访问
     * 
     * @param str 可能为 null 的字符串
     * @return 字符串本身或空字符串
     */
    public static String getSafeString(String str) {
        return str != null ? str : "";
    }
    
    /**
     * 安全的整数解析
     * 
     * @param str 可能为 null 或非数字的字符串
     * @param defaultValue 默认值
     * @return 解析后的整数或默认值
     */
    public static int parseSafeInt(String str, int defaultValue) {
        if (str == null || str.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            android.util.Log.w(TAG, "Failed to parse int: " + str);
            return defaultValue;
        }
    }
    
    /**
     * 安全的长整数解析
     * 
     * @param str 可能为 null 或非数字的字符串
     * @param defaultValue 默认值
     * @return 解析后的长整数或默认值
     */
    public static long parseSafeLong(String str, long defaultValue) {
        if (str == null || str.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            android.util.Log.w(TAG, "Failed to parse long: " + str);
            return defaultValue;
        }
    }
    
    /**
     * 安全的双重解析
     * 
     * @param str 可能为 null 或非数字的字符串
     * @param defaultValue 默认值
     * @return 解析后的双精度浮点数或默认值
     */
    public static double parseSafeDouble(String str, double defaultValue) {
        if (str == null || str.isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            android.util.Log.w(TAG, "Failed to parse double: " + str);
            return defaultValue;
        }
    }
}
