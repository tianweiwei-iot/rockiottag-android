package com.RockiotTag.tag.util;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.RockiotTag.tag.R;

/**
 * 状态栏统一工具类
 * 使用 WindowInsetsControllerCompat 新 API，替代已废弃的 SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
 * 深色模式：深色背景 + 白色图标
 * 浅色模式：浅色背景 + 黑色图标
 */
public class StatusBarHelper {

    /**
     * 根据当前深色模式设置自动设置状态栏
     */
    public static void setupStatusBar(Activity activity) {
        boolean isDarkMode = activity.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getBoolean("dark_mode", false);
        setupStatusBar(activity, isDarkMode);
    }

    /**
     * 设置状态栏颜色和图标颜色
     * @param activity Activity
     * @param isDarkMode 是否深色模式
     */
    public static void setupStatusBar(Activity activity, boolean isDarkMode) {
        if (activity == null || activity.getWindow() == null) return;

        WindowInsetsControllerCompat controller =
                ViewCompat.getWindowInsetsController(activity.getWindow().getDecorView());

        if (isDarkMode) {
            activity.getWindow().setStatusBarColor(
                    activity.getResources().getColor(R.color.dark_surface, null));
            if (controller != null) {
                controller.setAppearanceLightStatusBars(false); // 白色图标
            }
        } else {
            activity.getWindow().setStatusBarColor(
                    activity.getResources().getColor(R.color.top_bar_background, null));
            if (controller != null) {
                controller.setAppearanceLightStatusBars(true); // 黑色图标
            }
        }
    }

    /**
     * 设置状态栏（自定义颜色）
     * @param activity Activity
     * @param isDarkMode 是否深色模式
     * @param statusBarColor 状态栏背景色
     */
    public static void setupStatusBar(Activity activity, boolean isDarkMode, int statusBarColor) {
        if (activity == null || activity.getWindow() == null) return;

        activity.getWindow().setStatusBarColor(statusBarColor);

        WindowInsetsControllerCompat controller =
                ViewCompat.getWindowInsetsController(activity.getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(!isDarkMode);
        }
    }

    /**
     * 为标题栏增加状态栏高度的顶部内边距，避免返回按钮和标题与状态栏重叠。
     */
    public static void applyTitleBarPadding(Activity activity) {
        applyTitleBarPadding(activity, R.id.title_bar);
    }

    public static void applyTitleBarPadding(Activity activity, int titleBarId) {
        if (activity == null) return;
        View titleBar = activity.findViewById(titleBarId);
        if (titleBar == null) return;

        int statusBarHeight = getStatusBarHeight(activity);
        titleBar.setPadding(
                titleBar.getPaddingLeft(),
                titleBar.getPaddingTop() + statusBarHeight,
                titleBar.getPaddingRight(),
                titleBar.getPaddingBottom()
        );
    }

    private static int getStatusBarHeight(Context context) {
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return context.getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }
}
