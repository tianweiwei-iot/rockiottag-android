package com.RockiotTag.tag.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.RockiotTag.tag.R;
import com.RockiotTag.tag.viewmodel.MapViewModel;
import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;

/**
 * 地图显示Fragment
 * 职责：
 * 1. 显示高德地图
 * 2. 管理地图生命周期
 */
public class MapFragment extends Fragment {
    private static final String TAG = "MapFragment";
    
    private MapView mapView;
    private AMap aMap;
    private MapViewModel mapViewModel;
    
    public static MapFragment newInstance() {
        return new MapFragment();
    }
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        mapView = view.findViewById(R.id.map_view);
        if (mapView != null) {
            mapView.onCreate(savedInstanceState);
            initMap();
        }
        
        mapViewModel = new ViewModelProvider(requireActivity()).get(MapViewModel.class);
        observeViewModel();
    }
    
    /**
     * 初始化地图 - 使用 getMap() 而非 getMapAsync()
     */
    private void initMap() {
        if (mapView == null) return;
        
        aMap = mapView.getMap();
        if (aMap != null) {
            setupMap();
        }
    }
    
    /**
     * 设置地图属性
     */
    private void setupMap() {
        if (aMap == null) return;
        
        // 启用定位按钮
        aMap.getUiSettings().setMyLocationButtonEnabled(true);
        aMap.getUiSettings().setZoomControlsEnabled(false);
        
        // 观察设备位置变化
        mapViewModel.getDeviceLocation().observe(getViewLifecycleOwner(), device -> {
            if (device != null && device.getLatitude() != 0 && device.getLongitude() != 0) {
                updateDeviceMarker(device);
            }
        });
    }
    
    /**
     * 更新设备标记
     */
    private void updateDeviceMarker(com.RockiotTag.tag.model.TagDevice device) {
        // TODO: 实现设备标记更新逻辑
    }
    
    /**
     * 观察ViewModel数据变化
     */
    private void observeViewModel() {
        // 观察相机移动状态
        mapViewModel.getIsCameraMoving().observe(getViewLifecycleOwner(), isMoving -> {
            if (isMoving != null && isMoving) {
                // 相机正在移动
            }
        });
        
        // 观察地图状态
        mapViewModel.getMapStatus().observe(getViewLifecycleOwner(), status -> {
            // 可以显示地图状态
        });
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mapView != null) {
            mapView.onDestroy();
        }
    }
    
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }
}
