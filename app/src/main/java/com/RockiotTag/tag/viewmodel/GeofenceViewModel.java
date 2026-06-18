package com.RockiotTag.tag.viewmodel;

import android.app.Application;
import android.util.Log;
import com.RockiotTag.tag.util.LogUtil;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * GeofenceActivity的ViewModel - 管理电子围栏的业务逻辑
 */
public class GeofenceViewModel extends AndroidViewModel {
    private static final String TAG = "GeofenceViewModel";
    
    // LiveData for UI observation
    private final MutableLiveData<Float> geofenceRadius = new MutableLiveData<>(100f);
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saveSuccess = new MutableLiveData<>(false);
    
    public GeofenceViewModel(@NonNull Application application) {
        super(application);
    }
    
    /**
     * 获取电子围栏半径
     */
    public LiveData<Float> getGeofenceRadius() {
        return geofenceRadius;
    }
    
    /**
     * 获取状态消息
     */
    public LiveData<String> getStatusMessage() {
        return statusMessage;
    }
    
    /**
     * 获取保存成功状态
     */
    public LiveData<Boolean> getSaveSuccess() {
        return saveSuccess;
    }
    
    /**
     * 设置电子围栏半径
     */
    public void setGeofenceRadius(float radius) {
        if (radius > 0) {
            geofenceRadius.setValue(radius);
            LogUtil.d(TAG, "Geofence radius set to: " + radius);
        } else {
            Log.w(TAG, "Invalid radius: " + radius);
        }
    }
    
    /**
     * 保存电子围栏设置
     */
    public interface SaveCallback {
        void onSuccess(float radius);
        void onError(String error);
    }
    
    public void saveGeofenceSettings(float radius, SaveCallback callback) {
        LogUtil.d(TAG, "Saving geofence settings, radius: " + radius);
        
        if (radius <= 0) {
            String error = "无效的半径值";
            statusMessage.postValue(error);
            
            if (callback != null) {
                callback.onError(error);
            }
            return;
        }
        
        // 更新半径
        geofenceRadius.setValue(radius);
        
        // 模拟保存操作（实际项目中可能需要保存到服务器或本地数据库）
        new Thread(() -> {
            try {
                // 这里可以添加实际的保存逻辑，例如：
                // - 保存到 SharedPreferences
                // - 保存到数据库
                // - 同步到服务器
                
                LogUtil.d(TAG, "Geofence settings saved successfully");
                
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    statusMessage.postValue("保存成功");
                    saveSuccess.setValue(true);
                    
                    if (callback != null) {
                        callback.onSuccess(radius);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error saving geofence settings: " + e.getMessage(), e);
                
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    String error = "保存失败: " + e.getMessage();
                    statusMessage.postValue(error);
                    
                    if (callback != null) {
                        callback.onError(error);
                    }
                });
            }
        }).start();
    }
    
    /**
     * 验证半径值
     */
    public boolean validateRadius(String radiusText) {
        if (radiusText == null || radiusText.trim().isEmpty()) {
            return false;
        }
        
        try {
            float radius = Float.parseFloat(radiusText.trim());
            return radius > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 解析半径值
     */
    public float parseRadius(String radiusText) {
        try {
            return Float.parseFloat(radiusText.trim());
        } catch (NumberFormatException e) {
            return 100f; // 默认值
        }
    }
}
