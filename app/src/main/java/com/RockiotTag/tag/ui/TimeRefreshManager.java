package com.RockiotTag.tag.ui;

import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.RockiotTag.tag.util.TimeFormatter;

import java.lang.ref.WeakReference;

/**
 * 时间刷新管理器
 * 自动更新时间显示，每分钟刷新一次
 */
public class TimeRefreshManager {
    
    private static class SafeHandler extends Handler {
        private final WeakReference<TextView> textViewRef;

        public SafeHandler(Looper looper, TextView textView) {
            super(looper);
            this.textViewRef = new WeakReference<>(textView);
        }
    }

    private SafeHandler timeUpdateHandler;
    private Runnable timeUpdateRunnable;
    private WeakReference<TextView> textViewRef; // 保存 TextView 的弱引用
    private long lastTimestamp;
    
    /**
     * 启动时间自动刷新（每分钟更新一次）
     * @param textView 显示时间的TextView
     * @param timestamp 时间戳
     */
    public void startTimeAutoRefresh(TextView textView, long timestamp) {
        // 先停止之前的刷新，防止多个任务同时运行
        stopTimeAutoRefresh();
        
        this.lastTimestamp = timestamp;
        this.textViewRef = new WeakReference<>(textView); // 保存弱引用
        
        // 立即更新一次
        updateDisplay(textView);
        
        // 创建安全的 Handler
        timeUpdateHandler = new SafeHandler(Looper.getMainLooper(), textView);
        
        // 每分钟更新一次
        timeUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                TextView tv = timeUpdateHandler != null ? timeUpdateHandler.textViewRef.get() : null;
                if (tv != null) {
                    String displayText = "更新：" + TimeFormatter.formatSmartTime(tv.getContext(), lastTimestamp);
                    tv.setText(displayText);
                    timeUpdateHandler.postDelayed(this, 60000); // 1分钟
                }
            }
        };
        
        timeUpdateHandler.post(timeUpdateRunnable);
    }
    
    /**
     * 更新显示
     */
    private void updateDisplay(TextView textView) {
        if (textView != null) {
            String displayText = "更新：" + TimeFormatter.formatSmartTime(textView.getContext(), lastTimestamp);
            textView.setText(displayText);
        }
    }
    
    /**
     * 停止自动刷新
     */
    public void stopTimeAutoRefresh() {
        if (timeUpdateRunnable != null && timeUpdateHandler != null) {
            timeUpdateHandler.removeCallbacks(timeUpdateRunnable);
            timeUpdateRunnable = null;
        }
        if (timeUpdateHandler != null) {
            timeUpdateHandler.removeCallbacksAndMessages(null);
            timeUpdateHandler = null;
        }
    }
    
    /**
     * 更新时间戳（当位置更新时调用）
     * @param textView 显示时间的TextView
     * @param newTimestamp 新的时间戳
     */
    public void updateTimestamp(TextView textView, long newTimestamp) {
        this.lastTimestamp = newTimestamp;
        this.textViewRef = new WeakReference<>(textView); // 更新弱引用
        updateDisplay(textView);
    }
}
