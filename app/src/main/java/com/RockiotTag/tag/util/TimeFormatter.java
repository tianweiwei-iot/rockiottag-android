package com.RockiotTag.tag.util;

import android.content.Context;

import com.RockiotTag.tag.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 时间格式化工具类
 * 提供智能的时间显示格式，根据日期远近自动选择最合适的格式
 */
public class TimeFormatter {
    
    private static final SimpleDateFormat FULL_FORMAT = 
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    
    private static final SimpleDateFormat SHORT_FORMAT = 
        new SimpleDateFormat("HH:mm:ss", Locale.US);
    
    private static final SimpleDateFormat DATE_TIME_FORMAT = 
        new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);
    
    /**
     * 格式化时间为完整格式
     * @param timestamp 时间戳
     * @return 例如：2024-01-15 14:32:15
     */
    public static synchronized String formatFullTime(long timestamp) {
        if (timestamp <= 0) {
            return "未知";
        }
        return FULL_FORMAT.format(new Date(timestamp));
    }
    
    /**
     * 格式化时间为短时间格式（时:分:秒）
     * @param timestamp 时间戳
     * @return 例如：14:32:15
     */
    public static synchronized String formatShortTime(long timestamp) {
        if (timestamp <= 0) {
            return "--:--:--";
        }
        return SHORT_FORMAT.format(new Date(timestamp));
    }
    
    /**
     * 格式化日期（年-月-日）
     * @param timestamp 时间戳
     * @return 例如：2024-01-15
     */
    public static synchronized String formatDate(long timestamp) {
        if (timestamp <= 0) {
            return "未知";
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return dateFormat.format(new Date(timestamp));
    }
    
    /**
     * 格式化时间（时:分）
     * @param timestamp 时间戳
     * @return 例如：14:32
     */
    public static synchronized String formatTimeHM(long timestamp) {
        if (timestamp <= 0) {
            return "--:--";
        }
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);
        return timeFormat.format(new Date(timestamp));
    }
    
    /**
     * 智能格式化时间（根据日期远近选择格式）
     * @param context 上下文（用于获取国际化字符串）
     * @param timestamp 时间戳
     * @return 例如：2026-05-12 14:32:15 今天 / 2026-05-11 09:15:30 昨天
     */
    public static synchronized String formatSmartTime(Context context, long timestamp) {
        if (timestamp <= 0) {
            return "未知";
        }
        
        Calendar now = Calendar.getInstance();
        Calendar time = Calendar.getInstance();
        time.setTimeInMillis(timestamp);
        
        // 今天
        if (isSameDay(now, time)) {
            return FULL_FORMAT.format(time.getTime()) + " " + context.getString(R.string.today);
        }
        
        // 昨天
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(yesterday, time)) {
            return FULL_FORMAT.format(time.getTime()) + " " + context.getString(R.string.yesterday);
        }
        
        // 前天
        Calendar dayBeforeYesterday = Calendar.getInstance();
        dayBeforeYesterday.add(Calendar.DAY_OF_YEAR, -2);
        if (isSameDay(dayBeforeYesterday, time)) {
            return FULL_FORMAT.format(time.getTime()) + " " + context.getString(R.string.day_before_yesterday);
        }
        
        // 本周内
        if (isSameWeek(now, time)) {
            String weekday = getWeekdayName(context, time.get(Calendar.DAY_OF_WEEK));
            return FULL_FORMAT.format(time.getTime()) + " " + weekday;
        }
        
        // 更早：显示月-日 时:分:秒
        return DATE_TIME_FORMAT.format(time.getTime());
    }
    
    /**
     * 判断两个Calendar是否是同一天
     */
    private static boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
               cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH);
    }
    
    /**
     * 判断两个Calendar是否在同一周
     */
    private static boolean isSameWeek(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.WEEK_OF_YEAR) == cal2.get(Calendar.WEEK_OF_YEAR);
    }
    
    /**
     * 获取星期几的中文名称
     */
    private static String getWeekdayName(Context context, int dayOfWeek) {
        String[] weekdays = {
            context.getString(R.string.sunday),
            context.getString(R.string.monday),
            context.getString(R.string.tuesday),
            context.getString(R.string.wednesday),
            context.getString(R.string.thursday),
            context.getString(R.string.friday),
            context.getString(R.string.saturday)
        };
        // Calendar.DAY_OF_WEEK: 1=周日, 2=周一, ..., 7=周六
        if (dayOfWeek >= 1 && dayOfWeek <= 7) {
            return weekdays[dayOfWeek - 1];
        }
        return "";
    }
}
