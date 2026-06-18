package com.RockiotTag.tag.fragment;

import android.os.Bundle;
import android.util.Log;
import com.RockiotTag.tag.util.LogUtil;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.RockiotTag.tag.R;
import com.RockiotTag.tag.StayPoint;
import com.RockiotTag.tag.manager.TrackMapController;
import com.RockiotTag.tag.viewmodel.TrackViewModel;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;

import java.util.List;

/**
 * 轨迹地图 Fragment
 * 职责：显示轨迹地图和播放控制
 */
public class TrackMapFragment extends Fragment implements OnMapReadyCallback {
    private static final String TAG = "TrackMapFragment";
    
    private GoogleMap googleMap;
    private TrackViewModel viewModel;
    private TrackMapController mapController;
    
    public interface OnMapInteractionListener {
        void onMapReady(GoogleMap map);
        void onPlaybackProgress(int currentIndex, StayPoint currentPoint);
    }
    
    private OnMapInteractionListener listener;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_track_map, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // 初始化 ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(TrackViewModel.class);
        
        // 初始化地图
        SupportMapFragment mapFragment = (SupportMapFragment) 
            getChildFragmentManager().findFragmentById(R.id.google_map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
        
        // 观察数据变化
        observeData();
    }
    
    /**
     * 观察数据变化
     */
    private void observeData() {
        viewModel.getStayPoints().observe(getViewLifecycleOwner(), stayPoints -> {
            if (stayPoints != null && googleMap != null) {
                renderTrackOnMap(stayPoints);
            }
        });
    }
    
    /**
     * 在地图上渲染轨迹
     */
    private void renderTrackOnMap(List<StayPoint> stayPoints) {
        if (mapController != null) {
            mapController.release();
        }
        
        mapController = new TrackMapController(googleMap, stayPoints);
        mapController.renderTrack();
        
        // 通知监听器
        if (listener != null) {
            listener.onMapReady(googleMap);
        }
        
        // 调整相机视角
        if (!stayPoints.isEmpty()) {
            StayPoint firstPoint = stayPoints.get(0);
            LatLng firstPos = new LatLng(firstPoint.getLatitude(), firstPoint.getLongitude());
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(firstPos, 17));
        }
        
        Toast.makeText(getContext(), 
            getString(R.string.loaded_stay_points, stayPoints.size()), 
            Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        
        // 设置地图类型
        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        
        // 启用我的位置按钮（如果需要）
        // googleMap.setMyLocationEnabled(true);
        
        LogUtil.d(TAG, "Google Map is ready");
    }
    
    /**
     * 设置交互监听器
     */
    public void setOnMapInteractionListener(OnMapInteractionListener listener) {
        this.listener = listener;
    }
    
    /**
     * 更新播放位置
     */
    public void updatePlaybackPosition(int currentIndex, StayPoint currentPoint) {
        if (mapController != null) {
            mapController.updatePlaybackPosition(currentIndex, currentPoint);
        }
        
        if (listener != null) {
            listener.onPlaybackProgress(currentIndex, currentPoint);
        }
    }
    
    /**
     * 清空地图
     */
    public void clearMap() {
        if (mapController != null) {
            mapController.clearTrack();
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mapController != null) {
            mapController.release();
            mapController = null;
        }
    }
}
