package com.RockiotTag.tag.helper;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.LanguageUtils;
import com.RockiotTag.tag.NewApiService;
import com.RockiotTag.tag.R;
import com.RockiotTag.tag.UserApiService;
import com.RockiotTag.tag.DeviceApiService;
import com.RockiotTag.tag.util.LogUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * MainActivity 对话框辅助类
 * 负责封装所有对话框的创建和显示逻辑，减少 Activity 的代码量
 */
public class MainDialogHelper {

    private final AppCompatActivity activity;
    private final DialogCallbacks callbacks;

    /**
     * 对话框回调接口，由 Activity 实现以处理对话框触发的业务逻辑
     */
    public interface DialogCallbacks {
        // 地图切换相关
        com.RockiotTag.tag.MapManager getMapManager();
        com.RockiotTag.tag.DatabaseHelper getDatabaseHelper();

        // 语言切换相关
        void onLanguageChanged(String languageCode);

        // 蓝牙增强扫描相关
        com.RockiotTag.tag.integration.LocationOptimizationManager getLocationOptimizationManager();
        View getScanningIndicator();
        int getScanIntensityLevel();
        void onScanIntensitySelected(int level);

        // 登录/注册相关
        void onLoginSuccess(String token, String username, String nickname, String email);
        void refreshProfileFragment();

        // 权限相关
        void requestPermissions(String[] permissions, int requestCode);

        // 版本信息
        String getVersionName();
        int getVersionCode();
    }

    public MainDialogHelper(AppCompatActivity activity, DialogCallbacks callbacks) {
        this.activity = activity;
        this.callbacks = callbacks;
    }

    private boolean isDarkModeEnabled() {
        return activity.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
                .getBoolean("dark_mode", false);
    }

    private MaterialAlertDialogBuilder createAlertDialogBuilder() {
        if (isDarkModeEnabled()) {
            ContextThemeWrapper themedContext = new ContextThemeWrapper(activity, R.style.AlertDialogTheme_Dark);
            return new MaterialAlertDialogBuilder(themedContext, R.style.AlertDialogTheme_Dark);
        }
        return new MaterialAlertDialogBuilder(activity);
    }

    private ArrayAdapter<String> createSingleChoiceAdapter(String[] items) {
        final boolean darkMode = isDarkModeEnabled();
        Context context = darkMode
                ? new ContextThemeWrapper(activity, R.style.AlertDialogTheme_Dark)
                : activity;
        return new ArrayAdapter<String>(context, android.R.layout.select_dialog_singlechoice,
                android.R.id.text1, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text = view.findViewById(android.R.id.text1);
                if (darkMode && text != null) {
                    text.setTextColor(activity.getResources().getColor(R.color.dark_onSurface, null));
                }
                return view;
            }
        };
    }

    /**
     * 显示菜单选项对话框（围栏设置/蓝牙增强/版本信息）
     */
    public void showMenuOptions() {
        final List<String> menuItems = new ArrayList<>();
        menuItems.add(activity.getString(R.string.geofence_settings));
        menuItems.add(activity.getString(R.string.bluetooth_enhance));
        menuItems.add(activity.getString(R.string.version_info));

        new AlertDialog.Builder(activity)
                .setTitle(R.string.menu_title)
                .setItems(menuItems.toArray(new CharSequence[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                callbacks.onLanguageChanged(null); // 占位，实际由 Activity 处理 openGeofenceSettings
                                break;
                            case 1:
                                showBluetoothEnhanceOptions(callbacks.getScanIntensityLevel());
                                break;
                            case 2:
                                showVersionInfo();
                                break;
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 显示菜单选项对话框（带自定义菜单项回调）
     */
    public void showMenuOptions(final Runnable onGeofence, final Runnable onBluetoothEnhance, final Runnable onVersionInfo) {
        final List<String> menuItems = new ArrayList<>();
        menuItems.add(activity.getString(R.string.geofence_settings));
        menuItems.add(activity.getString(R.string.bluetooth_enhance));
        menuItems.add(activity.getString(R.string.version_info));

        new AlertDialog.Builder(activity)
                .setTitle(R.string.menu_title)
                .setItems(menuItems.toArray(new CharSequence[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                if (onGeofence != null) onGeofence.run();
                                break;
                            case 1:
                                if (onBluetoothEnhance != null) onBluetoothEnhance.run();
                                break;
                            case 2:
                                if (onVersionInfo != null) onVersionInfo.run();
                                break;
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 显示地图切换选项对话框
     */
    public void showMapSwitchOptions() {
        android.content.SharedPreferences prefs = activity.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);
        String currentMap = prefs.getString("map_provider", "amap");

        String[] mapOptions = {activity.getString(R.string.amap), activity.getString(R.string.google_map)};
        int checkedItem = 0;
        if (currentMap.equals("google")) {
            checkedItem = 1;
        }

        AlertDialog mapDialog = createAlertDialogBuilder()
                .setTitle(R.string.select_map)
                .setSingleChoiceItems(createSingleChoiceAdapter(mapOptions), checkedItem, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newMapProvider = "amap";
                        if (which == 1) {
                            newMapProvider = "google";
                        }

                        // 如果选择原生谷歌地图，先检查 Google Play Services
                        if (which == 1 && callbacks.getMapManager() != null) {
                            if (!callbacks.getMapManager().isGooglePlayServicesAvailable()) {
                                Toast.makeText(activity,
                                    "Google Play Services 不可用，无法使用谷歌地图",
                                    Toast.LENGTH_LONG).show();
                                dialog.dismiss();
                                return;
                            }
                        }

                        android.content.SharedPreferences prefs = activity.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);
                        android.content.SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("map_provider", newMapProvider);
                        editor.apply();

                        // 关键修复：切换地图时清除目标地图模式的旧缓存，防止地址污染
                        if (callbacks.getDatabaseHelper() != null) {
                            callbacks.getDatabaseHelper().cleanMapModeAddressCache(newMapProvider);
                            LogUtil.d("MainDialogHelper", "Cleared address cache for map mode: " + newMapProvider);
                        }

                        int toastMessage = R.string.switched_to_amap;
                        if (which == 1) {
                            toastMessage = R.string.switched_to_google_map;
                        }

                        Toast.makeText(activity, toastMessage, Toast.LENGTH_SHORT).show();

                        dialog.dismiss();

                        activity.recreate();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        mapDialog.show();
    }

    /**
     * 显示语言选择对话框
     */
    public void showLanguageOptions() {
        final List<LanguageItem> languages = new ArrayList<>();
        languages.add(new LanguageItem("\uD83C\uDDE8\uD83C\uDDF3", "中文", "zh"));
        languages.add(new LanguageItem("\uD83C\uDDEC\uD83C\uDDE7", "English", "en"));
        languages.add(new LanguageItem("\uD83C\uDDE7\uD83C\uDDF7", "Português", "pt-rBR"));
        languages.add(new LanguageItem("\uD83C\uDDF7\uD83C\uDDFA", "Русский", "ru"));
        languages.add(new LanguageItem("\uD83C\uDDEE\uD83C\uDDF3", "हिंदी", "hi"));
        languages.add(new LanguageItem("\uD83C\uDDF9\uD83C\uDDF7", "Türkçe", "tr"));

        // 获取当前选中的语言代码
        android.content.SharedPreferences prefs = activity.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);
        String currentLanguageCode = prefs.getString("language", LanguageUtils.getSystemLanguage());

        final boolean darkMode = isDarkModeEnabled();

        ArrayAdapter<LanguageItem> adapter = new ArrayAdapter<LanguageItem>(activity, R.layout.item_language, R.id.language_name, languages) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = convertView;
                if (view == null) {
                    view = LayoutInflater.from(getContext()).inflate(R.layout.item_language, parent, false);
                }
                LanguageItem item = getItem(position);
                TextView flagText = view.findViewById(R.id.language_flag);
                TextView nameText = view.findViewById(R.id.language_name);
                flagText.setText(item.flag);
                nameText.setText(item.name);

                // 高亮当前选中的语言
                if (item.code.equals(currentLanguageCode)) {
                    if (darkMode) {
                        view.setBackgroundColor(activity.getResources().getColor(R.color.dark_card_background_selected, null));
                    } else {
                        view.setBackgroundColor(activity.getResources().getColor(R.color.purple_500, null));
                    }
                    flagText.setTextColor(activity.getResources().getColor(android.R.color.white, null));
                    nameText.setTextColor(activity.getResources().getColor(android.R.color.white, null));
                } else if (darkMode) {
                    view.setBackgroundColor(activity.getResources().getColor(android.R.color.transparent, null));
                    flagText.setTextColor(activity.getResources().getColor(R.color.dark_onSurface, null));
                    nameText.setTextColor(activity.getResources().getColor(R.color.dark_onSurface, null));
                } else {
                    view.setBackgroundColor(activity.getResources().getColor(android.R.color.transparent, null));
                    flagText.setTextColor(activity.getResources().getColor(android.R.color.black, null));
                    nameText.setTextColor(activity.getResources().getColor(android.R.color.black, null));
                }

                return view;
            }
        };

        AlertDialog langDialog = createAlertDialogBuilder()
                .setTitle(R.string.language_title)
                .setAdapter(adapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        callbacks.onLanguageChanged(languages.get(which).code);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        langDialog.show();
    }

    /**
     * 显示版本信息对话框
     */
    public void showVersionInfo() {
        try {
            String versionName = callbacks.getVersionName();
            int versionCode = callbacks.getVersionCode();

            new AlertDialog.Builder(activity)
                    .setTitle(R.string.version_info)
                    .setMessage(activity.getString(R.string.version_format, versionName, versionCode))
                    .setPositiveButton(R.string.confirm, null)
                    .show();
        } catch (Exception e) {
            android.util.Log.e("MainDialogHelper", "Error getting version info: " + e.getMessage());
            Toast.makeText(activity, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示权限解释对话框
     */
    public void showPermissionRationale(final String[] permissions, final int requestCode) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.permission_request)
                .setMessage(R.string.permission_explanation)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        callbacks.requestPermissions(permissions, requestCode);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        Toast.makeText(activity, R.string.permission_denied, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    /**
     * 显示权限设置对话框（引导用户去系统设置开启权限）
     */
    public void showPermissionSettingsDialog() {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.permission_settings)
                .setMessage(R.string.permission_settings_explanation)
                .setPositiveButton(R.string.go_to_settings, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(android.net.Uri.fromParts("package", activity.getPackageName(), null));
                        activity.startActivity(intent);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    /**
     * 显示蓝牙增强扫描强度选择对话框
     */
    public void showBluetoothEnhanceOptions(final int currentLevel) {
        String[] options = {
            activity.getString(R.string.scan_intensity_off),
            activity.getString(R.string.scan_intensity_low),
            activity.getString(R.string.scan_intensity_high)
        };

        new AlertDialog.Builder(activity)
                .setTitle(R.string.bluetooth_enhance)
                .setSingleChoiceItems(options, currentLevel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        LogUtil.d("MainDialogHelper", "Scan intensity changed: " + currentLevel + " -> " + which);

                        // 先停止当前扫描
                        if (callbacks.getLocationOptimizationManager() != null) {
                            callbacks.getLocationOptimizationManager().stopBluetoothScanning();
                        }
                        if (callbacks.getScanningIndicator() != null) {
                            callbacks.getScanningIndicator().setVisibility(View.GONE);
                        }

                        // 根据新强度启动扫描
                        switch (which) {
                            case 0: // 关闭
                                LogUtil.d("MainDialogHelper", "Scan intensity: OFF");
                                break;
                            case 1: // 低 - 单次扫描
                                LogUtil.d("MainDialogHelper", "Scan intensity: LOW (single scan)");
                                break;
                            case 2: // 高 - 持续循环扫描
                                LogUtil.d("MainDialogHelper", "Scan intensity: HIGH (continuous)");
                                break;
                        }

                        callbacks.onScanIntensitySelected(which);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 显示登录对话框
     */
    public void showLoginDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(activity);
        builder.setTitle(R.string.login);

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText usernameInput = new EditText(activity);
        usernameInput.setHint(R.string.username);
        usernameInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        layout.addView(usernameInput);

        final EditText passwordInput = new EditText(activity);
        passwordInput.setHint(R.string.password);
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addPasswordToggle(passwordInput);
        layout.addView(passwordInput);

        builder.setView(layout);

        builder.setPositiveButton(R.string.login, null);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setNeutralButton(R.string.forgot_password, null);

        android.app.AlertDialog dialog = builder.create();
        dialog.show();

        // 重写按钮点击，防止自动关闭
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String username = usernameInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (!UserApiService.isValidUsername(username)) {
                Toast.makeText(activity, R.string.username_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!UserApiService.isValidPassword(password)) {
                Toast.makeText(activity, R.string.password_invalid, Toast.LENGTH_SHORT).show();
                return;
            }

            new Thread(() -> {
                NewApiService.ApiResponse response = UserApiService.getInstance().login(username, password);
                activity.runOnUiThread(() -> {
                    if (response.isSuccess() && response.getToken() != null) {
                        // 保存登录状态
                        String token = response.getToken();
                        String nickname = response.getNickName();
                        String email = response.getCid(); // email存储在cid字段

                        activity.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE).edit()
                            .putString("auth_token", token)
                            .putString("user_username", username)
                            .putString("user_nickname", nickname != null ? nickname : username)
                            .putString("user_email", email != null ? email : "")
                            .remove("selected_device_id") // 登录时清除旧的选中设备，以便自动选中第一个绑定设备
                            .apply();
                        Toast.makeText(activity, R.string.login_success, Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        // 更新ProfileFragment
                        callbacks.refreshProfileFragment();
                        // 登录成功后获取用户绑定的设备列表
                        callbacks.onLoginSuccess(token, username, nickname, email);
                    } else {
                        String msg = response.getMessage();
                        if (msg != null && msg.contains("not found")) {
                            Toast.makeText(activity, R.string.user_not_found, Toast.LENGTH_SHORT).show();
                        } else if (msg != null && msg.contains("password")) {
                            Toast.makeText(activity, R.string.wrong_password, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(activity, R.string.login_failed, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }).start();
        });

        // 忘记密码
        dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            Toast.makeText(activity, R.string.reset_password_contact_admin, Toast.LENGTH_LONG).show();
        });
    }

    /**
     * 显示注册对话框
     */
    public void showRegisterDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(activity);
        builder.setTitle(R.string.register);

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText usernameInput = new EditText(activity);
        usernameInput.setHint(R.string.username);
        usernameInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        layout.addView(usernameInput);

        TextView usernameHint = new TextView(activity);
        usernameHint.setText(activity.getString(R.string.username_rule));
        usernameHint.setTextSize(12);
        usernameHint.setTextColor(activity.getResources().getColor(R.color.text_secondary, null));
        layout.addView(usernameHint);

        final EditText passwordInput = new EditText(activity);
        passwordInput.setHint(R.string.password);
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addPasswordToggle(passwordInput);
        layout.addView(passwordInput);

        TextView passwordHint = new TextView(activity);
        passwordHint.setText(activity.getString(R.string.password_rule));
        passwordHint.setTextSize(12);
        passwordHint.setTextColor(activity.getResources().getColor(R.color.text_secondary, null));
        layout.addView(passwordHint);

        final EditText confirmPasswordInput = new EditText(activity);
        confirmPasswordInput.setHint(R.string.confirm_password);
        confirmPasswordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addPasswordToggle(confirmPasswordInput);
        layout.addView(confirmPasswordInput);

        builder.setView(layout);

        builder.setPositiveButton(R.string.register, null);
        builder.setNegativeButton(R.string.cancel, null);

        android.app.AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String username = usernameInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            String confirmPassword = confirmPasswordInput.getText().toString().trim();

            if (!UserApiService.isValidUsername(username)) {
                Toast.makeText(activity, R.string.username_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!UserApiService.isValidPassword(password)) {
                Toast.makeText(activity, R.string.password_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.equals(confirmPassword)) {
                Toast.makeText(activity, R.string.password_not_match, Toast.LENGTH_SHORT).show();
                return;
            }

            new Thread(() -> {
                NewApiService.ApiResponse response = UserApiService.getInstance().register(username, password);
                activity.runOnUiThread(() -> {
                    if (response.isSuccess()) {
                        Toast.makeText(activity, R.string.register_success, Toast.LENGTH_SHORT).show();
                        // 注册成功后自动登录
                        new Thread(() -> {
                            NewApiService.ApiResponse loginResp = UserApiService.getInstance().login(username, password);
                            activity.runOnUiThread(() -> {
                                if (loginResp.isSuccess() && loginResp.getToken() != null) {
                                    String token = loginResp.getToken();
                                    activity.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE).edit()
                                        .putString("auth_token", token)
                                        .putString("user_username", username)
                                        .remove("selected_device_id") // 注册登录时清除旧的选中设备
                                        .apply();
                                    Toast.makeText(activity, R.string.login_success, Toast.LENGTH_SHORT).show();
                                    callbacks.refreshProfileFragment();
                                    // 注册后登录成功，获取绑定设备列表（新用户通常没有设备）
                                    callbacks.onLoginSuccess(token, username, null, null);
                                }
                            });
                        }).start();
                        dialog.dismiss();
                    } else {
                        String msg = response.getMessage();
                        if (msg != null && (msg.contains("exist") || msg.contains("already"))) {
                            Toast.makeText(activity, R.string.username_exists, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(activity, R.string.register_failed, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }).start();
        });
    }

    /**
     * 为密码输入框添加显示/隐藏密码的切换功能
     */
    private void addPasswordToggle(EditText passwordInput) {
        passwordInput.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_menu_view, 0);
        passwordInput.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                // 检查是否点击了右侧图标区域
                if (event.getRawX() >= (passwordInput.getRight() - passwordInput.getCompoundDrawables()[2].getBounds().width() - passwordInput.getPaddingRight())) {
                    int currentInputType = passwordInput.getInputType();
                    if ((currentInputType & android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0 ||
                        (currentInputType & android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD) != 0) {
                        // 当前是密码模式，切换为可见
                        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                        passwordInput.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_menu_view, 0);
                    } else {
                        // 当前是可见模式，切换为密码
                        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        passwordInput.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_menu_view, 0);
                    }
                    // 保持光标在末尾
                    passwordInput.setSelection(passwordInput.getText().length());
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * 语言选项数据类
     */
    private static class LanguageItem {
        String flag;
        String name;
        String code;

        LanguageItem(String flag, String name, String code) {
            this.flag = flag;
            this.name = name;
            this.code = code;
        }
    }
}
