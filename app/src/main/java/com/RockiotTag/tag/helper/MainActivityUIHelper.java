package com.RockiotTag.tag.helper;

import android.content.Context;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.RockiotTag.tag.Device;
import com.RockiotTag.tag.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * MainActivity UI 助手
 * 职责：封装 UI 更新和格式化逻辑
 */
public class MainActivityUIHelper {
    
    /**
     * 格式化设备最后 seen 时间
     */
    public static String formatLastSeen(long lastSeen) {
        if (lastSeen <= 0) {
            return "未知";
        }
        
        long now = System.currentTimeMillis();
        long diff = now - lastSeen;
        
        if (diff < 60000) { // 小于1分钟
            return "刚刚";
        } else if (diff < 3600000) { // 小于1小时
            return (diff / 60000) + "分钟前";
        } else if (diff < 86400000) { // 小于24小时
            return (diff / 3600000) + "小时前";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            return sdf.format(new Date(lastSeen));
        }
    }
    
    /**
     * 更新设备卡片 UI
     */
    public static void updateDeviceCard(View cardView, Device device) {
        if (cardView == null || device == null) {
            return;
        }
        
        // 注意：实际 ID 需要根据 MainActivity 的布局文件调整
        // 这里提供通用实现，具体 ID 需要在 TrackActivity 中直接使用
    }
    
    /**
     * 显示加载状态
     */
    public static void showLoading(ProgressBar progressBar, TextView loadingText) {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        if (loadingText != null) {
            loadingText.setVisibility(View.VISIBLE);
            loadingText.setText(R.string.loading_data);
        }
    }
    
    /**
     * 隐藏加载状态
     */
    public static void hideLoading(ProgressBar progressBar, TextView loadingText) {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        if (loadingText != null) {
            loadingText.setVisibility(View.GONE);
        }
    }
    
    /**
     * 显示空状态
     */
    public static void showEmptyState(Context context, TextView emptyText) {
        if (emptyText != null) {
            emptyText.setVisibility(View.VISIBLE);
            emptyText.setText(R.string.no_device_data);
        }
    }
    
    /**
     * 隐藏空状态
     */
    public static void hideEmptyState(TextView emptyText) {
        if (emptyText != null) {
            emptyText.setVisibility(View.GONE);
        }
    }
}
