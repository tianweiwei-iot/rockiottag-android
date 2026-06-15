package com.RockiotTag.tag;

import android.content.Intent;
import android.os.Bundle;
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
        return inflater.inflate(R.layout.fragment_track, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 直接启动TrackActivity
        openTrackActivity();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每次回到轨迹Tab时重新打开TrackActivity
        openTrackActivity();
    }

    private void openTrackActivity() {
        if (getActivity() == null) return;

        Device selectedDevice = null;
        if (getActivity() instanceof MainActivity) {
            selectedDevice = ((MainActivity) getActivity()).getSelectedDevice();
        }

        if (selectedDevice == null) {
            Toast.makeText(requireContext(), R.string.please_select_device, Toast.LENGTH_SHORT).show();
            // 切换回首页
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchToTab(0);
            }
            return;
        }

        Intent intent = new Intent(requireContext(), TrackActivity.class);
        startActivity(intent);
    }
}
