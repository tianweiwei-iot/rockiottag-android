package com.RockiotTag.tag;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
    }

    // 注意：不再在onResume中启动TrackActivity，由MainActivity的Tab点击直接启动
    // 避免从TrackActivity返回后onResume再次触发导致循环启动
}
