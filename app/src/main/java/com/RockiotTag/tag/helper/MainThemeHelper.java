package com.RockiotTag.tag.helper;

import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.view.WindowInsetsControllerCompat;

import com.RockiotTag.tag.R;
import com.RockiotTag.tag.util.LogUtil;

/**
 * MainActivity 主题辅助类
 * 负责封装状态栏、深色/浅色模式、Tab颜色等主题相关逻辑
 */
public class MainThemeHelper {

    private static final String TAG = "MainThemeHelper";

    private final AppCompatActivity activity;
    private final ThemeCallbacks callbacks;

    /**
     * 主题回调接口，由 Activity 实现以提供必要的视图引用
     */
    public interface ThemeCallbacks {
        View getBottomNavigation();
        View getBottomInfo();
        TextView getBatteryLevelText();
        TextView getDeviceAddressText();
        TextView getUpdateTimeText();
        int getCurrentTab();
        com.RockiotTag.tag.MapManager getMapManager();
        com.amap.api.maps.AMap getAMap();
        void notifyFragmentsThemeChanged(boolean isDarkMode);
    }

    public MainThemeHelper(AppCompatActivity activity, ThemeCallbacks callbacks) {
        this.activity = activity;
        this.callbacks = callbacks;
    }

    /**
     * 设置状态栏样式
     */
    public void setupStatusBar() {
        try {
            android.content.SharedPreferences prefs = activity.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);
            boolean isDarkMode = prefs.getBoolean("dark_mode", false);

            LogUtil.d(TAG, "setupStatusBar called, isDarkMode: " + isDarkMode);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                android.view.Window window = activity.getWindow();
                if (window == null) {
                    Log.e(TAG, "Window is null!");
                    return;
                }

                // 使用 WindowInsetsControllerCompat 兼容库来设置状态栏
                WindowInsetsControllerCompat controller =
                    androidx.core.view.ViewCompat.getWindowInsetsController(window.getDecorView());

                if (controller != null) {
                    if (isDarkMode) {
                        // 夜间模式：黑色背景
                        window.setStatusBarColor(Color.parseColor("#000000"));
                        // 设置浅色图标（白色）
                        controller.setAppearanceLightStatusBars(false);
                        LogUtil.d(TAG, "Night mode: black background with white icons");
                    } else {
                        // 日间模式：白色背景
                        window.setStatusBarColor(Color.parseColor("#FFFFFF"));
                        // 设置深色图标（黑色）
                        controller.setAppearanceLightStatusBars(true);
                        LogUtil.d(TAG, "Day mode: white background with dark icons");
                    }
                } else {
                    Log.w(TAG, "WindowInsetsControllerCompat is null, using fallback method");
                    // Fallback: 直接设置颜色
                    if (isDarkMode) {
                        window.setStatusBarColor(Color.parseColor("#000000"));
                    } else {
                        window.setStatusBarColor(Color.parseColor("#FFFFFF"));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up status bar: " + e.getMessage());
        }
    }

    /**
     * 切换深色/浅色模式（重建Activity）
     */
    public void toggleDarkMode() {
        int currentMode = activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkMode = currentMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        android.content.SharedPreferences prefs = activity.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();

        if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
            editor.putBoolean("dark_mode", false);
            android.widget.Toast.makeText(activity, R.string.light_mode, android.widget.Toast.LENGTH_SHORT).show();
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
            editor.putBoolean("dark_mode", true);
            android.widget.Toast.makeText(activity, R.string.dark_mode, android.widget.Toast.LENGTH_SHORT).show();
        }
        editor.apply();

        // 重新启动Activity以应用新的主题
        android.content.Intent intent = activity.getIntent();
        activity.finish();
        activity.startActivity(intent);
    }

    /**
     * 切换深色/浅色模式（不重建Activity）
     */
    public void toggleDarkMode(boolean isDarkMode) {
        // 保存设置
        activity.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("dark_mode", isDarkMode).apply();

        // 手动应用深色/浅色模式，不重建Activity
        applyDarkMode(isDarkMode);
    }

    /**
     * 手动应用深色/浅色模式，不重建Activity
     */
    public void applyDarkMode(boolean isDarkMode) {
        int bgColor = isDarkMode ?
            activity.getResources().getColor(R.color.dark_background, null) :
            activity.getResources().getColor(R.color.background, null);
        int surfaceColor = isDarkMode ?
            activity.getResources().getColor(R.color.dark_surface, null) :
            activity.getResources().getColor(R.color.surface, null);
        int onSurfaceColor = isDarkMode ?
            activity.getResources().getColor(R.color.dark_onSurface, null) :
            activity.getResources().getColor(R.color.onSurface, null);
        int topBarColor = isDarkMode ?
            activity.getResources().getColor(R.color.dark_top_bar_background, null) :
            activity.getResources().getColor(R.color.top_bar_background, null);
        int cardColor = isDarkMode ?
            activity.getResources().getColor(R.color.dark_card_background, null) :
            activity.getResources().getColor(R.color.card_background, null);
        int textSecColor = isDarkMode ?
            activity.getResources().getColor(R.color.dark_text_secondary, null) :
            activity.getResources().getColor(R.color.text_secondary, null);

        // 选中/未选中Tab颜色
        int selectedColor = activity.getResources().getColor(R.color.purple_500, null);
        int unselectedColor = isDarkMode ?
            activity.getResources().getColor(R.color.dark_text_secondary, null) :
            activity.getResources().getColor(R.color.text_secondary, null);

        // 应用到根视图
        View rootView = activity.findViewById(android.R.id.content);
        if (rootView != null) rootView.setBackgroundColor(bgColor);

        // 应用到底部导航栏背景
        if (callbacks.getBottomNavigation() != null) {
            callbacks.getBottomNavigation().setBackgroundColor(topBarColor);
        }

        // 应用到导航栏Tab文字和图标颜色
        updateTabColors(selectedColor, unselectedColor);

        // 应用到底部信息卡片
        if (callbacks.getBottomInfo() != null) {
            try {
                ((CardView) callbacks.getBottomInfo()).setCardBackgroundColor(cardColor);
            } catch (Exception e) {
                // 忽略类型转换异常
            }
        }

        // 应用到信息卡片内的文字
        if (callbacks.getBatteryLevelText() != null) callbacks.getBatteryLevelText().setTextColor(onSurfaceColor);
        if (callbacks.getDeviceAddressText() != null) callbacks.getDeviceAddressText().setTextColor(onSurfaceColor);
        if (callbacks.getUpdateTimeText() != null) callbacks.getUpdateTimeText().setTextColor(onSurfaceColor);

        // 更新状态栏
        if (isDarkMode) {
            activity.getWindow().setStatusBarColor(activity.getResources().getColor(R.color.dark_surface, null));
            WindowInsetsControllerCompat controller =
                androidx.core.view.ViewCompat.getWindowInsetsController(activity.getWindow().getDecorView());
            if (controller != null) {
                controller.setAppearanceLightStatusBars(false); // 白色图标
            }
        } else {
            activity.getWindow().setStatusBarColor(activity.getResources().getColor(R.color.top_bar_background, null));
            WindowInsetsControllerCompat controller =
                androidx.core.view.ViewCompat.getWindowInsetsController(activity.getWindow().getDecorView());
            if (controller != null) {
                controller.setAppearanceLightStatusBars(true); // 黑色图标
            }
        }

        // 深色模式：设置地图样式（添加完善的异常处理）
        try {
            if (callbacks.getMapManager() != null && callbacks.getMapManager().isAmap()) {
                if (callbacks.getAMap() != null) {
                    if (isDarkMode) {
                        // 使用导航地图样式（深色风格，蓝黑配色）
                        callbacks.getAMap().setMapType(com.amap.api.maps.AMap.MAP_TYPE_NIGHT);
                    } else {
                        callbacks.getAMap().setMapType(com.amap.api.maps.AMap.MAP_TYPE_NORMAL);
                    }
                }
            } else if (callbacks.getMapManager() != null && callbacks.getMapManager().getGoogleMap() != null) {
                com.google.android.gms.maps.GoogleMap gMap = callbacks.getMapManager().getGoogleMap();
                if (isDarkMode) {
                    try {
                        gMap.setMapStyle(com.google.android.gms.maps.model.MapStyleOptions.loadRawResourceStyle(activity, R.raw.map_style_night));
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to apply dark map style: " + e.getMessage());
                    }
                } else {
                    gMap.setMapStyle(null);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying dark map style: " + e.getMessage());
        }

        // 通知Fragment更新
        callbacks.notifyFragmentsThemeChanged(isDarkMode);
    }

    /**
     * 更新导航栏Tab文字和图标颜色
     */
    public void updateTabColors(int selectedColor, int unselectedColor) {
        // 更新当前选中的Tab
        int[][] tabPairs = {
            {R.id.tab_home_icon, R.id.tab_home_text},
            {R.id.tab_list_icon, R.id.tab_list_text},
            {R.id.tab_track_icon, R.id.tab_track_text},
            {R.id.tab_profile_icon, R.id.tab_profile_text}
        };

        for (int i = 0; i < tabPairs.length; i++) {
            ImageView icon = activity.findViewById(tabPairs[i][0]);
            TextView text = activity.findViewById(tabPairs[i][1]);
            boolean isSelected = (i == callbacks.getCurrentTab());
            int color = isSelected ? selectedColor : unselectedColor;
            if (icon != null) icon.setColorFilter(color);
            if (text != null) {
                text.setTextColor(color);
                text.setSelected(isSelected);
            }
        }
    }
}
