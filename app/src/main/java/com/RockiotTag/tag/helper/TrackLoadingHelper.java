package com.RockiotTag.tag.helper;

import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.R;

/**
 * TrackActivity 加载状态辅助类
 * 负责封装加载进度条和加载对话框的显示/隐藏逻辑
 */
public class TrackLoadingHelper {

    private static final String TAG = "TrackLoadingHelper";

    private final AppCompatActivity activity;
    private android.app.AlertDialog loadingDialog;
    private ProgressBar loadingProgress;

    public TrackLoadingHelper(AppCompatActivity activity) {
        this.activity = activity;
    }

    /**
     * 设置进度条引用
     */
    public void setLoadingProgress(ProgressBar loadingProgress) {
        this.loadingProgress = loadingProgress;
    }

    /**
     * 显示加载进度条
     */
    public void showLoading() {
        if (loadingProgress != null) {
            loadingProgress.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 显示加载提示对话框
     */
    public void showLoadingDialog() {
        Log.d(TAG, "=== showLoadingDialog() called ===");
        if (activity.isFinishing() || activity.isDestroyed()) {
            Log.w(TAG, "[DIALOG_SKIP] Activity is finishing/destroyed, skip showing dialog");
            return;
        }

        if (loadingDialog != null && loadingDialog.isShowing()) {
            Log.d(TAG, "[DIALOG_EXISTS] Loading dialog already showing, skip");
            return;
        }

        Log.d(TAG, "[DIALOG_CREATE] Creating and showing loading dialog");
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(activity);
        builder.setTitle(activity.getString(R.string.loading_track_title));
        builder.setMessage(activity.getString(R.string.loading_track_message));
        builder.setCancelable(false);

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(50, 50, 50, 50);

        ProgressBar progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleSmall);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(20, 0, 40, 0);
        progressBar.setLayoutParams(params);
        layout.addView(progressBar);

        TextView textView = new TextView(activity);
        textView.setText(activity.getString(R.string.loading_track_message));
        textView.setTextSize(14);
        layout.addView(textView);

        builder.setView(layout);
        loadingDialog = builder.create();
        loadingDialog.show();
        Log.d(TAG, "[DIALOG_SHOWN] Loading dialog is now visible");
    }

    /**
     * 隐藏加载提示对话框
     */
    public void hideLoadingDialog() {
        Log.d(TAG, "=== hideLoadingDialog() called === loadingDialog=" + (loadingDialog != null) + ", isShowing=" + (loadingDialog != null && loadingDialog.isShowing()));
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
            Log.d(TAG, "[DIALOG_DISMISSED] Loading dialog dismissed");
        }
        loadingDialog = null;
    }

    /**
     * 隐藏加载状态（进度条 + 对话框）
     */
    public void hideLoading() {
        Log.d(TAG, "=== hideLoading() called ===");
        if (loadingProgress != null) {
            loadingProgress.setVisibility(View.GONE);
            Log.d(TAG, "[PROGRESS_HIDDEN] Loading progress bar hidden");
        }
        hideLoadingDialog();
    }
}
