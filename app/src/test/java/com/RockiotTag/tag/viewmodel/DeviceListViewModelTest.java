package com.RockiotTag.tag.viewmodel;

import static org.junit.Assert.*;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import android.app.Application;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33})
public class DeviceListViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private DeviceListViewModel viewModel;

    @Before
    public void setUp() {
        Application app = ApplicationProvider.getApplicationContext();
        viewModel = new DeviceListViewModel(app);
    }

    @Test
    public void testViewModelCreation() {
        assertNotNull(viewModel);
    }

    @Test
    public void testGetDeviceList() {
        assertNotNull(viewModel.getDeviceList());
    }

    @Test
    public void testGetIsEmpty() {
        assertNotNull(viewModel.getIsEmpty());
    }
}
