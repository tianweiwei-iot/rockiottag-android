package com.RockiotTag.tag.util;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * 加载状态管理器
 * 统一管理应用中的加载状态，提供友好的加载提示
 */
public class LoadingManager {
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> loadingMessage = new MutableLiveData<>("加载中...");
    
    /**
     * 获取加载状态
     * @return LiveData<Boolean> true表示正在加载
     */
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
    
    /**
     * 获取加载消息
     * @return LiveData<String> 加载提示信息
     */
    public LiveData<String> getLoadingMessage() {
        return loadingMessage;
    }
    
    /**
     * 显示加载状态（默认消息）
     */
    public void showLoading() {
        showLoading("加载中...");
    }
    
    /**
     * 显示加载状态（自定义消息）
     * @param message 加载提示消息
     */
    public void showLoading(String message) {
        loadingMessage.postValue(message != null ? message : "加载中...");
        isLoading.postValue(true);
    }
    
    /**
     * 隐藏加载状态
     */
    public void hideLoading() {
        isLoading.postValue(false);
        loadingMessage.postValue("");
    }
    
    /**
     * 判断是否正在加载
     * @return true表示正在加载
     */
    public boolean isLoading() {
        Boolean loading = isLoading.getValue();
        return loading != null && loading;
    }
}
