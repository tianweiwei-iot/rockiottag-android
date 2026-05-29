package com.RockiotTag.tag.helper;

import android.util.Log;

import com.amap.api.maps.AMap;
import com.amap.api.maps.model.LatLng;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;

import java.util.List;

/**
 * 轨迹地图相机控制助手
 * 职责：封装地图相机移动和视角调整
 */
public class TrackCameraHelper {
    private static final String TAG = "TrackCameraHelper";
    
    /**
     * 高德地图 - 移动到指定位置
     */
    public static void moveCameraOnAMap(AMap aMap, LatLng position, float zoom) {
        if (aMap != null && position != null) {
            aMap.moveCamera(com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(position, zoom));
        }
    }
    
    /**
     * 高德地图 - 动画移动到指定位置
     */
    public static void animateCameraOnAMap(AMap aMap, LatLng position, float zoom) {
        if (aMap != null && position != null) {
            aMap.animateCamera(com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(position, zoom));
        }
    }
    
    /**
     * 高德地图 - 调整视角以包含所有点
     */
    public static void fitBoundsOnAMap(AMap aMap, List<LatLng> latLngList, int padding) {
        if (aMap == null || latLngList == null || latLngList.isEmpty()) {
            return;
        }
        
        if (latLngList.size() == 1) {
            aMap.moveCamera(com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(latLngList.get(0), 17));
        } else {
            com.amap.api.maps.model.LatLngBounds.Builder builder = 
                new com.amap.api.maps.model.LatLngBounds.Builder();
            for (LatLng latLng : latLngList) {
                builder.include(latLng);
            }
            com.amap.api.maps.model.LatLngBounds bounds = builder.build();
            aMap.moveCamera(com.amap.api.maps.CameraUpdateFactory.newLatLngBounds(bounds, padding));
        }
    }
    
    /**
     * Google 地图 - 移动到指定位置
     */
    public static void moveCameraOnGoogleMap(GoogleMap googleMap, 
                                             com.google.android.gms.maps.model.LatLng position, 
                                             float zoom) {
        if (googleMap != null && position != null) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, zoom));
        }
    }
    
    /**
     * Google 地图 - 动画移动到指定位置
     */
    public static void animateCameraOnGoogleMap(GoogleMap googleMap, 
                                                com.google.android.gms.maps.model.LatLng position, 
                                                float zoom) {
        if (googleMap != null && position != null) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, zoom));
        }
    }
    
    /**
     * Google 地图 - 调整视角以包含所有点
     */
    public static void fitBoundsOnGoogleMap(GoogleMap googleMap, 
                                            List<com.google.android.gms.maps.model.LatLng> latLngList, 
                                            int padding) {
        if (googleMap == null || latLngList == null || latLngList.isEmpty()) {
            return;
        }
        
        if (latLngList.size() == 1) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLngList.get(0), 17));
        } else {
            com.google.android.gms.maps.model.LatLngBounds.Builder builder = 
                new com.google.android.gms.maps.model.LatLngBounds.Builder();
            for (com.google.android.gms.maps.model.LatLng latLng : latLngList) {
                builder.include(latLng);
            }
            com.google.android.gms.maps.model.LatLngBounds bounds = builder.build();
            googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
        }
    }
}
