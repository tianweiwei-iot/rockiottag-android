package com.RockiotTag.tag.util;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

/**
 * 生命周期感知的资源管理器基类
 * 
 * 自动管理资源的创建和释放，防止内存泄漏
 */
public abstract class LifecycleResourceManager implements DefaultLifecycleObserver {
    
    protected boolean isInitialized = false;
    
    /**
     * 初始化资源
     */
    public abstract void initialize();
    
    /**
     * 释放资源
     */
    public abstract void release();
    
    @Override
    public void onCreate(@androidx.annotation.NonNull LifecycleOwner owner) {
        if (!isInitialized) {
            initialize();
            isInitialized = true;
        }
    }
    
    @Override
    public void onDestroy(@androidx.annotation.NonNull LifecycleOwner owner) {
        if (isInitialized) {
            release();
            isInitialized = false;
        }
    }
}
