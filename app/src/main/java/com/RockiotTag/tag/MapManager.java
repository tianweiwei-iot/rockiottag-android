package com.RockiotTag.tag;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;

public class MapManager implements OnMapReadyCallback {
    private static final String TAG = "MapManager";
    private Handler handler;
    private boolean isCheckingMapLoad = false;
    
    public static final String MAP_PROVIDER_AMAP = "amap";
    public static final String MAP_PROVIDER_GOOGLE = "google";
    
    private Context context;
    private MapView amapView;
    private SupportMapFragment googleMapFragment;
    private AMap amap;
    private GoogleMap googleMap;
    private String currentProvider;
    private MapCallback callback;
    private double defaultLat = 22.543611;
    private double defaultLng = 113.881944;
    private boolean googleMapReady = false;
    private double targetLat = 0;
    private double targetLng = 0;
    private float targetZoom = 17;
    private boolean userHasInteractedWithMap = false; // 标记用户是否已经手动交互过地图
    
    public interface MapCallback {
        void onMapReady();
        void onMapClick(double latitude, double longitude);
    }
    
    public MapManager(Context context, MapView amapView, SupportMapFragment googleMapFragment) {
        // 关键修复：使用 ApplicationContext 防止内存泄漏
        this.context = context.getApplicationContext();
        this.amapView = amapView;
        this.googleMapFragment = googleMapFragment;
        this.handler = new Handler();
        
        android.content.SharedPreferences prefs = this.context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        this.currentProvider = prefs.getString("map_provider", MAP_PROVIDER_AMAP);
        Log.d(TAG, "MapManager initialized, current provider: " + currentProvider);
        Log.d(TAG, "googleMapFragment: " + googleMapFragment);
        checkNetworkStatus();
    }
    
    private void checkNetworkStatus() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
                Log.d(TAG, "Network status: " + (isConnected ? "Connected" : "Disconnected"));
                if (!isConnected) {
                    Log.w(TAG, "No network connection - Google Maps may not load!");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking network status: " + e.getMessage(), e);
        }
    }
    
    public void setCallback(MapCallback callback) {
        this.callback = callback;
    }
    
    public void initAmap() {
        Log.d(TAG, "initAmap called, amapView: " + amapView);
        if (amapView != null) {
            amap = amapView.getMap();
            Log.d(TAG, "AMap instance: " + amap);
            if (amap != null) {
                amap.getUiSettings().setMyLocationButtonEnabled(false);
                amap.setMyLocationEnabled(false);
                amap.getUiSettings().setCompassEnabled(false);
                amap.getUiSettings().setScaleControlsEnabled(true);
                amap.moveCamera(CameraUpdateFactory.zoomTo(17));
                
                amap.setOnMapClickListener(new AMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(com.amap.api.maps.model.LatLng latLng) {
                        if (callback != null) {
                            callback.onMapClick(latLng.latitude, latLng.longitude);
                        }
                    }
                });
                Log.d(TAG, "AMap initialized successfully");
            }
        }
    }
    
    public void initGoogleMap() {
        Log.d(TAG, "initGoogleMap called, googleMapFragment: " + googleMapFragment);
        if (googleMapFragment != null) {
            googleMapFragment.getMapAsync(this);
            Log.d(TAG, "Google Map getMapAsync called");
        } else {
            Log.e(TAG, "googleMapFragment is null!");
        }
    }
    
    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.d(TAG, "onMapReady called, googleMap: " + googleMap);
        this.googleMap = googleMap;
        this.googleMapReady = true;
        
        if (googleMap != null) {
            try {
                Log.d(TAG, "Configuring Google Map UI settings...");
                googleMap.getUiSettings().setMyLocationButtonEnabled(false);
                googleMap.getUiSettings().setCompassEnabled(true); // 启用指南针
                googleMap.getUiSettings().setMapToolbarEnabled(false);
                googleMap.getUiSettings().setZoomControlsEnabled(true);
                googleMap.getUiSettings().setRotateGesturesEnabled(true); // 启用旋转手势
                googleMap.getUiSettings().setTiltGesturesEnabled(true); // 启用倾斜手势
                
                // 设置地图语言和地区
                String languageCode = getMapLanguageCode();
                String regionCode = getRegionCode();
                Log.d(TAG, "Setting map language: " + languageCode + ", region: " + regionCode);
                
                // Google地图没有内置标尺控件，但可以通过其他方式显示
                // 注意：Google Maps Android API 不直接提供标尺UI控件
                
                // 设置默认地图类型为普通地图
                googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                Log.d(TAG, "Map type set to NORMAL");
                
                // 添加地图加载完成监听
                googleMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
                    @Override
                    public void onMapLoaded() {
                        Log.d(TAG, "Google Map loaded successfully!");
                        isCheckingMapLoad = false;
                        if (context != null) {
                            Toast.makeText(context, R.string.google_map_loaded, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                
                // 添加相机移动监听
                googleMap.setOnCameraIdleListener(new GoogleMap.OnCameraIdleListener() {
                    @Override
                    public void onCameraIdle() {
                        Log.d(TAG, "Camera is idle at: " + googleMap.getCameraPosition().target);
                    }
                });
                
                // 添加相机移动开始监听 - 检测用户手动交互
                googleMap.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
                    @Override
                    public void onCameraMoveStarted(int reason) {
                        Log.d(TAG, "Camera move started, reason: " + reason);
                        // REASON_GESTURE = 1 (用户手势)
                        // REASON_API_ANIMATION = 2 (API动画)
                        // REASON_DEVELOPER_ANIMATION = 3 (开发者动画)
                        if (reason == 1) {
                            userHasInteractedWithMap = true;
                            Log.d(TAG, "User manually interacted with map - disabling auto camera moves");
                        }
                    }
                });
                
                Log.d(TAG, "Setting initial location: lat=" + defaultLat + ", lng=" + defaultLng);
                com.google.android.gms.maps.model.LatLng defaultLatLng =
                    new com.google.android.gms.maps.model.LatLng(defaultLat, defaultLng);
                // 完全禁用自动移动相机，由用户手动控制地图位置
                Log.d(TAG, "Auto camera movement disabled - user controls map position");
                
                // 添加地图点击事件监听以确保地图已激活
                googleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(com.google.android.gms.maps.model.LatLng latLng) {
                        Log.d(TAG, "Google Map clicked at: " + latLng.latitude + ", " + latLng.longitude);
                        if (callback != null) {
                            callback.onMapClick(latLng.latitude, latLng.longitude);
                        }
                    }
                });
                
                Log.d(TAG, "Google Map initialized successfully");
                
                if (callback != null) {
                    callback.onMapReady();
                }
                
                // 如果当前是谷歌地图，立即确保视图可见
                if (isGoogleMap()) {
                    Log.d(TAG, "Current provider is Google Map, ensuring view is visible...");
                    if (googleMapFragment != null && googleMapFragment.getView() != null) {
                        googleMapFragment.getView().setVisibility(View.VISIBLE);
                        Log.d(TAG, "Google Map view visibility set to VISIBLE");
                    }
                }
                

                
                // 启动地图加载监控
                startMapLoadMonitoring();
            } catch (Exception e) {
                Log.e(TAG, "Error initializing Google Map: " + e.getMessage(), e);
                e.printStackTrace();
                if (context != null) {
                    Toast.makeText(context, context.getString(R.string.google_map_init_error, e.getMessage()), Toast.LENGTH_LONG).show();
                }
            }
        } else {
            Log.e(TAG, "Google Map is null in onMapReady!");
        }
    }
    
    private void startMapLoadMonitoring() {
        if (isCheckingMapLoad) {
            Log.d(TAG, "Map load monitoring already in progress");
            return;
        }
        
        isCheckingMapLoad = true;
        Log.d(TAG, "Starting map load monitoring (timeout in 15 seconds)");
        
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isCheckingMapLoad) {
                    Log.w(TAG, "Map load timeout - tiles may not have loaded!");
                    isCheckingMapLoad = false;
                    
                    if (context != null) {
                        Toast.makeText(context, R.string.google_map_check_settings, Toast.LENGTH_LONG).show();
                    }
                    
                    // 尝试多种方法来触发地图重新加载
                    if (googleMap != null) {
                        try {
                            Log.d(TAG, "Attempting map reload strategies...");
                            
                            // 策略1：切换地图类型
                            Log.d(TAG, "Strategy 1: Toggle map type");
                            int currentType = googleMap.getMapType();
                            googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (googleMap != null) {
                                        googleMap.setMapType(currentType);
                                        Log.d(TAG, "Map type restored");
                                    }
                                }
                            }, 1000);
                            
                            // 策略2：禁用（不再强制移动相机，避免干扰用户）
                            // 之前的策略会强制移动相机到错误位置，导致用户体验差
                            Log.d(TAG, "Strategy 2: Disabled - do not force camera movement");
                            
                            // 策略3：尝试清除并重新设置
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (googleMap != null) {
                                        Log.d(TAG, "Strategy 3: Attempting map reset");
                                        googleMap.clear();
                                        Log.d(TAG, "Map cleared");
                                    }
                                }
                            }, 5000);
                            
                        } catch (Exception e) {
                            Log.e(TAG, "Error triggering map reload: " + e.getMessage(), e);
                        }
                    }
                    
                    // 记录诊断信息
                    Log.w(TAG, "=== DIAGNOSTIC INFO ===");
                    Log.w(TAG, "Possible issues:");
                    Log.w(TAG, "1. API Key may be invalid or not properly configured");
                    Log.w(TAG, "2. Maps SDK for Android may not be enabled in Google Cloud Console");
                    Log.w(TAG, "3. API Key may not have proper restrictions (package name/SHA-1)");
                    Log.w(TAG, "4. Network/VPN may not have access to Google services");
                    Log.w(TAG, "5. Check Logcat for Google Maps SDK specific error messages");
                }
            }
        }, 15000); // 15秒超时
    }
    
    public boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(context);
        
        if (resultCode == ConnectionResult.SUCCESS) {
            Log.d(TAG, "Google Play Services is available");
            return true;
        } else {
            Log.e(TAG, "Google Play Services not available, result code: " + resultCode);
            String errorMessage = apiAvailability.getErrorString(resultCode);
            Log.e(TAG, "Error message: " + errorMessage);
            return false;
        }
    }
    
    public void switchToAmap() {
        Log.d(TAG, "switchToAmap called");
        currentProvider = MAP_PROVIDER_AMAP;
        saveMapProvider();
        
        if (amapView != null) {
            amapView.setVisibility(View.VISIBLE);
            Log.d(TAG, "AMap view set to VISIBLE");
        }
        if (googleMapFragment != null && googleMapFragment.getView() != null) {
            googleMapFragment.getView().setVisibility(View.GONE);
            Log.d(TAG, "Google Map view set to GONE");
        } else {
            Log.w(TAG, "Google Map fragment or view is null when switching to AMap");
        }
    }
    
    public void switchToGoogleMap() {
        Log.d(TAG, "switchToGoogleMap called, googleMapReady: " + googleMapReady);
        currentProvider = MAP_PROVIDER_GOOGLE;
        saveMapProvider();
        
        if (amapView != null) {
            amapView.setVisibility(View.GONE);
            Log.d(TAG, "AMap view set to GONE");
        }
        
        if (googleMapFragment != null) {
            if (googleMapFragment.getView() != null) {
                googleMapFragment.getView().setVisibility(View.VISIBLE);
                Log.d(TAG, "Google Map view set to VISIBLE");
            } else {
                Log.w(TAG, "Google Map fragment view is null, fragment: " + googleMapFragment);
            }
            
            if (!googleMapReady) {
                Log.w(TAG, "Google Map not ready yet, re-initializing...");
                initGoogleMap();
            } else {
                Log.d(TAG, "Google Map is already ready, re-applying UI settings...");
                // 重新应用UI设置，确保指南针等控件启用
                if (googleMap != null) {
                    try {
                        googleMap.getUiSettings().setCompassEnabled(true);
                        googleMap.getUiSettings().setRotateGesturesEnabled(true);
                        googleMap.getUiSettings().setTiltGesturesEnabled(true);
                        googleMap.getUiSettings().setZoomControlsEnabled(true);
                        Log.d(TAG, "Google Map UI settings re-applied");
                        
                        // 禁用自动旋转地图以显示指南针，避免地图跳转
                        Log.d(TAG, "Auto rotation disabled to prevent map jumping");
                    } catch (Exception e) {
                        Log.e(TAG, "Error re-applying Google Map UI settings: " + e.getMessage(), e);
                    }
                }
            }
        } else {
            Log.e(TAG, "Cannot show Google Map - fragment is null");
            if (context != null) {
                Toast.makeText(context, R.string.google_map_init_failed_check, Toast.LENGTH_LONG).show();
            }
        }
    }
    
    public String getCurrentProvider() {
        return currentProvider;
    }
    
    public boolean isAmap() {
        return MAP_PROVIDER_AMAP.equals(currentProvider);
    }
    
    public boolean isGoogleMap() {
        return MAP_PROVIDER_GOOGLE.equals(currentProvider);
    }
    
    public void moveCamera(double latitude, double longitude, float zoom) {
        if (isAmap() && amap != null) {
            LatLng latLng = com.RockiotTag.tag.CoordinateUtils.wgs84ToGcj02(latitude, longitude);
            amap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));
        } else if (isGoogleMap() && googleMap != null) {
            // 完全禁用谷歌地图的自动相机移动
            Log.d(TAG, "Google Map - Auto camera move COMPLETELY DISABLED. Requested: " + latitude + ", " + longitude);
            // 不执行任何相机移动操作
        } else {
            Log.w(TAG, "moveCamera called but map is null, provider: " + currentProvider);
        }
    }
    
    public void setMapType(int type) {
        if (isAmap() && amap != null) {
            amap.setMapType(type);
        } else if (isGoogleMap() && googleMap != null) {
            if (type == AMap.MAP_TYPE_SATELLITE) {
                googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
            } else {
                googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            }
        }
    }
    
    public Marker addAmapMarker(LatLng latLng, String title, String snippet) {
        if (amap != null) {
            MarkerOptions options = new MarkerOptions()
                .position(latLng)
                .title(title)
                .snippet(snippet);
            return amap.addMarker(options);
        }
        return null;
    }
    
    public com.google.android.gms.maps.model.Marker addGoogleMarker(
            com.google.android.gms.maps.model.LatLng latLng, String title, String snippet) {
        if (googleMap != null) {
            com.google.android.gms.maps.model.MarkerOptions options = 
                new com.google.android.gms.maps.model.MarkerOptions()
                    .position(latLng)
                    .title(title)
                    .snippet(snippet);
            return googleMap.addMarker(options);
        }
        return null;
    }
    
    public void clearMap() {
        if (isAmap() && amap != null) {
            amap.clear();
        } else if (isGoogleMap() && googleMap != null) {
            googleMap.clear();
        }
    }
    
    public float getZoomLevel() {
        if (isAmap() && amap != null) {
            return amap.getCameraPosition().zoom;
        } else if (isGoogleMap() && googleMap != null) {
            return googleMap.getCameraPosition().zoom;
        }
        return 17;
    }
    
    private void saveMapProvider() {
        android.content.SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putString("map_provider", currentProvider);
        editor.apply();
    }
    
    public AMap getAmap() {
        return amap;
    }
    
    public GoogleMap getGoogleMap() {
        return googleMap;
    }
    
    public boolean isGoogleMapReady() {
        return googleMapReady && googleMap != null;
    }
    
    public void setDefaultLocation(double lat, double lng) {
        this.defaultLat = lat;
        this.defaultLng = lng;
        
        if (isAmap() && amap != null) {
            LatLng latLng = new LatLng(lat, lng);
            amap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17));
        } else if (isGoogleMap() && googleMap != null) {
            // 完全禁用谷歌地图的自动相机移动
            Log.d(TAG, "Google Map - setDefaultLocation COMPLETELY DISABLED. Requested: " + lat + ", " + lng);
            // 不执行任何相机移动操作
        }
    }
    
    public void setTargetLocation(double lat, double lng, float zoom) {
        this.targetLat = lat;
        this.targetLng = lng;
        this.targetZoom = zoom;
        
        Log.d(TAG, "Target location set: lat=" + lat + ", lng=" + lng + ", zoom=" + zoom);
        
        // 完全禁用自动移动相机，由用户手动控制地图位置
        Log.d(TAG, "Auto camera movement disabled - user controls map position");
    }
    
    /**
     * 重置用户交互状态（仅在必要时调用，如切换设备时）
     */
    public void resetUserInteractionState() {
        this.userHasInteractedWithMap = false;
        Log.d(TAG, "User interaction state reset");
    }
    
    /**
     * 获取地图显示使用的语言代码
     * @return Google Maps 支持的语言代码
     */
    private String getMapLanguageCode() {
        android.content.SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        String languageCode = prefs.getString("language", "zh");
        
        // 将应用的语言代码转换为 Google Maps 支持的语言代码
        switch (languageCode) {
            case "zh":
                return "zh-CN";  // 中文（简体）
            case "en":
                return "en";     // 英语
            case "pt-rBR":
                return "pt-BR";  // 巴西葡萄牙语
            case "ru":
                return "ru";     // 俄语
            case "hi":
                return "hi";     // 印地语
            case "tr":
                return "tr";     // 土耳其语
            default:
                return "en";     // 默认使用英语
        }
    }
    
    /**
     * 获取地区代码，影响地图POI和边界显示
     * @return ISO 3166-1 alpha-2 地区代码
     */
    private String getRegionCode() {
        android.content.SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        String languageCode = prefs.getString("language", "zh");
        
        // 根据语言返回对应的地区代码
        switch (languageCode) {
            case "zh":
                return "CN";     // 中国
            case "en":
                return "US";     // 美国
            case "pt-rBR":
                return "BR";     // 巴西
            case "ru":
                return "RU";     // 俄罗斯
            case "hi":
                return "IN";     // 印度
            case "tr":
                return "TR";     // 土耳其
            default:
                return "US";     // 默认使用美国
        }
    }
}
