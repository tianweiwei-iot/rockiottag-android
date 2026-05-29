package com.RockiotTag.tag.viewmodel;

import static org.junit.Assert.*;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DeviceListViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private DeviceListViewModel viewModel;

    @Before
    public void setUp() {
        // 注意：实际测试需要 Application context
        // 这里只是示例，实际运行时需要 Robolectric 或 Instrumentation Test
    }

    @Test
    public void testViewModelCreation() {
        // 测试 ViewModel 创建
        assertNotNull(viewModel);
    }

    @Test
    public void testGetDeviceList() {
        // 测试获取设备列表 LiveData
        assertNotNull(viewModel.getDeviceList());
    }

    @Test
    public void testGetIsEmpty() {
        // 测试获取空状态 LiveData
        assertNotNull(viewModel.getIsEmpty());
    }
}
