package com.RockiotTag.tag;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

public class ProfileFragment extends Fragment {
    private static final String TAG = "ProfileFragment";

    private ImageView userAvatar;
    private TextView userNameText;
    private TextView userEmailText;
    private LinearLayout loginButtonsArea;
    private LinearLayout userInfoArea;
    private SwitchCompat darkModeSwitch;
    private TextView versionText;
    private TextView darkModeText;
    private com.google.android.material.button.MaterialButton logoutBtn;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userAvatar = view.findViewById(R.id.user_avatar);
        userNameText = view.findViewById(R.id.user_name_text);
        userEmailText = view.findViewById(R.id.user_email_text);
        loginButtonsArea = view.findViewById(R.id.login_buttons_area);
        userInfoArea = view.findViewById(R.id.user_info_area);
        darkModeSwitch = view.findViewById(R.id.dark_mode_switch);
        versionText = view.findViewById(R.id.version_text);
        darkModeText = view.findViewById(R.id.dark_mode_text);
        logoutBtn = view.findViewById(R.id.logout_btn);

        // 登录按钮
        view.findViewById(R.id.login_btn).setOnClickListener(v -> showLoginDialog());
        view.findViewById(R.id.register_btn).setOnClickListener(v -> showRegisterDialog());

        // 切换语言
        view.findViewById(R.id.language_item).setOnClickListener(v -> showLanguageDialog());

        // 切换地图
        view.findViewById(R.id.map_switch_item).setOnClickListener(v -> showMapSwitchDialog());

        // 深色模式
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean("dark_mode", false);
        darkModeSwitch.setChecked(isDarkMode);
        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("dark_mode", isChecked).apply();
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).toggleDarkMode(isChecked);
            }
        });

        // 版本信息
        try {
            String versionName = requireContext().getPackageManager()
                .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            int versionCode = requireContext().getPackageManager()
                .getPackageInfo(requireContext().getPackageName(), 0).versionCode;
            versionText.setText(getString(R.string.version_format, versionName, versionCode));
        } catch (Exception e) {
            versionText.setText("");
        }

        // 退出登录
        logoutBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                .setMessage(R.string.logout_confirm)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    // 清除登录状态
                    prefs.edit()
                        .remove("auth_token")
                        .remove("user_username")
                        .remove("user_email")
                        .remove("user_phone")
                        .remove("user_nickname")
                        .apply();
                    updateLoginUI();
                    Toast.makeText(requireContext(), R.string.logout_success, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        });

        updateLoginUI();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateLoginUI();
    }

    private void updateLoginUI() {
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);
        String token = prefs.getString("auth_token", null);
        String username = prefs.getString("user_username", "");
        String email = prefs.getString("user_email", "");
        String nickname = prefs.getString("user_nickname", "");

        if (token != null && !token.isEmpty()) {
            // 已登录
            userNameText.setText(nickname != null && !nickname.isEmpty() ? nickname : username);
            if (email != null && !email.isEmpty()) {
                userEmailText.setText(email);
                userEmailText.setVisibility(View.VISIBLE);
            } else {
                userEmailText.setVisibility(View.GONE);
            }
            loginButtonsArea.setVisibility(View.GONE);
            logoutBtn.setVisibility(View.VISIBLE);
        } else {
            // 未登录
            userNameText.setText(R.string.not_logged_in);
            userEmailText.setVisibility(View.GONE);
            loginButtonsArea.setVisibility(View.VISIBLE);
            logoutBtn.setVisibility(View.GONE);
        }
    }

    private void showLoginDialog() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showLoginDialog();
        }
    }

    private void showRegisterDialog() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showRegisterDialog();
        }
    }

    private void showLanguageDialog() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showLanguageOptions();
        }
    }

    private void showMapSwitchDialog() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showMapSwitchOptions();
        }
    }
}
