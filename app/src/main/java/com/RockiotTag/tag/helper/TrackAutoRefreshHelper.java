package com.RockiotTag.tag.helper;

import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.util.LogUtil;

import java.util.Calendar;

/**
 * TrackActivity 当天轨迹自动刷新逻辑。
 */
public class TrackAutoRefreshHelper {

    private static final String TAG = "TrackAutoRefreshHelper";
    private static final long AUTO_REFRESH_INTERVAL = 3 * 60 * 1000L;
    private static final long INITIAL_DELAY_MS = 10_000L;

    public interface Host {
        AppCompatActivity getActivity();
        boolean isFinishing();
        boolean isDestroyed();
        Calendar getSelectedDate();
        boolean isViewModelReady();
        void loadTrackDataSilently();
    }

    private final Host host;
    private Handler autoRefreshHandler;
    private Runnable autoRefreshRunnable;

    public TrackAutoRefreshHelper(Host host) {
        this.host = host;
    }

    public void initAutoRefresh() {
        autoRefreshHandler = new Handler(android.os.Looper.getMainLooper());
        autoRefreshRunnable = () -> {
            try {
                performAutoRefresh();
                if (autoRefreshHandler != null && !host.isFinishing() && !host.isDestroyed()) {
                    autoRefreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in auto refresh runnable: " + e.getMessage(), e);
                stopAutoRefresh();
            }
        };
    }

    public void startAutoRefresh() {
        if (autoRefreshHandler == null || autoRefreshRunnable == null) return;
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);

        Calendar selectedDate = host.getSelectedDate();
        if (selectedDate == null || !isToday(selectedDate)) {
            LogUtil.d(TAG, "Auto refresh skipped: selected date is not today");
            return;
        }

        autoRefreshHandler.postDelayed(autoRefreshRunnable, INITIAL_DELAY_MS);
        LogUtil.d(TAG, "Auto refresh started for TODAY, interval: "
                + (AUTO_REFRESH_INTERVAL / 1000 / 60) + " minutes");
    }

    public void stopAutoRefresh() {
        if (autoRefreshHandler != null && autoRefreshRunnable != null) {
            autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
            LogUtil.d(TAG, "Auto refresh stopped");
        }
    }

    public void cleanup() {
        stopAutoRefresh();
        autoRefreshHandler = null;
        autoRefreshRunnable = null;
    }

    private void performAutoRefresh() {
        try {
            LogUtil.d(TAG, "Performing auto refresh for current date...");

            if (host.isFinishing() || host.isDestroyed()) {
                LogUtil.d(TAG, "Activity is finishing or destroyed, skip auto refresh");
                stopAutoRefresh();
                return;
            }

            if (!host.isViewModelReady()) {
                Log.w(TAG, "Required components not initialized, skip auto refresh");
                return;
            }

            host.getActivity().runOnUiThread(() -> {
                try {
                    if (host.isFinishing() || host.isDestroyed()) return;
                    LogUtil.d(TAG, "Auto refresh: loading track data silently");
                    host.loadTrackDataSilently();
                    Calendar selectedDate = host.getSelectedDate();
                    if (selectedDate != null) {
                        LogUtil.d(TAG, "Auto refresh completed for date: " + selectedDate.getTime());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error during auto refresh: " + e.getMessage(), e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in performAutoRefresh: " + e.getMessage(), e);
            stopAutoRefresh();
        }
    }

    private static boolean isToday(Calendar selectedDate) {
        Calendar today = Calendar.getInstance();
        return selectedDate.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && selectedDate.get(Calendar.MONTH) == today.get(Calendar.MONTH)
                && selectedDate.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH);
    }
}
