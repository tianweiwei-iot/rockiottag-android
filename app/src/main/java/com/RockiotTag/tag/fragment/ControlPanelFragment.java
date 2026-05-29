package com.RockiotTag.tag.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.RockiotTag.tag.R;
import com.RockiotTag.tag.viewmodel.BleViewModel;
import com.RockiotTag.tag.viewmodel.MainViewModel;

/**
 * 控制面板Fragment
 * 职责：
 * 1. 显示控制按钮（蜂鸣器、定位、刷新等）
 * 2. 处理按钮点击事件
 */
public class ControlPanelFragment extends Fragment {
    private static final String TAG = "ControlPanelFragment";
    
    private MainViewModel mainViewModel;
    private BleViewModel bleViewModel;
    
    private ImageButton menuBtn;
    private ImageButton buzzerBtn;
    private ImageButton locateBtn;
    private ImageButton mapTypeBtn;
    private ImageButton refreshBtn;
    private ImageButton trackBtn;
    
    public static ControlPanelFragment newInstance() {
        return new ControlPanelFragment();
    }
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_control_panel, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // 初始化视图
        menuBtn = view.findViewById(R.id.menu_btn);
        buzzerBtn = view.findViewById(R.id.buzzer_btn);
        locateBtn = view.findViewById(R.id.locate_btn);
        mapTypeBtn = view.findViewById(R.id.map_type_btn);
        refreshBtn = view.findViewById(R.id.refresh_btn);
        trackBtn = view.findViewById(R.id.track_btn);
        
        // 初始化ViewModel
        mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        bleViewModel = new ViewModelProvider(requireActivity()).get(BleViewModel.class);
        
        setupClickListeners();
    }
    
    /**
     * 设置按钮点击监听器
     */
    private void setupClickListeners() {
        // 菜单按钮
        if (menuBtn != null) {
            menuBtn.setOnClickListener(v -> showMenuOptions());
        }
        
        // 蜂鸣器按钮
        if (buzzerBtn != null) {
            buzzerBtn.setOnClickListener(v -> {
                bleViewModel.triggerBuzzer();
                Toast.makeText(getContext(), R.string.trigger_buzzer, Toast.LENGTH_SHORT).show();
            });
        }
        
        // 定位按钮
        if (locateBtn != null) {
            locateBtn.setOnClickListener(v -> {
                // TODO: 实现定位功能
                Toast.makeText(getContext(), "定位", Toast.LENGTH_SHORT).show();
            });
        }
        
        // 地图类型按钮
        if (mapTypeBtn != null) {
            mapTypeBtn.setOnClickListener(v -> {
                // TODO: 切换地图类型
                Toast.makeText(getContext(), "切换地图", Toast.LENGTH_SHORT).show();
            });
        }
        
        // 刷新按钮
        if (refreshBtn != null) {
            refreshBtn.setOnClickListener(v -> {
                // TODO: 刷新设备信息
                Toast.makeText(getContext(), "刷新", Toast.LENGTH_SHORT).show();
            });
        }
        
        // 轨迹按钮
        if (trackBtn != null) {
            trackBtn.setOnClickListener(v -> {
                // TODO: 打开轨迹页面
                Toast.makeText(getContext(), "轨迹", Toast.LENGTH_SHORT).show();
            });
        }
    }
    
    /**
     * 显示菜单选项
     */
    private void showMenuOptions() {
        // TODO: 实现菜单对话框
        Toast.makeText(getContext(), "菜单", Toast.LENGTH_SHORT).show();
    }
}
