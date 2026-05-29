package com.RockiotTag.tag.util;

import android.content.Context;
import android.content.DialogInterface;

import androidx.appcompat.app.AlertDialog;

/**
 * 对话框助手工具类
 * 职责：统一处理常用对话框的创建和显示
 */
public class DialogHelper {
    
    /**
     * 显示确认对话框
     * @param context 上下文
     * @param title 标题
     * @param message 消息
     * @param positiveText 确定按钮文本
     * @param negativeText 取消按钮文本
     * @param onPositive 确定按钮回调
     * @param onNegative 取消按钮回调（可选）
     */
    public static void showConfirmDialog(Context context, String title, String message,
                                        String positiveText, String negativeText,
                                        DialogInterface.OnClickListener onPositive,
                                        DialogInterface.OnClickListener onNegative) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(positiveText, onPositive);
        if (onNegative != null) {
            builder.setNegativeButton(negativeText, onNegative);
        }
        builder.show();
    }
    
    /**
     * 显示简单提示对话框
     * @param context 上下文
     * @param title 标题
     * @param message 消息
     */
    public static void showInfoDialog(Context context, String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }
}
