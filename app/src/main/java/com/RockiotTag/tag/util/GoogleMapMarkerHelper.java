package com.RockiotTag.tag.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

/**
 * Google 地图标记图标创建工具类
 * 负责为 Google 地图创建各种类型的 Marker 图标
 */
public class GoogleMapMarkerHelper {
    private static final String TAG = "GoogleMapMarkerHelper";

    /**
     * 创建带R文字的自定义标记（Google 地图）
     */
    public static com.google.android.gms.maps.model.BitmapDescriptor createCustomMarkerWithR() {
        try {
            int width = 80;
            int height = 100;
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            
            // 绘制水滴形状
            android.graphics.Path path = new android.graphics.Path();
            float centerX = width / 2f;
            float circleRadius = 32f;
            
            path.moveTo(centerX, height - 5);
            path.cubicTo(
                centerX - 25, height - 40,
                centerX - circleRadius, circleRadius + 15,
                centerX - circleRadius, circleRadius
            );
            path.arcTo(
                new android.graphics.RectF(centerX - circleRadius, 5, centerX + circleRadius, 5 + circleRadius * 2),
                180, 180
            );
            path.cubicTo(
                centerX + circleRadius, circleRadius + 15,
                centerX + 25, height - 40,
                centerX, height - 5
            );
            path.close();
            
            // 填充紫色
            paint.setColor(Color.parseColor("#9C27B0"));
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(path, paint);
            
            // 白色边框
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            canvas.drawPath(path, paint);
            
            // 绘制R文字
            paint.setColor(Color.WHITE);
            paint.setTextSize(36);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setStyle(Paint.Style.FILL);
            
            float textX = centerX;
            float textY = circleRadius + 5 - (paint.descent() + paint.ascent()) / 2f;
            canvas.drawText("R", textX, textY, paint);
            
            return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "Error creating custom marker with R for Google Map: " + e.getMessage());
            return com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_MAGENTA);
        }
    }

    /**
     * 使用 emoji 创建播放图标（Google 地图）
     */
    public static com.google.android.gms.maps.model.BitmapDescriptor createEmojiMarker(String emoji) {
        int size = 80;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        
        // 绘制背景圆形（橙色）
        paint.setColor(Color.parseColor("#FF5722"));
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5, paint);
        
        // 绘制白色边框
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5, paint);
        
        // 绘制 emoji 图标
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(48); // emoji 字体大小
        paint.setTextAlign(Paint.Align.CENTER);
        
        // 计算 emoji 垂直居中位置
        Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        float textHeight = fontMetrics.bottom - fontMetrics.top;
        float textY = size / 2f + textHeight / 2 - fontMetrics.bottom;
        
        canvas.drawText(emoji, size / 2f, textY, paint);
        
        return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    /**
     * 创建起点/终点标记（Google 地图，优化：增大尺寸和增强视觉差异）
     */
    public static com.google.android.gms.maps.model.BitmapDescriptor createStartEndMarker(String text, int color) {
        try {
            // 增大起点/终点标记尺寸以突出显示：80x100 -> 100x120
            int width = 100;
            int height = 120;
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            
            android.graphics.Path path = new android.graphics.Path();
            float centerX = width / 2f;
            float circleRadius = 40f; // 增大半径
            
            path.moveTo(centerX, height - 5);
            path.cubicTo(
                centerX - 30, height - 45,
                centerX - circleRadius, circleRadius + 18,
                centerX - circleRadius, circleRadius
            );
            path.arcTo(
                new android.graphics.RectF(centerX - circleRadius, 5, centerX + circleRadius, 5 + circleRadius * 2),
                180, 180
            );
            path.cubicTo(
                centerX + circleRadius, circleRadius + 18,
                centerX + 30, height - 45,
                centerX, height - 5
            );
            path.close();
            
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(path, paint);
            
            // 添加白色边框增强视觉效果
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4);
            canvas.drawPath(path, paint);
            
            paint.setColor(Color.WHITE);
            paint.setTextSize(32);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setStyle(Paint.Style.FILL);
            
            float textX = centerX;
            float textY = circleRadius + 8 - (paint.descent() + paint.ascent()) / 2f;
            canvas.drawText(text, textX, textY, paint);
            
            return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "Error creating start/end marker for Google Map: " + e.getMessage());
            return com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_RED);
        }
    }

    /**
     * 创建数字标记（Google 地图，优化：缩小尺寸）
     */
    public static com.google.android.gms.maps.model.BitmapDescriptor createNumberedMarker(int number) {
        try {
            // 缩小标记尺寸：80 -> 60
            int size = 60;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            
            Paint circlePaint = new Paint();
            circlePaint.setColor(Color.RED);
            circlePaint.setAntiAlias(true);
            circlePaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5, circlePaint);
            
            Paint strokePaint = new Paint();
            strokePaint.setColor(Color.WHITE);
            strokePaint.setAntiAlias(true);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(2);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5, strokePaint);
            
            Paint textPaint = new Paint();
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(24);
            textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setAntiAlias(true);
            
            String text = String.valueOf(number);
            float x = size / 2f;
            float y = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
            canvas.drawText(text, x, y, textPaint);
            
            return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "Error creating numbered marker for Google Map: " + e.getMessage());
            return com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_RED);
        }
    }

    /**
     * 创建停留点标记（Google 地图，优化：缩小尺寸）
     */
    public static com.google.android.gms.maps.model.BitmapDescriptor createStayPointMarker(int number, String duration) {
        try {
            // 缩小标记尺寸：80 -> 60
            int size = 60;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            
            Paint circlePaint = new Paint();
            circlePaint.setColor(Color.parseColor("#FF9800"));
            circlePaint.setAntiAlias(true);
            circlePaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, circlePaint);
            
            Paint strokePaint = new Paint();
            strokePaint.setColor(Color.WHITE);
            strokePaint.setAntiAlias(true);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(2);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, strokePaint);
            
            Paint numberPaint = new Paint();
            numberPaint.setColor(Color.WHITE);
            numberPaint.setTextSize(18);
            numberPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            numberPaint.setTextAlign(Paint.Align.CENTER);
            numberPaint.setAntiAlias(true);
            
            String numberText = String.valueOf(number);
            float x = size / 2f;
            float y = size / 2f - 6 - (numberPaint.descent() + numberPaint.ascent()) / 2f;
            canvas.drawText(numberText, x, y, numberPaint);
            
            Paint durationPaint = new Paint();
            durationPaint.setColor(Color.WHITE);
            durationPaint.setTextSize(11);
            durationPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            durationPaint.setTextAlign(Paint.Align.CENTER);
            durationPaint.setAntiAlias(true);
            
            float durationY = size / 2f + 9 - (durationPaint.descent() + durationPaint.ascent()) / 2f;
            canvas.drawText(duration, x, durationY, durationPaint);
            
            return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "Error creating stay point marker for Google Map: " + e.getMessage());
            return com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_ORANGE);
        }
    }

    /**
     * 创建方向箭头标记（Google 地图）
     */
    public static com.google.android.gms.maps.model.BitmapDescriptor createArrowMarker(double angle) {
        int size = 29;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        
        Paint borderPaint = new Paint();
        borderPaint.setColor(0xFFFFFFFF);
        borderPaint.setStyle(Paint.Style.FILL);
        borderPaint.setAntiAlias(true);
        
        Paint fillPaint = new Paint();
        fillPaint.setColor(0xFFFF5722);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setAntiAlias(true);
        
        canvas.save();
        canvas.translate(size / 2f, size / 2f);
        canvas.rotate((float) angle);
        
        float halfWidth = size / 4f;
        float height = size / 3f;
        
        android.graphics.Path borderPath = new android.graphics.Path();
        borderPath.moveTo(0, -height - 1);
        borderPath.lineTo(-halfWidth - 1, height + 1);
        borderPath.lineTo(halfWidth + 1, height + 1);
        borderPath.close();
        canvas.drawPath(borderPath, borderPaint);
        
        android.graphics.Path arrowPath = new android.graphics.Path();
        arrowPath.moveTo(0, -height);
        arrowPath.lineTo(-halfWidth, height);
        arrowPath.lineTo(halfWidth, height);
        arrowPath.close();
        canvas.drawPath(arrowPath, fillPaint);
        
        canvas.restore();
        
        return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap);
    }
}
