package com.RockiotTag.tag.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.ContextThemeWrapper;

import com.RockiotTag.tag.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 手动深色模式下创建主题化对话框（不依赖 UI_MODE_NIGHT）。
 */
public final class ThemedDialogHelper {

    private ThemedDialogHelper() {}

    public static boolean isDarkModeEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        return prefs.getBoolean("dark_mode", false);
    }

    public static MaterialAlertDialogBuilder createBuilder(Context context) {
        if (isDarkModeEnabled(context)) {
            ContextThemeWrapper themedContext = new ContextThemeWrapper(context, R.style.AlertDialogTheme_Dark);
            return new MaterialAlertDialogBuilder(themedContext, R.style.AlertDialogTheme_Dark);
        }
        return new MaterialAlertDialogBuilder(context);
    }
}
