package com.RockiotTag.tag.util;

import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.RockiotTag.tag.R;

/**
 * 地图页浮动按钮主题：浅色白底黑图标，深色黑底白图标。
 */
public final class MapFloatingButtonHelper {

    private MapFloatingButtonHelper() {
    }

    public static void applyTheme(AppCompatActivity activity, boolean isDarkMode, ImageButton... buttons) {
        if (activity == null || buttons == null) {
            return;
        }
        int backgroundRes = isDarkMode
                ? R.drawable.bg_map_floating_button_dark
                : R.drawable.bg_map_floating_button;
        int iconColor = ContextCompat.getColor(activity, isDarkMode
                ? R.color.dark_map_floating_icon
                : R.color.map_floating_icon);

        for (ImageButton button : buttons) {
            if (button == null) {
                continue;
            }
            button.setBackgroundResource(backgroundRes);
            button.setColorFilter(iconColor, android.graphics.PorterDuff.Mode.SRC_IN);
        }
    }

    public static void applyMainScreenButtons(AppCompatActivity activity, boolean isDarkMode) {
        if (activity == null) {
            return;
        }
        applyTheme(activity, isDarkMode,
                activity.findViewById(R.id.refresh_btn),
                activity.findViewById(R.id.map_type_btn),
                activity.findViewById(R.id.locate_btn));
    }

    public static void applyTrackScreenButtons(AppCompatActivity activity, boolean isDarkMode) {
        if (activity == null) {
            return;
        }
        applyTheme(activity, isDarkMode,
                activity.findViewById(R.id.statistics_btn),
                activity.findViewById(R.id.refresh_btn));
    }
}
