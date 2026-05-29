package com.RockiotTag.tag.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

/**
 * 图片加载工具类 - 使用Glide实现高效的图片加载和缓存
 * 
 * 功能：
 * 1. 自动内存和磁盘缓存
 * 2. 占位图和错误图支持
 * 3. 异步加载，避免主线程阻塞
 * 4. 自动管理生命周期，防止内存泄漏
 */
public class ImageLoader {
    
    private static final String TAG = "ImageLoader";
    
    /**
     * 加载网络图片
     * 
     * @param context 上下文
     * @param url 图片URL
     * @param imageView 目标ImageView
     */
    public static void loadImage(Context context, String url, ImageView imageView) {
        if (context == null || imageView == null) {
            return;
        }
        
        Glide.with(context)
            .load(url)
            .into(imageView);
    }
    
    /**
     * 加载网络图片（带占位图和错误图）
     * 
     * @param context 上下文
     * @param url 图片URL
     * @param imageView 目标ImageView
     * @param placeholderResId 占位图资源ID
     * @param errorResId 错误图资源ID
     */
    public static void loadImage(Context context, String url, ImageView imageView,
                                 @DrawableRes int placeholderResId,
                                 @DrawableRes int errorResId) {
        if (context == null || imageView == null) {
            return;
        }
        
        Glide.with(context)
            .load(url)
            .placeholder(placeholderResId)
            .error(errorResId)
            .into(imageView);
    }
    
    /**
     * 加载网络图片（带回调）
     * 
     * @param context 上下文
     * @param url 图片URL
     * @param imageView 目标ImageView
     * @param callback 加载回调
     */
    public static void loadImage(Context context, String url, ImageView imageView,
                                 LoadCallback callback) {
        if (context == null || imageView == null) {
            return;
        }
        
        Glide.with(context)
            .load(url)
            .listener(new RequestListener<Drawable>() {
                @Override
                public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                          Target<Drawable> target, boolean isFirstResource) {
                    if (callback != null) {
                        callback.onLoadFailed(e != null ? e.getMessage() : "Unknown error");
                    }
                    return false;
                }
                
                @Override
                public boolean onResourceReady(Drawable resource, Object model,
                                             Target<Drawable> target, DataSource dataSource,
                                             boolean isFirstResource) {
                    if (callback != null) {
                        callback.onLoadSuccess();
                    }
                    return false;
                }
            })
            .into(imageView);
    }
    
    /**
     * 加载圆形图片（适用于头像等）
     * 
     * @param context 上下文
     * @param url 图片URL
     * @param imageView 目标ImageView
     */
    public static void loadCircleImage(Context context, String url, ImageView imageView) {
        if (context == null || imageView == null) {
            return;
        }
        
        Glide.with(context)
            .load(url)
            .circleCrop()
            .into(imageView);
    }
    
    /**
     * 清除指定ImageView的图片加载请求
     * 
     * @param context 上下文
     * @param imageView 目标ImageView
     */
    public static void clearImage(Context context, ImageView imageView) {
        if (context == null || imageView == null) {
            return;
        }
        
        Glide.with(context).clear(imageView);
    }
    
    /**
     * 清除所有图片缓存
     * 
     * @param context 上下文
     */
    public static void clearCache(Context context) {
        if (context == null) {
            return;
        }
        
        new Thread(() -> {
            Glide.get(context).clearDiskCache();
        }).start();
        
        Glide.get(context).clearMemory();
    }
    
    /**
     * 图片加载回调接口
     */
    public interface LoadCallback {
        void onLoadSuccess();
        void onLoadFailed(String error);
    }
}
