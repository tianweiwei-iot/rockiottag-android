package com.RockiotTag.tag.integration;

import android.util.Log;

import com.RockiotTag.tag.DatabaseHelper;
import com.RockiotTag.tag.NewApiService;
import com.RockiotTag.tag.model.DeviceLocation;
import com.RockiotTag.tag.util.LogUtil;

/**
 * 位置服务器同步管理器
 * 从 LocationOptimizationManager 拆分，专门负责将位置数据上传到服务器
 * 支持最大重试 10 次，失败后保存到离线缓存
 */
public class LocationSyncManager {

    private static final String TAG = "LocationSyncManager";
    private static final int MAX_RETRY = 10;
    private static final long RETRY_INTERVAL_MS = 20000;

    private final NewApiService apiService;
    private final OfflineCacheManager offlineCacheManager;
    private final DeviceMacMapper deviceMacMapper;
    private final java.util.concurrent.ExecutorService executor =
        java.util.concurrent.Executors.newSingleThreadExecutor();

    public LocationSyncManager(NewApiService apiService,
                                OfflineCacheManager offlineCacheManager,
                                DeviceMacMapper deviceMacMapper) {
        this.apiService = apiService;
        this.offlineCacheManager = offlineCacheManager;
        this.deviceMacMapper = deviceMacMapper;
    }

    /**
     * 立即同步位置到服务器
     * 最多重试 MAX_RETRY 次，每次间隔 RETRY_INTERVAL_MS
     * 失败后保存到离线缓存
     */
    public void syncLocationToServer(String deviceNum, DeviceLocation location) {
        if (apiService == null || location == null) {
            Log.w(TAG, "Cannot sync location: apiService or location is null");
            return;
        }

        executor.execute(() -> {
            int retryCount = 0;
            boolean success = false;

            while (!success && retryCount < MAX_RETRY) {
                try {
                    retryCount++;
                    if (retryCount > 1) {
                        LogUtil.d(TAG, "=== RETRY ATTEMPT #" + retryCount + "/" + MAX_RETRY + " ===");
                    } else {
                        LogUtil.d(TAG, "=== FIRST UPLOAD ATTEMPT ===");
                    }

                    String apiUrl = com.RockiotTag.tag.ApiConfig.getMyServerUrl(deviceNum);
                    LogUtil.d(TAG, "API URL: " + apiUrl);

                    NewApiService.ApiResponse response = apiService.syncLocation(
                        apiUrl,
                        deviceNum,
                        location.getLatitude(),
                        location.getLongitude(),
                        location.getBattery(),
                        location.getTimestamp()
                    );

                    if (response != null && response.isSuccess()) {
                        LogUtil.d(TAG, "UPLOAD SUCCESS on attempt #" + retryCount);
                        success = true;

                        // 删除离线缓存
                        if (offlineCacheManager != null) {
                            offlineCacheManager.removeOfflineCache(deviceNum, location.getTimestamp());
                        }

                        // 更新 MAC 地址
                        if (deviceMacMapper != null) {
                            deviceMacMapper.updateDeviceMacInDatabase(deviceNum, location);
                        }

                    } else {
                        Log.w(TAG, "UPLOAD FAILED on attempt #" + retryCount + "/" + MAX_RETRY);

                        // 保存到离线缓存
                        if (offlineCacheManager != null) {
                            offlineCacheManager.saveToOfflineCache(deviceNum, location);
                        }

                        // 未达到最大重试次数时等待
                        if (retryCount < MAX_RETRY) {
                            LogUtil.d(TAG, "Waiting " + (RETRY_INTERVAL_MS / 1000) + "s before retry...");
                            Thread.sleep(RETRY_INTERVAL_MS);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.e(TAG, "Retry interrupted, stopping upload thread");
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "UPLOAD ERROR on attempt #" + retryCount + "/" + MAX_RETRY, e);

                    if (offlineCacheManager != null) {
                        offlineCacheManager.saveToOfflineCache(deviceNum, location);
                    }

                    if (retryCount < MAX_RETRY) {
                        try {
                            Thread.sleep(RETRY_INTERVAL_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            Log.e(TAG, "Retry interrupted, stopping upload thread");
                            break;
                        }
                    }
                }
            }

            if (success) {
                LogUtil.d(TAG, "UPLOAD COMPLETED after " + retryCount + " attempt(s)");
            } else {
                Log.w(TAG, "UPLOAD FAILED after " + MAX_RETRY + " attempts, saved to offline cache");
            }
        });
    }
}
