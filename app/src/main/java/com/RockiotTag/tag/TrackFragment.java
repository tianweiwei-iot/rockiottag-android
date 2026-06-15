package com.RockiotTag.tag;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class TrackFragment extends Fragment {
    private static final String TAG = "TrackFragment";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "=== TrackFragment onCreateView ===");
        return inflater.inflate(R.layout.fragment_track, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "=== TrackFragment onViewCreated ===");
        // 不在这里启动TrackActivity，等用户主动点击轨迹Tab
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "=== TrackFragment onResume ===");
        // 用户点击轨迹Tab时启动TrackActivity
        openTrackActivity();
    }

    private void openTrackActivity() {
        Log.d(TAG, "=== TrackFragment openTrackActivity ===");
        if (getActivity() == null) {
            Log.w(TAG, "getActivity is null, skip");
            return;
        }

        Device selectedDevice = null;
        if (getActivity() instanceof MainActivity) {
            selectedDevice = ((MainActivity) getActivity()).getSelectedDevice();
        }

        Log.d(TAG, "selectedDevice: " + (selectedDevice != null ? selectedDevice.getName() : "NULL"));

        if (selectedDevice == null) {
            Log.w(TAG, "No selected device, switching to home tab");
            Toast.makeText(requireContext(), R.string.please_select_device, Toast.LENGTH_SHORT).show();
            // 切换回首页
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchToTab(0);
            }
            return;
        }

        Log.d(TAG, "Starting TrackActivity");
        Intent intent = new Intent(requireContext(), TrackActivity.class);
        startActivity(intent);
    }
}
