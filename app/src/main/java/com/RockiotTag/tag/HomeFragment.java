package com.RockiotTag.tag;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "=== HomeFragment onCreateView ===");
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "=== HomeFragment onViewCreated ===");
        // 地图由MainActivity管理，Fragment只提供容器
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "=== HomeFragment onResume ===");
        // 通知MainActivity显示首页相关UI（浮动按钮、底部信息卡片）
        if (getActivity() instanceof MainActivity) {
            Log.d(TAG, "=== Calling MainActivity.onHomeFragmentVisible ===");
            ((MainActivity) getActivity()).onHomeFragmentVisible();
        }
    }
}
