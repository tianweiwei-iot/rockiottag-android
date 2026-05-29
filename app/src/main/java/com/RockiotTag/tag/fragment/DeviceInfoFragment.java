package com.RockiotTag.tag.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.RockiotTag.tag.R;
import com.RockiotTag.tag.model.DeviceTag;
import com.RockiotTag.tag.viewmodel.MainViewModel;

/**
 * 设备信息显示Fragment
 * 职责：
 * 1. 显示设备名称和标签图标
 * 2. 显示电池电量
 * 3. 显示设备地址
 * 4. 显示更新时间
 */
public class DeviceInfoFragment extends Fragment {
    private static final String TAG = "DeviceInfoFragment";
    
    private MainViewModel viewModel;
    private TextView deviceNameText;
    private TextView deviceTagIcon;
    private TextView batteryLevelText;
    private TextView deviceAddressText;
    private TextView updateTimeText;
    private TextView noDeviceText;
    private View deviceNameContainer;
    
    public static DeviceInfoFragment newInstance() {
        return new DeviceInfoFragment();
    }
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_device_info, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // 初始化视图
        deviceNameText = view.findViewById(R.id.device_name);
        deviceTagIcon = view.findViewById(R.id.device_tag_icon);
        batteryLevelText = view.findViewById(R.id.battery_level);
        deviceAddressText = view.findViewById(R.id.device_address);
        updateTimeText = view.findViewById(R.id.update_time);
        noDeviceText = view.findViewById(R.id.no_device_text);
        deviceNameContainer = view.findViewById(R.id.device_name_container);
        
        // 初始化ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        
        setupViewModelObservers();
    }
    
    /**
     * 设置ViewModel观察者
     */
    private void setupViewModelObservers() {
        // 观察设备名称
        viewModel.getDeviceName().observe(getViewLifecycleOwner(), name -> {
            updateDeviceName(name);
        });
        
        // 观察电池电量
        viewModel.getBatteryLevel().observe(getViewLifecycleOwner(), battery -> {
            if (battery != null && batteryLevelText != null) {
                batteryLevelText.setText(battery);
            }
        });
        
        // 观察设备地址
        viewModel.getDeviceAddress().observe(getViewLifecycleOwner(), address -> {
            if (address != null && deviceAddressText != null) {
                deviceAddressText.setText(address);
            }
        });
        
        // 观察更新时间
        viewModel.getUpdateTime().observe(getViewLifecycleOwner(), time -> {
            if (time != null && updateTimeText != null) {
                updateTimeText.setText(time);
            }
        });
    }
    
    /**
     * 更新设备名称和标签
     */
    private void updateDeviceName(String name) {
        if (name != null && !name.isEmpty()) {
            // 有设备，显示设备信息
            deviceNameText.setText(name);
            
            // 获取并显示标签图标
            String tag = ""; // TODO: 从ViewModel获取tag
            if (deviceTagIcon != null) {
                String icon = DeviceTag.getEmoji(tag);
                deviceTagIcon.setText(icon);
                deviceTagIcon.setVisibility(icon.isEmpty() ? View.GONE : View.VISIBLE);
            }
            
            if (deviceNameContainer != null) {
                deviceNameContainer.setVisibility(View.VISIBLE);
            }
            if (noDeviceText != null) {
                noDeviceText.setVisibility(View.GONE);
            }
        } else {
            // 没有设备，显示提示
            if (deviceNameContainer != null) {
                deviceNameContainer.setVisibility(View.GONE);
            }
            if (noDeviceText != null) {
                noDeviceText.setVisibility(View.VISIBLE);
                noDeviceText.setText(getString(R.string.no_device_selected));
            }
        }
    }
}
