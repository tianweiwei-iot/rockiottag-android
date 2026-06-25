package com.RockiotTag.tag.helper;

import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.RockiotTag.tag.R;
import com.google.android.gms.maps.GoogleMap;

/**
 * TrackActivity 主题辅助类
 * 负责封装深色/浅色模式应用、Tab选中状态等主题相关逻辑
 */
public class TrackThemeHelper {

    private static final String TAG = "TrackThemeHelper";

    private final AppCompatActivity activity;
    private final ThemeCallbacks callbacks;

    /**
     * 主题回调接口，由 Activity 实现以提供必要的视图和数据
     */
    public interface ThemeCallbacks {
        boolean isGoogleMapMode();
        GoogleMap getGoogleMap();
        com.amap.api.maps.AMap getAMap();
        // 底部导航栏 Tab 视图
        android.widget.LinearLayout getTabHome();
        android.widget.LinearLayout getTabList();
        android.widget.LinearLayout getTabTrack();
        android.widget.LinearLayout getTabProfile();
        android.widget.ImageView getTabHomeIcon();
        android.widget.ImageView getTabListIcon();
        android.widget.ImageView getTabTrackIcon();
        android.widget.ImageView getTabProfileIcon();
    }

    public TrackThemeHelper(AppCompatActivity activity, ThemeCallbacks callbacks) {
        this.activity = activity;
        this.callbacks = callbacks;
    }

    /**
     * 应用深色/浅色模式
     */
    public void applyDarkMode(boolean isDarkMode) {
        int bgColor = activity.getResources().getColor(isDarkMode ? R.color.dark_background : R.color.background, null);
        int topBarColor = activity.getResources().getColor(isDarkMode ? R.color.dark_top_bar_background : R.color.top_bar_background, null);
        int onSurfaceColor = activity.getResources().getColor(isDarkMode ? R.color.dark_onSurface : R.color.onSurface, null);
        int textSecColor = activity.getResources().getColor(isDarkMode ? R.color.dark_text_secondary : R.color.text_secondary, null);
        int cardColor = activity.getResources().getColor(isDarkMode ? R.color.dark_card_background : R.color.card_background, null);

        // 根视图
        View rootView = activity.findViewById(android.R.id.content);
        if (rootView != null) rootView.setBackgroundColor(bgColor);

        // 顶部栏
        View topBar = activity.findViewById(R.id.top_bar);
        if (topBar != null) topBar.setBackgroundColor(topBarColor);

        // 标题文字
        TextView titleText = activity.findViewById(R.id.title_text);
        if (titleText != null) titleText.setTextColor(onSurfaceColor);

        // 轨迹页浮动按钮（统计 / 刷新）
        com.RockiotTag.tag.util.MapFloatingButtonHelper.applyTrackScreenButtons(activity, isDarkMode);

        // 轨迹信息卡片
        CardView trackInfoCard = activity.findViewById(R.id.track_info_card);
        if (trackInfoCard != null) {
            trackInfoCard.setCardBackgroundColor(cardColor);
            if (trackInfoCard instanceof ViewGroup) {
                updateChildViewsColor((ViewGroup) trackInfoCard, onSurfaceColor, textSecColor);
            }
        }

        // 底部导航栏
        View bottomNav = activity.findViewById(R.id.bottom_navigation);
        if (bottomNav != null) bottomNav.setBackgroundColor(topBarColor);

        // 更新导航栏Tab文字和图标颜色
        int selectedColor = activity.getResources().getColor(R.color.brand_primary, null);
        int unselectedColor = isDarkMode ?
            activity.getResources().getColor(R.color.dark_text_secondary, null) :
            activity.getResources().getColor(R.color.text_secondary, null);
        int[][] tabPairs = {
            {R.id.tab_home_icon, R.id.tab_home_text},
            {R.id.tab_list_icon, R.id.tab_list_text},
            {R.id.tab_track_icon, R.id.tab_track_text},
            {R.id.tab_profile_icon, R.id.tab_profile_text}
        };
        for (int i = 0; i < tabPairs.length; i++) {
            ImageView icon = activity.findViewById(tabPairs[i][0]);
            TextView text = activity.findViewById(tabPairs[i][1]);
            if (icon != null) icon.setColorFilter(unselectedColor);
            if (text != null) text.setTextColor(unselectedColor);
        }

        // 状态栏
        com.RockiotTag.tag.util.StatusBarHelper.setupStatusBar(activity, isDarkMode);

        // 深色模式：设置地图样式（添加完善的异常处理）
        try {
            if (callbacks.isGoogleMapMode()) {
                if (callbacks.getGoogleMap() != null) {
                    if (isDarkMode) {
                        try {
                            callbacks.getGoogleMap().setMapStyle(com.google.android.gms.maps.model.MapStyleOptions.loadRawResourceStyle(activity, R.raw.map_style_night));
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to apply dark map style: " + e.getMessage());
                        }
                    } else {
                        callbacks.getGoogleMap().setMapStyle(null);
                    }
                }
            } else {
                if (callbacks.getAMap() != null) {
                    if (isDarkMode) {
                        callbacks.getAMap().setMapType(com.amap.api.maps.AMap.MAP_TYPE_NIGHT);
                    } else {
                        callbacks.getAMap().setMapType(com.amap.api.maps.AMap.MAP_TYPE_NORMAL);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying dark map style: " + e.getMessage());
        }
    }

    /**
     * 递归更新子视图颜色
     */
    public void updateChildViewsColor(ViewGroup parent, int onSurfaceColor, int textSecColor) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof TextView) {
                TextView tv = (TextView) child;
                // 按钮文字保持原色（白色），只改普通文字
                if (!(child instanceof Button)) {
                    tv.setTextColor(onSurfaceColor);
                }
            } else if (child instanceof ImageView) {
                ((ImageView) child).setColorFilter(textSecColor);
            } else if (child instanceof ViewGroup) {
                updateChildViewsColor((ViewGroup) child, onSurfaceColor, textSecColor);
            }
        }
    }

    /**
     * 更新底部导航栏 Tab 选中状态
     */
    public void updateTabSelection(int tabIndex) {
        if (callbacks.getTabHome() != null) callbacks.getTabHome().setSelected(tabIndex == 0);
        if (callbacks.getTabList() != null) callbacks.getTabList().setSelected(tabIndex == 1);
        if (callbacks.getTabTrack() != null) callbacks.getTabTrack().setSelected(tabIndex == 2);
        if (callbacks.getTabProfile() != null) callbacks.getTabProfile().setSelected(tabIndex == 3);

        int selectedColor = activity.getResources().getColor(R.color.brand_primary, null);
        int unselectedColor = activity.getResources().getColor(R.color.text_secondary, null);

        if (callbacks.getTabHomeIcon() != null) callbacks.getTabHomeIcon().setColorFilter(tabIndex == 0 ? selectedColor : unselectedColor);
        if (callbacks.getTabListIcon() != null) callbacks.getTabListIcon().setColorFilter(tabIndex == 1 ? selectedColor : unselectedColor);
        if (callbacks.getTabTrackIcon() != null) callbacks.getTabTrackIcon().setColorFilter(tabIndex == 2 ? selectedColor : unselectedColor);
        if (callbacks.getTabProfileIcon() != null) callbacks.getTabProfileIcon().setColorFilter(tabIndex == 3 ? selectedColor : unselectedColor);
    }
}
