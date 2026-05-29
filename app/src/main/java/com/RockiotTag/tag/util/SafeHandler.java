package com.RockiotTag.tag.util;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.lang.ref.WeakReference;

/**
 * 安全的Handler工具类，防止内存泄漏
 * 
 * 使用WeakReference持有Activity/Fragment的引用，
 * 确保在生命周期结束时能够正确释放资源
 */
public class SafeHandler extends Handler {
    
    private final WeakReference<Callback> callbackRef;
    
    public interface Callback {
        void handleMessage(Message msg);
    }
    
    /**
     * 构造函数
     * 
     * @param callback 消息处理回调
     */
    public SafeHandler(Callback callback) {
        super(Looper.getMainLooper());
        this.callbackRef = new WeakReference<>(callback);
    }
    
    @Override
    public void handleMessage(Message msg) {
        Callback callback = callbackRef.get();
        if (callback != null) {
            callback.handleMessage(msg);
        }
    }
    
    /**
     * 清理资源，移除所有回调和消息
     */
    public void cleanup() {
        removeCallbacksAndMessages(null);
    }
}
