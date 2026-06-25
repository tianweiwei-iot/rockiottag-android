package com.RockiotTag.tag.helper;

import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.util.LogUtil;
import com.amap.api.maps.AMap;

/**
 * MainActivity 高德地图 UI 配置（初始化与切换地图时复用）。
 */
public final class MainMapUiConfigurator {

    private static final String TAG = "MainMapUiConfigurator";

    public interface AmapHost {
        void setLastUserInteractionTime(long time);
        void updateCustomCompassRotation(float bearing);
    }

    private MainMapUiConfigurator() {
    }

    public static void applyMainAmapSettings(AppCompatActivity activity, AmapHost host, AMap aMap) {
        if (activity == null || host == null || aMap == null) {
            return;
        }

        int logoMargin = (int) (56 * activity.getResources().getDisplayMetrics().density);
        aMap.getUiSettings().setLogoBottomMargin(logoMargin);
        aMap.getUiSettings().setZoomControlsEnabled(false);
        aMap.getUiSettings().setCompassEnabled(false);
        aMap.getUiSettings().setMyLocationButtonEnabled(true);
        aMap.setMyLocationEnabled(false);
        aMap.getUiSettings().setScaleControlsEnabled(true);
        aMap.moveCamera(com.amap.api.maps.CameraUpdateFactory.zoomTo(17));

        aMap.setOnMapTouchListener(motionEvent ->
                host.setLastUserInteractionTime(System.currentTimeMillis()));

        aMap.setOnCameraChangeListener(new AMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(com.amap.api.maps.model.CameraPosition cameraPosition) {
                host.setLastUserInteractionTime(System.currentTimeMillis());
                host.updateCustomCompassRotation(cameraPosition.bearing);
            }

            @Override
            public void onCameraChangeFinish(com.amap.api.maps.model.CameraPosition cameraPosition) {
                host.setLastUserInteractionTime(System.currentTimeMillis());
                host.updateCustomCompassRotation(cameraPosition.bearing);
            }
        });

        try {
            boolean isDarkMode = activity.getSharedPreferences("app_settings",
                    android.content.Context.MODE_PRIVATE).getBoolean("dark_mode", false);
            if (isDarkMode) {
                aMap.setMapType(AMap.MAP_TYPE_NIGHT);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply dark map style: " + e.getMessage());
        }

        LogUtil.d(TAG, "Main AMap UI configured");
    }
}
