package com.RockiotTag.tag.util;

import android.app.DatePickerDialog;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * 时间选择器助手类
 * 封装日期和时间选择器的通用逻辑
 */
public class TimePickerHelper {
    
    /**
     * 显示日期选择器
     * 
     * @param context 上下文
     * @param selectedDate 当前选中的日期
     * @param onDateSelected 日期选择回调
     */
    public static void showDatePicker(Context context, Calendar selectedDate, OnDateSelectedListener onDateSelected) {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
            context,
            (view, year, month, dayOfMonth) -> {
                selectedDate.set(Calendar.YEAR, year);
                selectedDate.set(Calendar.MONTH, month);
                selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                onDateSelected.onDateSelected(selectedDate);
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        );

        // 设置最小日期（一个月前）
        Calendar minDate = Calendar.getInstance();
        minDate.add(Calendar.MONTH, -1);
        datePickerDialog.getDatePicker().setMinDate(minDate.getTimeInMillis());

        // 设置最大日期（今天）
        Calendar maxDate = Calendar.getInstance();
        datePickerDialog.getDatePicker().setMaxDate(maxDate.getTimeInMillis());

        datePickerDialog.show();
    }
    
    /**
     * 显示时间选择器
     * 
     * @param context 上下文
     * @param title 标题
     * @param calendar 当前时间
     * @param onTimeSelected 时间选择回调
     */
    public static void showTimePicker(Context context, String title, Calendar calendar, OnTimeSelectedListener onTimeSelected) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setPadding(50, 40, 50, 10);

        final NumberPicker hourPicker = new NumberPicker(context);
        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(23);
        hourPicker.setValue(calendar.get(Calendar.HOUR_OF_DAY));
        hourPicker.setWrapSelectorWheel(true);
        hourPicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        LinearLayout.LayoutParams hourParams = new LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT);
        hourPicker.setLayoutParams(hourParams);

        TextView colonText = new TextView(context);
        colonText.setText(":");
        colonText.setTextSize(24);
        colonText.setPadding(16, 0, 16, 0);

        final NumberPicker minutePicker = new NumberPicker(context);
        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(59);
        minutePicker.setValue(calendar.get(Calendar.MINUTE));
        minutePicker.setWrapSelectorWheel(true);
        minutePicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        LinearLayout.LayoutParams minuteParams = new LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT);
        minutePicker.setLayoutParams(minuteParams);

        layout.addView(hourPicker);
        layout.addView(colonText);
        layout.addView(minutePicker);

        builder.setView(layout);

        builder.setPositiveButton("确定", (dialog, which) -> {
            calendar.set(Calendar.HOUR_OF_DAY, hourPicker.getValue());
            calendar.set(Calendar.MINUTE, minutePicker.getValue());
            onTimeSelected.onTimeSelected(calendar);
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    /**
     * 格式化日期按钮文本
     * 
     * @param context 上下文
     * @param selectedDate 选中的日期
     * @return 格式化的日期文本
     */
    public static String formatDateButtonText(Context context, Calendar selectedDate) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        
        Calendar today = Calendar.getInstance();
        if (selectedDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            selectedDate.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
            selectedDate.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)) {
            return "今天";
        } else {
            return sdf.format(selectedDate.getTime());
        }
    }
    
    /**
     * 格式化时间文本
     * 
     * @param calendar 时间
     * @return 格式化的时间文本（HH:mm）
     */
    public static String formatTimeText(Calendar calendar) {
        SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return timeSdf.format(calendar.getTime());
    }
    
    /**
     * 跳转到前一天
     * 
     * @param selectedDate 当前日期
     * @param onDateChanged 日期变化回调
     * @return 是否成功跳转
     */
    public static boolean goToPreviousDay(Calendar selectedDate, OnDateChangedListener onDateChanged) {
        selectedDate.add(Calendar.DAY_OF_MONTH, -1);
        Calendar minDate = Calendar.getInstance();
        minDate.add(Calendar.MONTH, -1);
        
        if (selectedDate.before(minDate)) {
            selectedDate.add(Calendar.DAY_OF_MONTH, 1);
            return false;
        }
        
        onDateChanged.onDateChanged(selectedDate);
        return true;
    }
    
    /**
     * 跳转到后一天
     * 
     * @param selectedDate 当前日期
     * @param onDateChanged 日期变化回调
     * @return 是否成功跳转
     */
    public static boolean goToNextDay(Calendar selectedDate, OnDateChangedListener onDateChanged) {
        selectedDate.add(Calendar.DAY_OF_MONTH, 1);
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 23);
        today.set(Calendar.MINUTE, 59);
        today.set(Calendar.SECOND, 59);
        
        if (selectedDate.after(today)) {
            selectedDate.add(Calendar.DAY_OF_MONTH, -1);
            return false;
        }
        
        onDateChanged.onDateChanged(selectedDate);
        return true;
    }
    
    /**
     * 重置到今天
     * 
     * @param onDateChanged 日期变化回调
     */
    public static void resetToToday(OnDateChangedListener onDateChanged) {
        Calendar today = Calendar.getInstance();
        onDateChanged.onDateChanged(today);
    }
    
    /**
     * 日期选择回调接口
     */
    public interface OnDateSelectedListener {
        void onDateSelected(Calendar date);
    }
    
    /**
     * 时间选择回调接口
     */
    public interface OnTimeSelectedListener {
        void onTimeSelected(Calendar time);
    }
    
    /**
     * 日期变化回调接口
     */
    public interface OnDateChangedListener {
        void onDateChanged(Calendar date);
    }
}
