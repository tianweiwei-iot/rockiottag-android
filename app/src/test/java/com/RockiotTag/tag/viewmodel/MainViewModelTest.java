package com.RockiotTag.tag.viewmodel;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.Application;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Observer;

import com.RockiotTag.tag.Device;
import com.RockiotTag.tag.repository.DeviceRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class MainViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private Application application;

    @Mock
    private DeviceRepository deviceRepository;

    private MainViewModel viewModel;

    @Before
    public void setUp() {
        when(deviceRepository.getAllDevices()).thenReturn(new ArrayList<>());
        viewModel = new MainViewModel(application);
    }

    @Test
    public void testGetDeviceList() {
        // 测试获取设备列表
        List<Device> devices = viewModel.getDeviceList().getValue();
        assertNotNull(devices);
    }

    @Test
    public void testGetIsLoading() {
        // 测试加载状态
        Boolean isLoading = viewModel.getIsLoading().getValue();
        assertNotNull(isLoading);
        assertFalse(isLoading);
    }

    @Test
    public void testGetErrorMessage() {
        // 测试错误消息初始值为null
        String errorMessage = viewModel.getErrorMessage().getValue();
        assertNull(errorMessage);
    }

    @Test
    public void testGetSelectedDeviceId() {
        // 测试选中设备ID
        String deviceId = viewModel.getSelectedDeviceId().getValue();
        // 初始值可能为null或空字符串
        assertNotNull(deviceId);
    }

    @Test
    public void testFormatDistance() {
        // 测试距离格式化
        assertEquals("100米", viewModel.formatDistance(100));
        assertEquals("1.50公里", viewModel.formatDistance(1500));
        assertEquals("0米", viewModel.formatDistance(0));
    }

    @Test
    public void testFormatBattery() {
        // 测试电量格式化
        assertEquals("100%", viewModel.formatBattery(100));
        assertEquals("50%", viewModel.formatBattery(50));
        assertEquals("0%", viewModel.formatBattery(0));
    }
}
