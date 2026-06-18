package com.RockiotTag.tag.viewmodel;

import android.app.Application;
import android.util.Log;
import com.RockiotTag.tag.util.LogUtil;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * WebViewMapActivity的ViewModel - 管理WebView地图加载的业务逻辑
 */
public class WebViewMapViewModel extends AndroidViewModel {
    private static final String TAG = "WebViewMapViewModel";
    
    // LiveData for UI observation
    private final MutableLiveData<String> pageUrl = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Integer> progress = new MutableLiveData<>(0);
    
    public WebViewMapViewModel(@NonNull Application application) {
        super(application);
    }
    
    /**
     * 获取页面URL
     */
    public LiveData<String> getPageUrl() {
        return pageUrl;
    }
    
    /**
     * 获取加载状态
     */
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
    
    /**
     * 获取错误信息
     */
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * 获取加载进度
     */
    public LiveData<Integer> getProgress() {
        return progress;
    }
    
    /**
     * 加载地图页面
     */
    public void loadMapPage(String url) {
        LogUtil.d(TAG, "Loading map page: " + url);
        
        if (url == null || url.isEmpty()) {
            errorMessage.setValue("无效的URL");
            return;
        }
        
        isLoading.setValue(true);
        progress.setValue(0);
        pageUrl.setValue(url);
    }
    
    /**
     * 更新加载进度
     */
    public void updateProgress(int newProgress) {
        progress.setValue(newProgress);
        
        if (newProgress >= 100) {
            isLoading.setValue(false);
        }
    }
    
    /**
     * 页面加载完成
     */
    public void onPageFinished() {
        isLoading.setValue(false);
        progress.setValue(100);
        LogUtil.d(TAG, "Page loading finished");
    }
    
    /**
     * 页面加载失败
     */
    public void onPageError(String error) {
        isLoading.setValue(false);
        errorMessage.setValue("页面加载失败: " + error);
        Log.e(TAG, "Page loading error: " + error);
    }
    
    /**
     * 刷新页面
     */
    public void refreshPage() {
        String currentUrl = pageUrl.getValue();
        if (currentUrl != null && !currentUrl.isEmpty()) {
            loadMapPage(currentUrl);
        }
    }
}
