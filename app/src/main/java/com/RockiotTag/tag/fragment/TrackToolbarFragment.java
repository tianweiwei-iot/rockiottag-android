package com.RockiotTag.tag.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.RockiotTag.tag.R;

/**
 * 轨迹工具栏 Fragment
 * 职责：管理地图控制按钮
 */
public class TrackToolbarFragment extends Fragment {
    private static final String TAG = "TrackToolbarFragment";
    
    private ImageButton toggleMarkersBtn;
    private ImageButton togglePolylineBtn;
    private ImageButton toggleSatelliteBtn;
    private ImageButton statisticsBtn;
    
    public interface OnToolbarActionListener {
        void onToggleMarkers();
        void onTogglePolyline();
        void onToggleSatellite();
        void onShowStatistics();
    }
    
    private OnToolbarActionListener listener;
    private boolean showMarkers = true;
    private boolean showPolyline = true;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_track_toolbar, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initViews(view);
        setupListeners();
    }
    
    private void initViews(View view) {
        toggleMarkersBtn = view.findViewById(R.id.toggle_markers_btn);
        togglePolylineBtn = view.findViewById(R.id.toggle_polyline_btn);
        toggleSatelliteBtn = view.findViewById(R.id.toggle_satellite_btn);
        statisticsBtn = view.findViewById(R.id.statistics_btn);
    }
    
    private void setupListeners() {
        toggleMarkersBtn.setOnClickListener(v -> {
            showMarkers = !showMarkers;
            if (listener != null) {
                listener.onToggleMarkers();
            }
        });
        
        togglePolylineBtn.setOnClickListener(v -> {
            showPolyline = !showPolyline;
            if (listener != null) {
                listener.onTogglePolyline();
            }
        });
        
        toggleSatelliteBtn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onToggleSatellite();
            }
        });
        
        statisticsBtn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onShowStatistics();
            }
        });
    }
    
    public void setOnToolbarActionListener(OnToolbarActionListener listener) {
        this.listener = listener;
    }
    
    public boolean isShowMarkers() {
        return showMarkers;
    }
    
    public boolean isShowPolyline() {
        return showPolyline;
    }
}
