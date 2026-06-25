package com.RockiotTag.tag.util;

import android.content.Context;
import android.content.DialogInterface;
import android.view.ContextThemeWrapper;

import androidx.appcompat.app.AlertDialog;

import com.RockiotTag.tag.R;

/**
 * 对话框助手工具类
 * 职责：统一处理常用对话框的创建和显示
 */
public class DialogHelper {

    private static Context getThemedContext(Context context) {
        boolean darkMode = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getBoolean("dark_mode", false);
        int theme = darkMode ? R.style.AlertDialogTheme_Dark : R.style.AlertDialogTheme;
        return new ContextThemeWrapper(context, theme);
    }

    public static void showConfirmDialog(Context context, String title, String message,
                                        String positiveText, String negativeText,
                                        DialogInterface.OnClickListener onPositive,
                                        DialogInterface.OnClickListener onNegative) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getThemedContext(context));
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(positiveText, onPositive);
        if (onNegative != null) {
            builder.setNegativeButton(negativeText, onNegative);
        }
        builder.show();
    }

    public static void showInfoDialog(Context context, String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getThemedContext(context));
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }
}
