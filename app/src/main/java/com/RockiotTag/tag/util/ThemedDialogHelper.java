package com.RockiotTag.tag.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

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
        ContextThemeWrapper themedContext = new ContextThemeWrapper(context, R.style.AlertDialogTheme);
        return new MaterialAlertDialogBuilder(themedContext, R.style.AlertDialogTheme);
    }

    /**
     * 编辑设备对话框标题栏：左侧标题，右上角解绑（红色文字）。
     */
    public static View createEditDeviceTitleBar(Context context, View.OnClickListener onUnbindClick) {
        boolean darkMode = isDarkModeEnabled(context);
        int titleColor = context.getResources().getColor(
                darkMode ? R.color.dark_onSurface : R.color.onSurface, null);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, 24);

        TextView title = new TextView(context);
        title.setText(R.string.edit_device);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(titleColor);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(title, titleLp);

        TextView unbindBtn = new TextView(context);
        unbindBtn.setText(R.string.unbind);
        unbindBtn.setTextColor(Color.RED);
        unbindBtn.setTextSize(14);
        unbindBtn.setPadding(24, 8, 0, 8);
        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
        unbindBtn.setBackgroundResource(outValue.resourceId);
        unbindBtn.setOnClickListener(onUnbindClick);
        header.addView(unbindBtn);

        return header;
    }
}
