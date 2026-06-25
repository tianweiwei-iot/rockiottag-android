package com.RockiotTag.tag.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * 全局 Toast：新提示会取消上一条；Activity 切走或进后台时由 Application 统一 cancel。
 */
public final class ToastHelper {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Toast currentToast;

    private ToastHelper() {
    }

    public static void show(Context context, int resId) {
        if (context == null) {
            return;
        }
        show(context, context.getString(resId), Toast.LENGTH_SHORT);
    }

    public static void show(Context context, CharSequence message) {
        show(context, message, Toast.LENGTH_SHORT);
    }

    public static void showLong(Context context, int resId) {
        if (context == null) {
            return;
        }
        show(context, context.getString(resId), Toast.LENGTH_LONG);
    }

    public static void showLong(Context context, CharSequence message) {
        show(context, message, Toast.LENGTH_LONG);
    }

    public static void show(Context context, CharSequence message, int duration) {
        if (context == null || message == null || message.length() == 0) {
            return;
        }
        Context appContext = context.getApplicationContext();
        Runnable action = () -> {
            cancel();
            currentToast = Toast.makeText(appContext, message, duration);
            currentToast.show();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            MAIN.post(action);
        }
    }

    /** Activity onPause / 切界面时调用，取消尚未显示的 Toast */
    public static void cancel() {
        Runnable action = () -> {
            if (currentToast != null) {
                currentToast.cancel();
                currentToast = null;
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            MAIN.post(action);
        }
    }
}
