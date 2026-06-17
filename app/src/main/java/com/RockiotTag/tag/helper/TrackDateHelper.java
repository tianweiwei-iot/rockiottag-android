package com.RockiotTag.tag.helper;

import android.app.DatePickerDialog;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.R;

import java.util.Calendar;

/**
 * TrackActivity 日期选择辅助类
 * 负责封装日期选择器、时间选择器、日期切换等逻辑
 */
public class TrackDateHelper {

    private static final String TAG = "TrackDateHelper";

    private final AppCompatActivity activity;
    private final DateCallbacks callbacks;

    /**
     * 日期回调接口，由 Activity 实现以提供数据和处理日期变更
     */
    public interface DateCallbacks {
        Calendar getSelectedDate();
        void setSelectedDate(Calendar date);
        Calendar getStartDate();
        void setStartDate(Calendar date);
        Calendar getEndDate();
        void setEndDate(Calendar date);
        boolean isLoadingTrackData();
        void setLoadingTrackData(boolean loading);
        void updateDateBtnText();
        void loadTrackData();
        void stopPlayback();
    }

    public TrackDateHelper(AppCompatActivity activity, DateCallbacks callbacks) {
        this.activity = activity;
        this.callbacks = callbacks;
    }

    /**
     * 显示日期选择器对话框
     */
    public void showDatePicker() {
        try {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                activity,
                (view, year, month, dayOfMonth) -> {
                    // 关键修复：选择日期前先停止播放
                    callbacks.stopPlayback();

                    // 关键修复：防止快速切换日期导致并发加载
                    if (callbacks.isLoadingTrackData()) {
                        Log.d(TAG, "Already loading track data, ignore date picker selection");
                        return;
                    }

                    Calendar selectedDate = callbacks.getSelectedDate();
                    selectedDate.set(Calendar.YEAR, year);
                    selectedDate.set(Calendar.MONTH, month);
                    selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    Calendar startDate = (Calendar) selectedDate.clone();
                    startDate.set(Calendar.HOUR_OF_DAY, 0);
                    startDate.set(Calendar.MINUTE, 0);
                    startDate.set(Calendar.SECOND, 0);
                    startDate.set(Calendar.MILLISECOND, 0);
                    callbacks.setStartDate(startDate);

                    Calendar endDate = (Calendar) selectedDate.clone();
                    endDate.set(Calendar.HOUR_OF_DAY, 23);
                    endDate.set(Calendar.MINUTE, 59);
                    endDate.set(Calendar.SECOND, 59);
                    endDate.set(Calendar.MILLISECOND, 999);
                    callbacks.setEndDate(endDate);

                    callbacks.updateDateBtnText();
                    callbacks.loadTrackData();
                },
                callbacks.getSelectedDate().get(Calendar.YEAR),
                callbacks.getSelectedDate().get(Calendar.MONTH),
                callbacks.getSelectedDate().get(Calendar.DAY_OF_MONTH)
            );

            Calendar minDate = Calendar.getInstance();
            minDate.add(Calendar.MONTH, -1);
            datePickerDialog.getDatePicker().setMinDate(minDate.getTimeInMillis());

            Calendar maxDate = Calendar.getInstance();
            datePickerDialog.getDatePicker().setMaxDate(maxDate.getTimeInMillis());

            datePickerDialog.show();
        } catch (Exception e) {
            Log.e(TAG, "Error in showDatePicker: " + e.getMessage(), e);
            // 确保在异常情况下也释放加载锁
            callbacks.setLoadingTrackData(false);
        }
    }

    /**
     * 显示开始时间选择器
     */
    public void showStartTimePicker() {
        try {
            androidx.appcompat.app.AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(activity.getString(R.string.select_start_time));

            LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.HORIZONTAL);
            layout.setGravity(Gravity.CENTER);
            layout.setPadding(50, 40, 50, 10);

            final NumberPicker hourPicker = new NumberPicker(activity);
            hourPicker.setMinValue(0);
            hourPicker.setMaxValue(23);
            hourPicker.setValue(callbacks.getStartDate().get(Calendar.HOUR_OF_DAY));
            hourPicker.setWrapSelectorWheel(true);
            hourPicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
            LinearLayout.LayoutParams hourParams = new LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT);
            hourPicker.setLayoutParams(hourParams);

            TextView colonText = new TextView(activity);
            colonText.setText(":");
            colonText.setTextSize(24);
            colonText.setPadding(16, 0, 16, 0);

            final NumberPicker minutePicker = new NumberPicker(activity);
            minutePicker.setMinValue(0);
            minutePicker.setMaxValue(59);
            minutePicker.setValue(callbacks.getStartDate().get(Calendar.MINUTE));
            minutePicker.setWrapSelectorWheel(true);
            minutePicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
            LinearLayout.LayoutParams minuteParams = new LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT);
            minutePicker.setLayoutParams(minuteParams);

            layout.addView(hourPicker);
            layout.addView(colonText);
            layout.addView(minutePicker);

            builder.setView(layout);

            builder.setPositiveButton(activity.getString(R.string.confirm), (dialog, which) -> {
                try {
                    Calendar startDate = callbacks.getStartDate();
                    startDate.set(Calendar.HOUR_OF_DAY, hourPicker.getValue());
                    startDate.set(Calendar.MINUTE, minutePicker.getValue());
                    startDate.set(Calendar.SECOND, 0);
                    startDate.set(Calendar.MILLISECOND, 0);
                    callbacks.updateDateBtnText();
                    callbacks.loadTrackData();
                } catch (Exception e) {
                    Log.e(TAG, "Error in start time picker: " + e.getMessage(), e);
                    // 确保在异常情况下也释放加载锁
                    callbacks.setLoadingTrackData(false);
                }
            });

            builder.setNegativeButton(activity.getString(R.string.cancel), null);
            builder.show();
        } catch (Exception e) {
            Log.e(TAG, "Error in showStartTimePicker: " + e.getMessage(), e);
        }
    }

    /**
     * 显示结束时间选择器
     */
    public void showEndTimePicker() {
        try {
            androidx.appcompat.app.AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(activity.getString(R.string.select_end_time));

            LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.HORIZONTAL);
            layout.setGravity(Gravity.CENTER);
            layout.setPadding(50, 40, 50, 10);

            final NumberPicker hourPicker = new NumberPicker(activity);
            hourPicker.setMinValue(0);
            hourPicker.setMaxValue(23);
            hourPicker.setValue(callbacks.getEndDate().get(Calendar.HOUR_OF_DAY));
            hourPicker.setWrapSelectorWheel(true);
            hourPicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
            LinearLayout.LayoutParams hourParams = new LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT);
            hourPicker.setLayoutParams(hourParams);

            TextView colonText = new TextView(activity);
            colonText.setText(":");
            colonText.setTextSize(24);
            colonText.setPadding(16, 0, 16, 0);

            final NumberPicker minutePicker = new NumberPicker(activity);
            minutePicker.setMinValue(0);
            minutePicker.setMaxValue(59);
            minutePicker.setValue(callbacks.getEndDate().get(Calendar.MINUTE));
            minutePicker.setWrapSelectorWheel(true);
            minutePicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
            LinearLayout.LayoutParams minuteParams = new LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT);
            minutePicker.setLayoutParams(minuteParams);

            layout.addView(hourPicker);
            layout.addView(colonText);
            layout.addView(minutePicker);

            builder.setView(layout);

            builder.setPositiveButton(activity.getString(R.string.confirm), (dialog, which) -> {
                try {
                    Calendar endDate = callbacks.getEndDate();
                    endDate.set(Calendar.HOUR_OF_DAY, hourPicker.getValue());
                    endDate.set(Calendar.MINUTE, minutePicker.getValue());
                    endDate.set(Calendar.SECOND, 59);
                    endDate.set(Calendar.MILLISECOND, 999);
                    callbacks.updateDateBtnText();
                    callbacks.loadTrackData();
                } catch (Exception e) {
                    Log.e(TAG, "Error in end time picker: " + e.getMessage(), e);
                    // 确保在异常情况下也释放加载锁
                    callbacks.setLoadingTrackData(false);
                }
            });

            builder.setNegativeButton(activity.getString(R.string.cancel), null);
            builder.show();
        } catch (Exception e) {
            Log.e(TAG, "Error in showEndTimePicker: " + e.getMessage(), e);
        }
    }

    /**
     * 重置时间范围为今天
     */
    public void resetTimeRange() {
        try {
            // 关键修复：重置日期前先停止播放
            callbacks.stopPlayback();

            Calendar selectedDate = Calendar.getInstance();
            callbacks.setSelectedDate(selectedDate);

            Calendar startDate = (Calendar) selectedDate.clone();
            startDate.set(Calendar.HOUR_OF_DAY, 0);
            startDate.set(Calendar.MINUTE, 0);
            startDate.set(Calendar.SECOND, 0);
            startDate.set(Calendar.MILLISECOND, 0);
            callbacks.setStartDate(startDate);

            Calendar endDate = (Calendar) selectedDate.clone();
            endDate.set(Calendar.HOUR_OF_DAY, 23);
            endDate.set(Calendar.MINUTE, 59);
            endDate.set(Calendar.SECOND, 59);
            endDate.set(Calendar.MILLISECOND, 999);
            callbacks.setEndDate(endDate);

            callbacks.updateDateBtnText();
            callbacks.loadTrackData();
        } catch (Exception e) {
            Log.e(TAG, "Error in resetTimeRange: " + e.getMessage(), e);
            // 确保在异常情况下也释放加载锁
            callbacks.setLoadingTrackData(false);
        }
    }

    /**
     * 跳转到前一天
     */
    public void goToPreviousDay() {
        Log.d(TAG, "=== goToPreviousDay START === isLoadingTrackData=" + callbacks.isLoadingTrackData());
        try {
            callbacks.stopPlayback();

            if (callbacks.isLoadingTrackData()) {
                Log.w(TAG, "[LOADING_BLOCK] Already loading track data, ignore previous day request");
                return;
            }

            Calendar selectedDate = callbacks.getSelectedDate();
            selectedDate.add(Calendar.DAY_OF_MONTH, -1);
            Calendar minDate = Calendar.getInstance();
            minDate.add(Calendar.MONTH, -1);

            if (selectedDate.before(minDate)) {
                Log.d(TAG, "[DATE_LIMIT] Selected date before min date, reverting");
                selectedDate.add(Calendar.DAY_OF_MONTH, 1);
                return;
            }

            Calendar startDate = (Calendar) selectedDate.clone();
            startDate.set(Calendar.HOUR_OF_DAY, 0);
            startDate.set(Calendar.MINUTE, 0);
            startDate.set(Calendar.SECOND, 0);
            startDate.set(Calendar.MILLISECOND, 0);
            callbacks.setStartDate(startDate);

            Calendar endDate = (Calendar) selectedDate.clone();
            endDate.set(Calendar.HOUR_OF_DAY, 23);
            endDate.set(Calendar.MINUTE, 59);
            endDate.set(Calendar.SECOND, 59);
            endDate.set(Calendar.MILLISECOND, 999);
            callbacks.setEndDate(endDate);

            Log.d(TAG, "[DATE_CHANGE] Previous day: " + com.RockiotTag.tag.util.TimeFormatter.formatDate(selectedDate.getTimeInMillis()));
            callbacks.updateDateBtnText();
            callbacks.loadTrackData();
            Log.d(TAG, "=== goToPreviousDay END ===");
        } catch (Exception e) {
            Log.e(TAG, "[EXCEPTION] goToPreviousDay: " + e.getMessage(), e);
            callbacks.setLoadingTrackData(false);
        }
    }

    /**
     * 跳转到后一天
     */
    public void goToNextDay() {
        Log.d(TAG, "=== goToNextDay START === isLoadingTrackData=" + callbacks.isLoadingTrackData());
        try {
            callbacks.stopPlayback();

            if (callbacks.isLoadingTrackData()) {
                Log.w(TAG, "[LOADING_BLOCK] Already loading track data, ignore next day request");
                return;
            }

            Calendar selectedDate = callbacks.getSelectedDate();
            selectedDate.add(Calendar.DAY_OF_MONTH, 1);
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 23);
            today.set(Calendar.MINUTE, 59);
            today.set(Calendar.SECOND, 59);

            if (selectedDate.after(today)) {
                Log.d(TAG, "[DATE_LIMIT] Selected date after today, reverting");
                selectedDate.add(Calendar.DAY_OF_MONTH, -1);
                return;
            }

            Calendar startDate = (Calendar) selectedDate.clone();
            startDate.set(Calendar.HOUR_OF_DAY, 0);
            startDate.set(Calendar.MINUTE, 0);
            startDate.set(Calendar.SECOND, 0);
            startDate.set(Calendar.MILLISECOND, 0);
            callbacks.setStartDate(startDate);

            Calendar endDate = (Calendar) selectedDate.clone();
            endDate.set(Calendar.HOUR_OF_DAY, 23);
            endDate.set(Calendar.MINUTE, 59);
            endDate.set(Calendar.SECOND, 59);
            endDate.set(Calendar.MILLISECOND, 999);
            callbacks.setEndDate(endDate);

            Log.d(TAG, "[DATE_CHANGE] Next day: " + com.RockiotTag.tag.util.TimeFormatter.formatDate(selectedDate.getTimeInMillis()));
            callbacks.updateDateBtnText();
            callbacks.loadTrackData();
            Log.d(TAG, "=== goToNextDay END ===");
        } catch (Exception e) {
            Log.e(TAG, "[EXCEPTION] goToNextDay: " + e.getMessage(), e);
            callbacks.setLoadingTrackData(false);
        }
    }

    /**
     * 更新日期按钮文本（今天/具体日期）
     */
    public void updateDateBtnText(android.widget.Button dateBtn) {
        try {
            Calendar today = Calendar.getInstance();
            Calendar selectedDate = callbacks.getSelectedDate();
            if (selectedDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                selectedDate.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                selectedDate.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)) {
                dateBtn.setText(activity.getString(R.string.today));
            } else {
                dateBtn.setText(com.RockiotTag.tag.util.TimeFormatter.formatDate(selectedDate.getTimeInMillis()));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in updateDateBtnText: " + e.getMessage(), e);
        }
    }

    /**
     * 获取一天的开始时间（00:00:00）
     */
    public static Calendar getDayStartTime(Calendar date) {
        Calendar start = (Calendar) date.clone();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        return start;
    }

    /**
     * 获取一天的结束时间（23:59:59）
     */
    public static Calendar getDayEndTime(Calendar date) {
        Calendar end = (Calendar) date.clone();
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);
        return end;
    }

    /**
     * 判断 Calendar 是否是今天
     */
    public static boolean isToday(Calendar date) {
        Calendar today = Calendar.getInstance();
        return date.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
               date.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
               date.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH);
    }
}
