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
import com.RockiotTag.tag.util.TimeFormatter;
import com.RockiotTag.tag.viewmodel.MainViewModel;

/**
 * 设备控制Fragment - 显示设备信息（电池、地址、更新时间）
 * 职责：
 * 1. 显示设备的电池电量
 * 2. 显示设备的地址信息
 * 3. 显示设备的最后更新时间
 * 4. 响应ViewModel中的数据变化
 */
public class DeviceControlFragment extends Fragment {
    private static final String TAG = "DeviceControlFragment";
    
    private MainViewModel viewModel;
    private TextView batteryLevelText;
    private TextView deviceAddressText;
    private TextView updateTimeText;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_device_control, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        batteryLevelText = view.findViewById(R.id.battery_level);
        deviceAddressText = view.findViewById(R.id.device_address);
        updateTimeText = view.findViewById(R.id.update_time);
        
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        
        setupViewModelObservers();
    }
    
    /**
     * 设置ViewModel观察者
     */
    private void setupViewModelObservers() {
        // 观察电池电量
        viewModel.getBatteryLevel().observe(getViewLifecycleOwner(), batteryStr -> {
            if (batteryStr != null && batteryLevelText != null) {
                try {
                    int battery = Integer.parseInt(batteryStr);
                    if (battery > 0) {
                        batteryLevelText.setText(getString(R.string.battery_level_value, String.valueOf(battery)));
                    } else if (battery == 0) {
                        batteryLevelText.setText(getString(R.string.battery_level_zero));
                    } else {
                        batteryLevelText.setText(getString(R.string.battery_level_unknown));
                    }
                } catch (NumberFormatException e) {
                    batteryLevelText.setText(getString(R.string.battery_level_unknown));
                }
            }
        });
        
        // 观察设备地址
        viewModel.getDeviceAddress().observe(getViewLifecycleOwner(), address -> {
            if (address != null && deviceAddressText != null) {
                if ("not_reported".equals(address)) {
                    deviceAddressText.setText(getString(R.string.position_not_reported));
                } else {
                    deviceAddressText.setText(getString(R.string.position_with_address, address));
                }
            }
        });
        
        // 观察更新时间 - 使用 TimeFormatter 智能格式化
        viewModel.getUpdateTime().observe(getViewLifecycleOwner(), timeStr -> {
            if (timeStr != null && updateTimeText != null) {
                if ("not_reported".equals(timeStr)) {
                    updateTimeText.setText(getString(R.string.last_update_not_reported));
                } else {
                    try {
                        long timestamp = Long.parseLong(timeStr);
                        updateTimeText.setText(getString(R.string.last_update_with_time, 
                            TimeFormatter.formatSmartTime(requireContext(), timestamp)));
                    } catch (NumberFormatException e) {
                        updateTimeText.setText(getString(R.string.last_update_not_reported));
                    }
                }
            }
        });
    }
}
