package com.RockiotTag.tag.util;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.RockiotTag.tag.util.LogUtil;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.RockiotTag.tag.model.Resource;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.Callable;

/**
 * 可重试任务执行器（增强版）
 * 
 * 提供自动重试机制，支持指数退避策略
 * 增强功能：
 * 1. 区分可重试和不可重试的异常
 * 2. 更智能的退避策略
 * 3. 重试进度回调
 */
public class RetryableTask {
    
    private static final String TAG = "RetryableTask";
    private static final int DEFAULT_MAX_RETRY = 3;
    private static final long DEFAULT_RETRY_DELAY = 2000; // 2秒
    private static final long MAX_RETRY_DELAY = 30000; // 最大30秒
    
    /**
     * 函数式接口 - 有返回值的操作
     */
    public interface Supplier<T> {
        T get() throws Exception;
    }
    
    /**
     * 重试进度回调
     */
    public interface RetryProgressListener {
        void onRetry(int attempt, int maxRetry, Exception lastError);
    }
    
    /**
     * 执行可重试任务
     * 
     * @param task 要执行的任务
     * @param <T> 返回类型
     * @return LiveData<Resource<T>> 可观察的结果
     */
    public static <T> LiveData<Resource<T>> execute(Supplier<T> task) {
        return execute(task, DEFAULT_MAX_RETRY, null);
    }
    
    /**
     * 执行可重试任务（自定义重试次数）
     * 
     * @param task 要执行的任务
     * @param maxRetry 最大重试次数
     * @param <T> 返回类型
     * @return LiveData<Resource<T>> 可观察的结果
     */
    public static <T> LiveData<Resource<T>> execute(Supplier<T> task, int maxRetry) {
        return execute(task, maxRetry, null);
    }
    
    /**
     * 执行可重试任务（带进度监听）
     * 
     * @param task 要执行的任务
     * @param maxRetry 最大重试次数
     * @param progressListener 重试进度监听器
     * @param <T> 返回类型
     * @return LiveData<Resource<T>> 可观察的结果
     */
    public static <T> LiveData<Resource<T>> execute(Supplier<T> task, int maxRetry, 
                                                     RetryProgressListener progressListener) {
        MutableLiveData<Resource<T>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        
        new Thread(() -> {
            Exception lastException = null;
            
            for (int i = 0; i <= maxRetry; i++) {
                try {
                    LogUtil.d(TAG, "Executing task, attempt " + (i + 1) + "/" + (maxRetry + 1));
                    
                    T data = task.get();
                    
                    // 成功，返回结果
                    T finalData = data;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        result.setValue(Resource.success(finalData));
                    });
                    return;
                    
                } catch (Exception e) {
                    lastException = e;
                    Log.w(TAG, "Attempt " + (i + 1) + " failed: " + e.getClass().getSimpleName() + 
                          " - " + e.getMessage());
                    
                    // 通知进度监听器
                    if (progressListener != null) {
                        int finalI = i;
                        Exception finalE = e;
                        new Handler(Looper.getMainLooper()).post(() -> {
                            progressListener.onRetry(finalI + 1, maxRetry + 1, finalE);
                        });
                    }
                    
                    // 检查是否为不可重试的异常
                    if (!isRetryableException(e)) {
                        Log.e(TAG, "Non-retryable exception, stopping retries: " + e.getMessage());
                        break;
                    }
                    
                    if (i < maxRetry) {
                        // 还有重试机会，等待后重试（指数退避 + 抖动）
                        long delay = calculateRetryDelay(i);
                        try {
                            LogUtil.d(TAG, "Retrying after " + delay + "ms (attempt " + (i + 1) + ")");
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            Log.w(TAG, "Retry interrupted");
                            break;
                        }
                    }
                }
            }
            
            // 所有重试都失败
            Exception finalException = lastException;
            new Handler(Looper.getMainLooper()).post(() -> {
                String errorMsg = buildErrorMessage(finalException, maxRetry);
                result.setValue(Resource.error(errorMsg, null));
            });
            
        }).start();
        
        return result;
    }
    
    /**
     * 判断异常是否可重试
     */
    private static boolean isRetryableException(Exception e) {
        // 网络超时、连接错误等可以重试
        if (e instanceof SocketTimeoutException) {
            return true;
        }
        if (e instanceof UnknownHostException) {
            return true;
        }
        if (e instanceof java.io.IOException) {
            return true;
        }
        
        // 检查消息中是否包含可重试的错误
        String message = e.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("timeout") ||
                   lowerMessage.contains("network") ||
                   lowerMessage.contains("connection") ||
                   lowerMessage.contains("502") ||
                   lowerMessage.contains("503") ||
                   lowerMessage.contains("504");
        }
        
        // 默认不重试未知异常
        return false;
    }
    
    /**
     * 计算重试延迟（指数退避 + 随机抖动）
     */
    private static long calculateRetryDelay(int attempt) {
        // 指数退避：2s, 4s, 8s, 16s, 30s...
        long exponentialDelay = Math.min(
            DEFAULT_RETRY_DELAY * (long) Math.pow(2, attempt),
            MAX_RETRY_DELAY
        );
        
        // 添加随机抖动（±20%），避免多个请求同时重试
        double jitter = 0.8 + (Math.random() * 0.4); // 0.8 ~ 1.2
        return (long) (exponentialDelay * jitter);
    }
    
    /**
     * 构建友好的错误消息
     */
    private static String buildErrorMessage(Exception e, int maxRetry) {
        if (e == null) {
            return "操作失败，已重试" + maxRetry + "次";
        }
        
        String simpleName = e.getClass().getSimpleName();
        String message = e.getMessage();
        
        // 根据异常类型返回友好消息
        if (e instanceof SocketTimeoutException) {
            return "网络连接超时，已重试" + maxRetry + "次，请检查网络后重试";
        }
        if (e instanceof UnknownHostException) {
            return "无法连接到服务器，请检查网络连接";
        }
        if (e instanceof java.io.IOException) {
            return "网络请求失败，已重试" + maxRetry + "次";
        }
        
        // 通用错误消息
        if (message != null && !message.isEmpty()) {
            return "操作失败: " + message + "（已重试" + maxRetry + "次）";
        }
        
        return "操作失败（" + simpleName + "），已重试" + maxRetry + "次";
    }
}
