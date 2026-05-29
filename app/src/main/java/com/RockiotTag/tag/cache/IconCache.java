package com.RockiotTag.tag.cache;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;

import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;

/**
 * 图标缓存管理器
 * 使用LruCache缓存BitmapDescriptor，避免重复创建
 */
public class IconCache {
    private static final String TAG = "IconCache";
    
    // LRU缓存，最大缓存50个图标
    private static final int MAX_CACHE_SIZE = 50;
    
    private static IconCache instance;
    private final LruCache<String, BitmapDescriptor> cache;
    private final Context context;
    
    private IconCache(Context context) {
        this.context = context.getApplicationContext();
        this.cache = new LruCache<>(MAX_CACHE_SIZE);
    }
    
    public static synchronized IconCache getInstance(Context context) {
        if (instance == null) {
            instance = new IconCache(context);
        }
        return instance;
    }
    
    /**
     * 获取或创建图标
     * @param key 图标唯一标识（如资源ID、颜色等）
     * @param iconFactory 图标工厂函数
     * @return BitmapDescriptor
     */
    public BitmapDescriptor getOrCreate(String key, IconFactory iconFactory) {
        // 先从缓存中获取
        BitmapDescriptor descriptor = cache.get(key);
        if (descriptor != null) {
            return descriptor;
        }
        
        // 缓存未命中，创建新图标
        descriptor = iconFactory.create();
        if (descriptor != null) {
            cache.put(key, descriptor);
        }
        
        return descriptor;
    }
    
    /**
     * 从资源ID获取图标
     * @param resId 资源ID
     * @return BitmapDescriptor
     */
    public BitmapDescriptor getFromResource(int resId) {
        String key = "res_" + resId;
        return getOrCreate(key, () -> BitmapDescriptorFactory.fromResource(resId));
    }
    
    /**
     * 从位图获取图标
     * @param bitmap 位图
     * @return BitmapDescriptor
     */
    public BitmapDescriptor getFromBitmap(Bitmap bitmap) {
        String key = "bmp_" + bitmap.hashCode();
        return getOrCreate(key, () -> BitmapDescriptorFactory.fromBitmap(bitmap));
    }
    
    /**
     * 清除所有缓存
     */
    public void clear() {
        cache.evictAll();
    }
    
    /**
     * 获取缓存大小
     * @return 当前缓存的图标数量
     */
    public int getSize() {
        return cache.size();
    }
    
    /**
     * 图标工厂接口
     */
    public interface IconFactory {
        BitmapDescriptor create();
    }
}
