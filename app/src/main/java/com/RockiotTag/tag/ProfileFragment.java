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
    private com.google.android.material.button.MaterialButton switchAccountBtn;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 添加状态栏高度的顶部内边距，避免标题栏与状态栏重叠
        View titleBar = view.findViewById(R.id.title_bar);
        if (titleBar != null) {
            int statusBarHeight = getStatusBarHeight();
            titleBar.setPadding(titleBar.getPaddingLeft(),
                titleBar.getPaddingTop() + statusBarHeight,
                titleBar.getPaddingRight(),
                titleBar.getPaddingBottom());
        }

        userAvatar = view.findViewById(R.id.user_avatar);
        userNameText = view.findViewById(R.id.user_name_text);
        userEmailText = view.findViewById(R.id.user_email_text);
        loginButtonsArea = view.findViewById(R.id.login_buttons_area);
        userInfoArea = view.findViewById(R.id.user_info_area);
        darkModeSwitch = view.findViewById(R.id.dark_mode_switch);
        versionText = view.findViewById(R.id.version_text);
        darkModeText = view.findViewById(R.id.dark_mode_text);
        logoutBtn = view.findViewById(R.id.logout_btn);
        switchAccountBtn = view.findViewById(R.id.switch_account_btn);

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
                    // 1. 调用后端登出接口作废Token
                    String token = prefs.getString("auth_token", null);
                    if (token != null && !token.isEmpty()) {
                        new Thread(() -> {
                            UserApiService.getInstance().logout(token);
                        }).start();
                    }
                    
                    // 2. 清空本地所有身份凭证
                    prefs.edit()
                        .remove("auth_token")
                        .remove("user_username")
                        .remove("user_email")
                        .remove("user_phone")
                        .remove("user_nickname")
                        .remove("bound_devices")  // 清除绑定设备列表
                        .remove("selected_device_id")  // 清除选中的设备
                        .apply();
                    
                    // 3. 清空本地数据库中的设备数据和轨迹记录
                    clearLocalDeviceData();
                    
                    // 4. 更新UI
                    updateLoginUI();
                    
                    // 5. 通知MainActivity清空内存缓存并刷新
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).refreshDeviceListAfterLogout();
                    }
                    Toast.makeText(requireContext(), R.string.logout_success, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        });

        // 切换账号
        switchAccountBtn.setOnClickListener(v -> {
            // 显示登录/注册选择对话框
            String[] items = {getString(R.string.login), getString(R.string.register)};
            new AlertDialog.Builder(requireContext())
                .setTitle(R.string.switch_account)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showLoginDialog();
                    } else {
                        showRegisterDialog();
                    }
                })
                .show();
        });

        updateLoginUI();

        // 首次创建时应用深色模式
        boolean isDarkModeInit = requireContext().getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
            .getBoolean("dark_mode", false);
        applyTheme(isDarkModeInit);
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
            switchAccountBtn.setVisibility(View.VISIBLE);
            logoutBtn.setVisibility(View.VISIBLE);
        } else {
            // 未登录
            userNameText.setText(R.string.not_logged_in);
            userEmailText.setVisibility(View.GONE);
            loginButtonsArea.setVisibility(View.VISIBLE);
            switchAccountBtn.setVisibility(View.GONE);
            logoutBtn.setVisibility(View.GONE);
        }
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
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

    /**
     * 清除本地设备数据
     */
    private void clearLocalDeviceData() {
        try {
            DatabaseHelper dbHelper = new DatabaseHelper(requireContext());
            // 清除所有设备数据
            dbHelper.deleteAllDevices();
            // 清除所有轨迹/定位记录
            dbHelper.deleteAllLocationRecords();
            android.util.Log.d("ProfileFragment", "Cleared all local device data and location records");
        } catch (Exception e) {
            android.util.Log.e("ProfileFragment", "Error clearing device data: " + e.getMessage(), e);
        }
    }

    /**
     * 手动应用深色/浅色主题
     */
    public void applyTheme(boolean isDarkMode) {
        View rootView = getView();
        if (rootView == null) return;
        
        int bgColor = getResources().getColor(isDarkMode ? R.color.dark_background : R.color.background, null);
        int topBarColor = getResources().getColor(isDarkMode ? R.color.dark_top_bar_background : R.color.top_bar_background, null);
        int onSurfaceColor = getResources().getColor(isDarkMode ? R.color.dark_onSurface : R.color.onSurface, null);
        int textSecColor = getResources().getColor(isDarkMode ? R.color.dark_text_secondary : R.color.text_secondary, null);
        int dividerColor = getResources().getColor(isDarkMode ? R.color.dark_divider : R.color.text_secondary, null);
        
        rootView.setBackgroundColor(bgColor);
        
        // 标题栏
        View titleBar = rootView.findViewById(R.id.title_bar);
        if (titleBar != null) titleBar.setBackgroundColor(topBarColor);
        
        // 用户信息区域
        View userInfoArea = rootView.findViewById(R.id.user_info_area);
        if (userInfoArea != null) userInfoArea.setBackgroundColor(topBarColor);
        
        // 登录按钮区域
        View loginButtonsArea = rootView.findViewById(R.id.login_buttons_area);
        if (loginButtonsArea != null) loginButtonsArea.setBackgroundColor(topBarColor);
        
        // 文字颜色
        if (userNameText != null) userNameText.setTextColor(onSurfaceColor);
        if (userEmailText != null) userEmailText.setTextColor(textSecColor);
        if (darkModeText != null) darkModeText.setTextColor(onSurfaceColor);
        if (versionText != null) versionText.setTextColor(textSecColor);
        
        // 菜单项文字颜色
        updateMenuItemColors(rootView, onSurfaceColor, textSecColor, dividerColor, isDarkMode);
    }
    
    private void updateMenuItemColors(View rootView, int onSurfaceColor, int textSecColor, int dividerColor, boolean isDarkMode) {
        // 遍历菜单项，更新文字和图标颜色
        int[] menuItemIds = {R.id.language_item, R.id.map_switch_item, R.id.dark_mode_item, R.id.version_item};
        for (int id : menuItemIds) {
            View menuItem = rootView.findViewById(id);
            if (menuItem instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout) menuItem;
                for (int i = 0; i < layout.getChildCount(); i++) {
                    View child = layout.getChildAt(i);
                    if (child instanceof TextView) {
                        ((TextView) child).setTextColor(
                            child.getId() == R.id.version_text || child.getId() == R.id.dark_mode_text ? 
                            textSecColor : onSurfaceColor);
                    }
                    if (child instanceof ImageView) {
                        ((ImageView) child).setColorFilter(textSecColor);
                    }
                }
            }
        }
    }
}
