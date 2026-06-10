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
import com.RockiotTag.tag.model.LocationData;
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
        TextView totalDistanceText,
        String deviceNum,
        String deviceName,
        long selectedDateMillis,
        List<LocationData> allLocationRecords) {
            
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
        titleView.setPadding(0, 0, 0, dpToPx(context, 8));
        layout.addView(titleView);
            
        // 添加设备信息（昵称 + 设备号）
        if (deviceName != null && !deviceName.isEmpty()) {
            android.widget.LinearLayout deviceInfoRow = new android.widget.LinearLayout(context);
            deviceInfoRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            deviceInfoRow.setPadding(0, 0, 0, dpToPx(context, 12));
            deviceInfoRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView nameView = new TextView(context);
            nameView.setText(deviceName);
            nameView.setTextSize(15);
            nameView.setTypeface(nameView.getTypeface(), android.graphics.Typeface.BOLD);
            nameView.setTextColor(Color.parseColor("#2196F3"));
            nameView.setPadding(0, 0, dpToPx(context, 12), 0);
            deviceInfoRow.addView(nameView);

            TextView numView = new TextView(context);
            numView.setText(deviceNum);
            numView.setTextSize(12);
            numView.setTextColor(Color.parseColor("#999999"));
            android.widget.LinearLayout.LayoutParams numParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            );
            numParams.gravity = android.view.Gravity.CENTER_VERTICAL;
            numView.setLayoutParams(numParams);
            deviceInfoRow.addView(numView);

            layout.addView(deviceInfoRow);
        }
            
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
            
        // 显示对话框，添加导出按钮和详细按钮
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(R.string.track_statistics_title_short)
            .setView(layout)
            .setPositiveButton(R.string.confirm, null)
            .setNeutralButton(R.string.export, null)
            .setNegativeButton(R.string.detail, null)
            .show();
            
        // 设置导出按钮点击事件
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            exportStatisticsAsImage(context, layout, startTime);
            dialog.dismiss();
        });
        
        // 设置详细按钮点击事件
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
            showDetailDataDialog(context, allLocationRecords, deviceNum, selectedDateMillis);
            dialog.dismiss();
        });
    }
    
    /**
     * 显示详细数据对话框（使用原始未优化的数据）
     */
    private static void showDetailDataDialog(Context context, List<LocationData> allLocationRecords, 
                                              String deviceNum, long selectedDateMillis) {
        // 创建详细数据布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(context, 16), dpToPx(context, 16), dpToPx(context, 16), dpToPx(context, 16));
        
        // 添加标题
        TextView titleView = new TextView(context);
        String dateStr = TimeFormatter.formatDate(selectedDateMillis);
        titleView.setText(context.getString(R.string.detail_data_title, deviceNum, dateStr));
        titleView.setTextSize(16);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        titleView.setTextColor(Color.parseColor("#333333"));
        titleView.setPadding(0, 0, 0, dpToPx(context, 12));
        layout.addView(titleView);
        
        // 添加数据统计
        TextView summaryView = new TextView(context);
        summaryView.setText(context.getString(R.string.detail_data_summary, allLocationRecords.size()));
        summaryView.setTextSize(14);
        summaryView.setTextColor(Color.parseColor("#666666"));
        summaryView.setPadding(0, 0, 0, dpToPx(context, 12));
        layout.addView(summaryView);
        
        // 创建滚动视图
        android.widget.ScrollView scrollView = new android.widget.ScrollView(context);
        scrollView.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(context, 300)));
        
        // 创建数据内容布局
        android.widget.LinearLayout dataLayout = new android.widget.LinearLayout(context);
        dataLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        dataLayout.setPadding(dpToPx(context, 8), dpToPx(context, 8), dpToPx(context, 8), dpToPx(context, 8));
        
        // 添加每条原始数据记录
        for (int i = 0; i < allLocationRecords.size(); i++) {
            LocationData point = allLocationRecords.get(i);
            TextView dataRow = new TextView(context);
            String timeStr = TimeFormatter.formatTimeHM(point.getTimestamp());
            String latStr = String.format("%.6f", point.getLatitude());
            String lngStr = String.format("%.6f", point.getLongitude());
            
            dataRow.setText(String.format(context.getString(R.string.detail_data_row_format), 
                i + 1, timeStr, latStr, lngStr));
            dataRow.setTextSize(12);
            dataRow.setTextColor(Color.parseColor("#444444"));
            dataRow.setPadding(0, dpToPx(context, 4), 0, dpToPx(context, 4));
            dataLayout.addView(dataRow);
        }
        
        scrollView.addView(dataLayout);
        layout.addView(scrollView);
        
        // 显示对话框
        androidx.appcompat.app.AlertDialog detailDialog = new androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(R.string.detail_data)
            .setView(layout)
            .setPositiveButton(R.string.confirm, null)
            .setNegativeButton(R.string.export_txt, null)
            .show();
        
        // 设置导出txt按钮点击事件
        detailDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
            exportDataAsTxt(context, allLocationRecords, deviceNum, selectedDateMillis);
            detailDialog.dismiss();
        });
    }
    
    /**
     * 导出数据为txt文件（使用原始未优化的数据）
     */
    private static void exportDataAsTxt(Context context, List<LocationData> allLocationRecords, 
                                         String deviceNum, long selectedDateMillis) {
        try {
            // 格式化日期
            String dateStr = new SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                .format(new Date(selectedDateMillis));
            String dateDisplayStr = TimeFormatter.formatDate(selectedDateMillis);
            
            // 创建文件名
            String fileName = "track_data_" + deviceNum + "_" + dateStr + ".txt";
            
            // 创建保存目录
            File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File rockiotDir = new File(documentsDir, "RockiotTag");
            if (!rockiotDir.exists()) {
                rockiotDir.mkdirs();
            }
            
            File txtFile = new File(rockiotDir, fileName);
            
            // 构建文件内容
            StringBuilder content = new StringBuilder();
            content.append("========================================\n");
            content.append(context.getString(R.string.export_txt_header, deviceNum, dateDisplayStr));
            content.append("========================================\n\n");
            content.append(context.getString(R.string.export_txt_summary, allLocationRecords.size()));
            content.append("\n\n");
            content.append("----------------------------------------\n");
            content.append(context.getString(R.string.export_txt_data_header));
            content.append("----------------------------------------\n");
            
            for (int i = 0; i < allLocationRecords.size(); i++) {
                LocationData point = allLocationRecords.get(i);
                String timeStr = TimeFormatter.formatTimeHM(point.getTimestamp());
                String latStr = String.format("%.8f", point.getLatitude());
                String lngStr = String.format("%.8f", point.getLongitude());
                
                content.append(String.format(context.getString(R.string.export_txt_row_format),
                    i + 1, timeStr, latStr, lngStr));
            }
            
            content.append("\n========================================\n");
            String exportTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(System.currentTimeMillis()));
            content.append(String.format(context.getString(R.string.export_txt_footer), exportTime));
            content.append("========================================\n");
            
            // 写入文件
            java.io.FileWriter writer = new java.io.FileWriter(txtFile);
            writer.write(content.toString());
            writer.flush();
            writer.close();
            
            // 通知媒体库扫描
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScanIntent.setData(Uri.fromFile(txtFile));
            context.sendBroadcast(mediaScanIntent);
            
            Toast.makeText(context, 
                context.getString(R.string.export_txt_success, txtFile.getAbsolutePath()), 
                Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(context, 
                context.getString(R.string.export_txt_failed, e.getMessage()), 
                Toast.LENGTH_LONG).show();
        }
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