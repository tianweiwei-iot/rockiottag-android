package com.RockiotTag.tag.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Log;

import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;

/**
 * 地图标记工具类
 * 职责：统一处理地图标记的创建和样式
 */
public class MapMarkerHelper {
    private static final String TAG = "MapMarkerHelper";
    
    /**
     * 创建带文字的自定义标记（紫色水滴 + R文字）
     * @return BitmapDescriptor
     */
    public static BitmapDescriptor createCustomMarkerWithR() {
        try {
            // 获取高德地图默认的紫色水滴标记
            Bitmap markerBitmap = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA).getBitmap();
            
            // 加大标记尺寸1.5倍
            float scaleFactor = 1.5f;
            int width = (int) (markerBitmap.getWidth() * scaleFactor);
            int height = (int) (markerBitmap.getHeight() * scaleFactor);
            
            // 创建缩放后的Bitmap
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(markerBitmap, width, height, true);
            Bitmap resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            
            // 创建Canvas并绘制标记
            Canvas canvas = new Canvas(resultBitmap);
            canvas.drawBitmap(scaledBitmap, 0, 0, null);
            
            // 绘制更大的R文字在标记中间
            Paint textPaint = new Paint();
            textPaint.setColor(0xFFFFFFFF); // 白色
            textPaint.setTextSize(width / 1.8f); // 加大R字，与标记尺寸成正比
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setAntiAlias(true);
            
            String text = "R";
            float x = width / 2f;
            float y = height / 2.5f - (textPaint.descent() + textPaint.ascent()) / 2f;
            canvas.drawText(text, x, y, textPaint);
            
            return BitmapDescriptorFactory.fromBitmap(resultBitmap);
        } catch (Exception e) {
            Log.e(TAG, "Error creating custom marker: " + e.getMessage());
            // 如果创建失败，使用默认的紫色标记
            return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA);
        }
    }
    
    /**
     * 创建指定颜色的默认标记
     * @param hue 颜色色相值（HUE_BLUE, HUE_RED等）
     * @return BitmapDescriptor
     */
    public static BitmapDescriptor createDefaultMarker(float hue) {
        return BitmapDescriptorFactory.defaultMarker(hue);
    }
    
    /**
     * 获取标签对应的Emoji（委托给 DeviceTag）
     * @param tag 标签字符串
     * @return Emoji字符串
     */
    public static String getTagEmoji(String tag) {
        return com.RockiotTag.tag.model.DeviceTag.getEmoji(tag);
    }
    
    /**
     * 创建Emoji标记
     * @param emoji Emoji字符串
     * @return BitmapDescriptor
     */
    public static BitmapDescriptor createEmojiMarker(String emoji) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            
            Paint paint = new Paint();
            paint.setTextSize(60);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setAntiAlias(true);
            
            canvas.drawText(emoji, 50, 70, paint);
            
            return BitmapDescriptorFactory.fromBitmap(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "Error creating emoji marker: " + e.getMessage());
            return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE);
        }
    }
    
    /**
     * 创建起点/终点标记（水滴型，与Google地图样式统一）
     * @param text 标记文字
     * @param color 颜色
     * @return BitmapDescriptor
     */
    public static BitmapDescriptor createStartEndMarker(String text, int color) {
        try {
            // 改为水滴型，与Google地图样式统一
            int width = 100;
            int height = 120;
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            
            // 绘制水滴形状
            android.graphics.Path path = new android.graphics.Path();
            float centerX = width / 2f;
            float circleRadius = 40f;
            
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
            
            // 填充颜色
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(path, paint);
            
            // 添加白色边框增强视觉效果
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4);
            canvas.drawPath(path, paint);
            
            // 绘制文字
            paint.setColor(Color.WHITE);
            paint.setTextSize(32);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setStyle(Paint.Style.FILL);
            
            float textX = centerX;
            float textY = circleRadius + 8 - (paint.descent() + paint.ascent()) / 2f;
            canvas.drawText(text, textX, textY, paint);
            
            return BitmapDescriptorFactory.fromBitmap(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "Error creating start/end marker: " + e.getMessage());
            return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN);
        }
    }
    
    /**
     * 创建停留点标记（优化：缩小尺寸）
     * @param index 序号
     * @param duration 停留时长
     * @return BitmapDescriptor
     */
    public static BitmapDescriptor createStayPointMarker(int index, String duration) {
        try {
            // 缩小标记尺寸：120x120 -> 80x80
            Bitmap bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            
            // 绘制圆形背景
            Paint bgPaint = new Paint();
            bgPaint.setColor(Color.parseColor("#FF9800")); // 橙色
            bgPaint.setAntiAlias(true);
            canvas.drawCircle(40, 40, 32, bgPaint);
            
            // 绘制序号
            Paint textPaint = new Paint();
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(28);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setAntiAlias(true);
            
            canvas.drawText(String.valueOf(index), 40, 38, textPaint);
            
            // 绘制时长
            textPaint.setTextSize(16);
            canvas.drawText(duration, 40, 58, textPaint);
            
            return BitmapDescriptorFactory.fromBitmap(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "Error creating stay point marker: " + e.getMessage());
            return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE);
        }
    }
    
    /**
     * 创建数字标记（优化：缩小尺寸）
     * @param number 数字
     * @return BitmapDescriptor
     */
    public static BitmapDescriptor createNumberedMarker(int number) {
        try {
            // 缩小标记尺寸：80x80 -> 60x60
            Bitmap bitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            
            // 绘制圆形背景
            Paint bgPaint = new Paint();
            bgPaint.setColor(Color.RED);
            bgPaint.setAntiAlias(true);
            canvas.drawCircle(30, 30, 25, bgPaint);
            
            // 绘制数字
            Paint textPaint = new Paint();
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(28);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setAntiAlias(true);
            
            canvas.drawText(String.valueOf(number), 30, 38, textPaint);
            
            return BitmapDescriptorFactory.fromBitmap(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "Error creating numbered marker: " + e.getMessage());
            return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE);
        }
    }
    
    /**
     * 创建箭头标记（指示方向）
     * @param angle 角度
     * @return BitmapDescriptor
     */
    public static BitmapDescriptor createArrowMarker(double angle) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            
            // 旋转画布
            canvas.rotate((float) angle, 50, 50);
            
            // 绘制箭头
            Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStyle(Paint.Style.FILL);
            paint.setAntiAlias(true);
            
            // 绘制三角形箭头
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(50, 20);
            path.lineTo(30, 70);
            path.lineTo(70, 70);
            path.close();
            
            canvas.drawPath(path, paint);
            
            return BitmapDescriptorFactory.fromBitmap(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "Error creating arrow marker: " + e.getMessage());
            return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED);
        }
    }
}
