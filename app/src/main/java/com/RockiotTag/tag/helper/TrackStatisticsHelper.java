package com.RockiotTag.tag.helper;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.RockiotTag.tag.R;
import com.RockiotTag.tag.StayPoint;
import com.RockiotTag.tag.util.TimeFormatter;
import com.RockiotTag.tag.util.TrackCalculator;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 轨迹统计信息助手
 * 职责：计算和显示轨迹统计信息
 */
public class TrackStatisticsHelper {
    
    /**
     * 显示统计对话框
     */
    public static void showStatisticsDialog(
        Context context,
        List<StayPoint> stayPoints,
        TextView totalDistanceText) {
            
        if (stayPoints == null || stayPoints.isEmpty()) {
            return;
        }
            
        // 计算总距离
        TrackCalculator.TrackStatistics stats = 
            TrackCalculator.calculateTrackStatistics(convertToLocationDataList(stayPoints));
            
        double totalDistanceKm = stats.totalDistanceKm;
        int validSegments = stats.validSegments;
            
        // 计算时间跨度
        long startTime = stayPoints.get(0).getArriveTime();
        long endTime = stayPoints.get(stayPoints.size() - 1).getLeaveTime();
        long durationMs = endTime - startTime;
            
        // 格式化时间
        String startTimeStr = TimeFormatter.formatTimeHM(startTime);
        String endTimeStr = TimeFormatter.formatTimeHM(endTime);
        String durationStr = formatDuration(context, durationMs);
            
        // 计算停留点统计
        int stayPointCount = 0;
        long totalStayDuration = 0;
        for (StayPoint point : stayPoints) {
            if (point.isStayPoint()) {
                stayPointCount++;
                totalStayDuration += point.getStayDuration();
            }
        }
            
        // 创建统计信息布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(context, 16), dpToPx(context, 16), dpToPx(context, 16), dpToPx(context, 16));
        layout.setBackgroundColor(Color.WHITE);
        layout.setId(View.generateViewId());
            
        // 添加标题
        TextView titleView = new TextView(context);
        titleView.setText(context.getString(R.string.track_statistics_info));
        titleView.setTextSize(18);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        titleView.setTextColor(Color.parseColor("#333333"));
        titleView.setPadding(0, 0, 0, dpToPx(context, 16));
        layout.addView(titleView);
            
        // 添加分隔线
        layout.addView(createDivider(context));
            
        // 添加统计项
        addStatisticItem(context, layout, "📍", context.getString(R.string.track_points_label).trim(), 
            stayPoints.size() + context.getString(R.string.track_points_unit).trim(), "#666666");
        addStatisticItem(context, layout, "✅", context.getString(R.string.valid_segments_label).trim(), 
            validSegments + context.getString(R.string.valid_segments_unit).trim(), "#666666");
        addStatisticItem(context, layout, "📏", context.getString(R.string.total_distance_label).trim(), 
            String.format("%.2f km", totalDistanceKm), "#2196F3");
        addStatisticItem(context, layout, "⏱️", context.getString(R.string.time_span_label).trim(),
            durationStr, "#666666");
        addStatisticItem(context, layout, "🟢", context.getString(R.string.start_time_label).trim(), 
            startTimeStr, "#4CAF50");
        addStatisticItem(context, layout, "🔴", context.getString(R.string.end_time_label).trim(), 
            endTimeStr, "#F44336");
            
        // 添加分隔线
        layout.addView(createDivider(context));
            
        // 添加停留点统计
        addStatisticItem(context, layout, "🛑", context.getString(R.string.stay_points_label).trim(), 
            stayPointCount + context.getString(R.string.stay_points_unit).trim(), "#FF9800");
        if (stayPointCount > 0) {
            addStatisticItem(context, layout, "⏳", context.getString(R.string.total_stay_label).trim(), 
                formatDuration(context, totalStayDuration), "#FF9800");
        }
            
        // 添加底部间距
        android.view.View bottomSpace = new android.view.View(context);
        bottomSpace.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(context, 16)));
        layout.addView(bottomSpace);
            
        // 显示对话框，添加导出按钮
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(R.string.track_statistics_title_short)
            .setView(layout)
            .setPositiveButton(R.string.confirm, null)
            .setNeutralButton(R.string.export, null)
            .show();
            
        // 设置导出按钮点击事件
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            exportStatisticsAsImage(context, layout, startTime);
            dialog.dismiss();
        });
    }
    
    /**
     * 添加统计项
     */
    private static void addStatisticItem(Context context, android.widget.LinearLayout parent, 
                                         String icon, String label, String value, String valueColor) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(context);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setPadding(0, dpToPx(context, 8), 0, dpToPx(context, 8));
        
        // 图标
        TextView iconView = new TextView(context);
        iconView.setText(icon);
        iconView.setTextSize(16);
        iconView.setPadding(0, 0, dpToPx(context, 8), 0);
        row.addView(iconView);
        
        // 标签
        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(15);
        labelView.setTextColor(android.graphics.Color.parseColor("#666666"));
        labelView.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            0, 
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 
            1.0f
        ));
        row.addView(labelView);
        
        // 值
        TextView valueView = new TextView(context);
        valueView.setText(value);
        valueView.setTextSize(15);
        valueView.setTypeface(valueView.getTypeface(), android.graphics.Typeface.BOLD);
        valueView.setTextColor(android.graphics.Color.parseColor(valueColor));
        row.addView(valueView);
        
        parent.addView(row);
    }
    
    /**
     * 创建分隔线
     */
    private static android.view.View createDivider(Context context) {
        android.view.View divider = new android.view.View(context);
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(context, 1)
        );
        params.setMargins(0, dpToPx(context, 4), 0, dpToPx(context, 4));
        divider.setLayoutParams(params);
        divider.setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"));
        return divider;
    }
    
    /**
     * dp转px
     */
    private static int dpToPx(Context context, float dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
    
    /**
     * 格式化时长
     */
    private static String formatDuration(Context context, long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        seconds = seconds % 60;
        minutes = minutes % 60;
        
        if (hours > 0) {
            return String.format(context.getString(R.string.hour_minute_format), hours, minutes);
        } else if (minutes > 0) {
            return String.format(context.getString(R.string.minute_second_format), minutes, seconds);
        } else {
            return String.format(context.getString(R.string.second_format), seconds);
        }
    }
    
    /**
     * 转换 StayPoint 列表为 LocationData 列表（用于距离计算）
     */
    private static List<com.RockiotTag.tag.model.LocationData> convertToLocationDataList(
        List<StayPoint> stayPoints) {
        
        List<com.RockiotTag.tag.model.LocationData> result = new java.util.ArrayList<>();
        for (StayPoint point : stayPoints) {
            com.RockiotTag.tag.model.LocationData data = 
                new com.RockiotTag.tag.model.LocationData(
                    "", // deviceId 不需要
                    point.getLatitude(),
                    point.getLongitude(),
                    point.getArriveTime()
                );
            result.add(data);
        }
        return result;
    }
    
    /**
     * 导出统计信息为图片
     */
    private static void exportStatisticsAsImage(Context context, android.widget.LinearLayout layout, long startTime) {
        try {
            // 测量布局
            int widthSpec = View.MeasureSpec.makeMeasureSpec(dpToPx(context, 400), View.MeasureSpec.EXACTLY);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            layout.measure(widthSpec, heightSpec);
            layout.layout(0, 0, layout.getMeasuredWidth(), layout.getMeasuredHeight());
            
            // 创建Bitmap
            Bitmap bitmap = Bitmap.createBitmap(
                layout.getMeasuredWidth(),
                layout.getMeasuredHeight(),
                Bitmap.Config.ARGB_8888
            );
            Canvas canvas = new Canvas(bitmap);
            layout.draw(canvas);
            
            // 保存图片到相册
            String fileName = "track_stats_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date(startTime)) + ".png";
            
            File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File rockiotDir = new File(picturesDir, "RockiotTag");
            if (!rockiotDir.exists()) {
                rockiotDir.mkdirs();
            }
            
            File imageFile = new File(rockiotDir, fileName);
            FileOutputStream fos = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
            bitmap.recycle();
            
            // 通知媒体库扫描
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScanIntent.setData(Uri.fromFile(imageFile));
            context.sendBroadcast(mediaScanIntent);
            
            Toast.makeText(context, R.string.export_success, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, context.getString(R.string.export_failed, e.getMessage()), 
                Toast.LENGTH_LONG).show();
        }
    }
}