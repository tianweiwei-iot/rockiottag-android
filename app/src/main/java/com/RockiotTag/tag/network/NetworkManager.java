package com.RockiotTag.tag.network;

import android.content.Context;
import android.util.Log;
import com.RockiotTag.tag.util.LogUtil;

import com.RockiotTag.tag.NewApiService;
import com.RockiotTag.tag.util.UserFriendlyError;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * 统一网络管理器
 * 封装所有网络请求，提供统一的错误处理和重试机制
 */
public class NetworkManager {
    private static final String TAG = "NetworkManager";
    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_DELAY_MS = 1000; // 1秒
    
    private static NetworkManager instance;
    private final Context context;
    
    public interface NetworkCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }
    
    private NetworkManager(Context context) {
        this.context = context.getApplicationContext();
    }
    
    public static synchronized NetworkManager getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkManager(context);
        }
        return instance;
    }
    
    /**
     * 执行网络请求（带重试机制）
     * @param apiCall API调用函数
     * @param callback 回调
     * @param <T> 返回类型
     */
    public <T> void executeWithRetry(ApiCall<T> apiCall, NetworkCallback<T> callback) {
        executeWithRetry(apiCall, callback, MAX_RETRY_COUNT);
    }
    
    private <T> void executeWithRetry(ApiCall<T> apiCall, NetworkCallback<T> callback, int retryCount) {
        new Thread(() -> {
            Exception lastException = null;
            
            for (int attempt = 1; attempt <= retryCount; attempt++) {
                try {
                    LogUtil.d(TAG, "API call attempt " + attempt + "/" + retryCount);
                    T result = apiCall.call();
                    
                    // 在主线程回调
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        callback.onSuccess(result);
                    });
                    return;
                    
                } catch (SocketTimeoutException e) {
                    lastException = e;
                    Log.w(TAG, "Attempt " + attempt + " failed: Timeout");
                    
                    if (attempt < retryCount) {
                        try {
                            Thread.sleep(RETRY_DELAY_MS * attempt); // 递增延迟
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    
                } catch (UnknownHostException | ConnectException e) {
                    lastException = e;
                    Log.e(TAG, "Attempt " + attempt + " failed: Network error - " + e.getMessage());
                    
                    // 网络错误不重试，直接返回
                    break;
                    
                } catch (IOException e) {
                    lastException = e;
                    Log.e(TAG, "Attempt " + attempt + " failed: IO error - " + e.getMessage());
                    
                    if (attempt < retryCount) {
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    
                } catch (Exception e) {
                    lastException = e;
                    Log.e(TAG, "Attempt " + attempt + " failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    break; // 其他异常不重试
                }
            }
            
            // 所有重试都失败
            if (lastException != null) {
                String errorMessage = UserFriendlyError.getUserMessage(lastException.getMessage());
                Log.e(TAG, "All attempts failed: " + errorMessage);
                
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    callback.onError(errorMessage);
                });
            }
        }).start();
    }
    
    /**
     * 获取设备信息（封装常用API）
     */
    public void getDeviceInfo(String deviceNum, NetworkCallback<NewApiService.DeviceInfo> callback) {
        executeWithRetry(() -> {
            return NewApiService.getInstance().getDeviceLatest(com.RockiotTag.tag.ApiConfig.getMyServerUrl(deviceNum), deviceNum);
        }, callback);
    }
    
    /**
     * API调用接口
     */
    public interface ApiCall<T> {
        T call() throws Exception;
    }
}
