package com.RockiotTag.tag;

import com.RockiotTag.tag.util.ToastHelper;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.util.LogUtil;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.RockiotTag.tag.util.BoundDevicesHelper;
import com.RockiotTag.tag.util.TagPickerHelper;
import com.RockiotTag.tag.util.ThemedDialogHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DeviceListActivity extends AppCompatActivity {

    private static final String TAG = "DeviceListActivity";
    private ListView boundDeviceListView;
    private TextView emptyText;
    private BoundDeviceAdapter boundDeviceAdapter;
    private List<TagDevice> boundDeviceList;
    private DatabaseHelper databaseHelper;
    private NewApiService apiService;
    private Handler handler;
    private SharedPreferences prefs;
    private UnboundDeviceManager unboundDeviceManager;
    
    private boolean isMultiSelectMode = false;
    private Set<Integer> selectedPositions = new HashSet<>();
    private Button multiSelectBtn;
    private Button selectAllBtn;
    private Button unbindSelectedBtn;
    private LinearLayout multiSelectPanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        setContentView(R.layout.activity_device_list);

        LinearLayout titleBar = findViewById(R.id.title_bar);
        if (titleBar != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                titleBar.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                    @Override
                    public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                        LogUtil.d(TAG, "statusBarInsets: " + insets.getInsets(WindowInsets.Type.statusBars()).top);
                        v.setPadding(0, insets.getInsets(WindowInsets.Type.statusBars()).top, 0, 0);
                        return insets;
                    }
                });
            } else {
                int statusBarHeight = getStatusBarHeight();
                titleBar.setPadding(0, statusBarHeight, 0, 0);
            }
        }

        databaseHelper = DatabaseHelper.getInstance(this);
        apiService = NewApiService.getInstance();
        SharedPreferencesManager.loadAuth(this);
        handler = new Handler(android.os.Looper.getMainLooper());
        
        prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        unboundDeviceManager = UnboundDeviceManager.getInstance(this);

        ImageButton backBtn = findViewById(R.id.back_btn);
        if (backBtn != null) {
            backBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isMultiSelectMode) {
                        exitMultiSelectMode();
                    } else {
                        finish();
                    }
                }
            });
        }

        Button addDeviceBtn = findViewById(R.id.add_device_btn);
        if (addDeviceBtn != null) {
            addDeviceBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!BoundDevicesHelper.isLoggedIn(DeviceListActivity.this)) {
                        ToastHelper.show(DeviceListActivity.this, R.string.please_login_first);
                        return;
                    }
                    Intent intent = new Intent(DeviceListActivity.this, AddDeviceActivity.class);
                    startActivity(intent);
                }
            });
        }
        
        multiSelectBtn = findViewById(R.id.multi_select_btn);
        selectAllBtn = findViewById(R.id.select_all_btn);
        unbindSelectedBtn = findViewById(R.id.unbind_selected_btn);
        multiSelectPanel = findViewById(R.id.multi_select_panel);
        
        if (multiSelectBtn != null) {
            multiSelectBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isMultiSelectMode) {
                        exitMultiSelectMode();
                    } else {
                        enterMultiSelectMode();
                    }
                }
            });
        }
        
        if (selectAllBtn != null) {
            selectAllBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleSelectAll();
                }
            });
        }
        
        if (unbindSelectedBtn != null) {
            unbindSelectedBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showBatchUnbindDialog();
                }
            });
        }

        boundDeviceListView = findViewById(R.id.bound_device_list);
        emptyText = findViewById(R.id.empty_text);

        boundDeviceList = new ArrayList<>();
        boundDeviceAdapter = new BoundDeviceAdapter(this, boundDeviceList);
        boundDeviceAdapter.setOnDeviceEditListener(new BoundDeviceAdapter.OnDeviceEditListener() {
            @Override
            public void onEditDevice(TagDevice device, int position) {
                if (!isMultiSelectMode) {
                    showEditDeviceDialog(device, position);
                }
            }
        });
        boundDeviceAdapter.setOnCheckedChangeListener(new BoundDeviceAdapter.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(int position, boolean isChecked) {
                if (isChecked) {
                    selectedPositions.add(position);
                } else {
                    selectedPositions.remove(position);
                }
                updateUnbindButton();
            }
        });
        boundDeviceListView.setAdapter(boundDeviceAdapter);

        boundDeviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                LogUtil.d(TAG, "=== onItemClick called, position: " + position + " ===");
                if (isMultiSelectMode) {
                    toggleSelection(position);
                } else {
                    if (position >= 0 && position < boundDeviceList.size()) {
                        TagDevice device = boundDeviceList.get(position);
                        LogUtil.d(TAG, "Clicked device: " + (device != null ? device.getName() : "null"));
                        selectDeviceAndFinish(device);
                    } else {
                        Log.e(TAG, "Invalid position: " + position + ", list size: " + boundDeviceList.size());
                    }
                }
            }
        });

        boundDeviceListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (!isMultiSelectMode) {
                    TagDevice device = boundDeviceList.get(position);
                    showUnbindDialog(device, position);
                }
                return true;
            }
        });

        loadBoundDevices();
        applyTheme(ThemedDialogHelper.isDarkModeEnabled(this));
    }
    
    private void enterMultiSelectMode() {
        isMultiSelectMode = true;
        selectedPositions.clear();
        multiSelectBtn.setText(getString(R.string.cancel_multi_select));
        multiSelectPanel.setVisibility(View.VISIBLE);
        boundDeviceAdapter.setMultiSelectMode(true);
        updateUnbindButton();
    }
    
    private void exitMultiSelectMode() {
        isMultiSelectMode = false;
        selectedPositions.clear();
        multiSelectBtn.setText(getString(R.string.multi_select));
        multiSelectPanel.setVisibility(View.GONE);
        boundDeviceAdapter.setMultiSelectMode(false);
        boundDeviceAdapter.clearSelections();
    }
    
    private void toggleSelection(int position) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position);
            boundDeviceAdapter.setSelection(position, false);
        } else {
            selectedPositions.add(position);
            boundDeviceAdapter.setSelection(position, true);
        }
        updateUnbindButton();
    }
    
    private void toggleSelectAll() {
        if (selectedPositions.size() == boundDeviceList.size()) {
            selectedPositions.clear();
            boundDeviceAdapter.clearSelections();
            selectAllBtn.setText(getString(R.string.select_all));
        } else {
            selectedPositions.clear();
            for (int i = 0; i < boundDeviceList.size(); i++) {
                selectedPositions.add(i);
            }
            boundDeviceAdapter.selectAll();
            selectAllBtn.setText(getString(R.string.deselect_all));
        }
        updateUnbindButton();
    }
    
    private void updateUnbindButton() {
        int count = selectedPositions.size();
        unbindSelectedBtn.setText(getString(R.string.unbind_count, count));
        unbindSelectedBtn.setEnabled(count > 0);
        
        if (selectedPositions.size() == boundDeviceList.size() && boundDeviceList.size() > 0) {
            selectAllBtn.setText(getString(R.string.deselect_all));
        } else {
            selectAllBtn.setText(getString(R.string.select_all));
        }
    }
    
    private void showBatchUnbindDialog() {
        if (selectedPositions.isEmpty()) {
            ToastHelper.show(this, R.string.please_select_devices);
            return;
        }
        
        AlertDialog.Builder builder = ThemedDialogHelper.createBuilder(this);
        builder.setTitle(getString(R.string.batch_unbind));
        builder.setMessage(getString(R.string.batch_unbind_confirm, selectedPositions.size()));
        builder.setPositiveButton(getString(R.string.confirm), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                batchUnbindDevices();
            }
        });
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.show();
    }
    
    private void batchUnbindDevices() {
        final List<TagDevice> devicesToUnbind = new ArrayList<>();
        for (Integer position : selectedPositions) {
            if (position >= 0 && position < boundDeviceList.size()) {
                devicesToUnbind.add(boundDeviceList.get(position));
            }
        }
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<String> failedDevices = new ArrayList<>();
                    final List<String> successDevices = new ArrayList<>();
                    String token = prefs.getString("auth_token", null);
                    
                    for (TagDevice device : devicesToUnbind) {
                        try {
                            String deviceNum = device.getDeviceNum();
                            
                            // 1. 先调用后端解绑 API
                            if (deviceNum != null && !deviceNum.isEmpty() && token != null && !token.isEmpty()) {
                                DeviceApiService.DeviceApiResponse apiResponse = 
                                    DeviceApiService.getInstance().unbindDevice(token, deviceNum);
                                if (!apiResponse.isSuccess()) {
                                    failedDevices.add(device.getName() + "(" + apiResponse.getMessage() + ")");
                                    Log.e(TAG, "Server unbind failed for " + device.getName() + ": " + apiResponse.getMessage());
                                    continue;
                                }
                                // 2. 更新 bound_devices SharedPreferences
                                BoundDevicesHelper.removeDevice(DeviceListActivity.this, deviceNum);
                            }
                            
                            // 3. 记录到已解绑列表
                            if (deviceNum != null && !deviceNum.isEmpty()) {
                                unboundDeviceManager.addUnboundDevice(deviceNum);
                            }
                            
                            // 4. 删除本地数据库记录
                            String selectedDeviceId = prefs.getString("selected_device_id", "");
                            if (selectedDeviceId.equals(device.getDeviceId())) {
                                prefs.edit().remove("selected_device_id").apply();
                            }
                            
                            databaseHelper.deleteDevice(device.getDeviceId());
                            successDevices.add(device.getName());
                        } catch (Exception e) {
                            failedDevices.add(device.getName());
                            Log.e(TAG, "Failed to unbind device: " + device.getName() + ", error: " + e.getMessage());
                        }
                    }
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            exitMultiSelectMode();
                            loadBoundDevices();
                            
                            String message = "";
                            if (!successDevices.isEmpty()) {
                                message += getString(R.string.unbind_success_count, successDevices.size());
                            }
                            if (!failedDevices.isEmpty()) {
                                message += getString(R.string.unbind_failed_count, failedDevices.size());
                            }
                            ToastHelper.showLong(DeviceListActivity.this, message);
                            
                            setResult(RESULT_OK);
                        }
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error in batch unbind: " + e.getMessage(), e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ToastHelper.show(DeviceListActivity.this, getString(R.string.batch_unbind_failed, e.getMessage()));
                        }
                    });
                }
            }
        }).start();
    }

    private void applyTheme(boolean isDarkMode) {
        int bgColor = getResources().getColor(isDarkMode ? R.color.dark_background : R.color.background, null);
        int topBarColor = getResources().getColor(isDarkMode ? R.color.dark_top_bar_background : R.color.top_bar_background, null);
        int onSurfaceColor = getResources().getColor(isDarkMode ? R.color.dark_onSurface : R.color.onSurface, null);
        int textSecColor = getResources().getColor(isDarkMode ? R.color.dark_text_secondary : R.color.text_secondary, null);
        int cardColor = getResources().getColor(isDarkMode ? R.color.dark_card_background : R.color.white, null);
        int dividerColor = getResources().getColor(isDarkMode ? R.color.dark_divider : R.color.text_secondary, null);

        View root = findViewById(R.id.device_list_root);
        if (root != null) {
            root.setBackgroundColor(bgColor);
        }

        LinearLayout titleBar = findViewById(R.id.title_bar);
        if (titleBar != null) {
            titleBar.setBackgroundColor(topBarColor);
            for (int i = 0; i < titleBar.getChildCount(); i++) {
                View child = titleBar.getChildAt(i);
                if (child instanceof TextView) {
                    ((TextView) child).setTextColor(onSurfaceColor);
                }
            }
        }

        if (boundDeviceListView != null) {
            boundDeviceListView.setBackgroundColor(cardColor);
            boundDeviceListView.setDivider(new android.graphics.drawable.ColorDrawable(dividerColor));
        }
        if (emptyText != null) {
            emptyText.setTextColor(textSecColor);
            emptyText.setBackgroundColor(bgColor);
        }

        com.RockiotTag.tag.util.StatusBarHelper.setupStatusBar(this, isDarkMode, topBarColor);

        if (boundDeviceAdapter != null) {
            boundDeviceAdapter.setDarkMode(isDarkMode);
            boundDeviceAdapter.notifyDataSetChanged();
        }
    }

    private void showEditDeviceDialog(final TagDevice device, final int position) {
        final boolean darkMode = ThemedDialogHelper.isDarkModeEnabled(this);
        int labelColor = getResources().getColor(darkMode ? R.color.dark_text_secondary : R.color.text_secondary, null);
        int valueColor = getResources().getColor(darkMode ? R.color.dark_onSurface : R.color.onSurface, null);

        AlertDialog.Builder builder = ThemedDialogHelper.createBuilder(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final AlertDialog[] dialogHolder = new AlertDialog[1];
        layout.addView(ThemedDialogHelper.createEditDeviceTitleBar(this, v -> {
            if (dialogHolder[0] != null) {
                dialogHolder[0].dismiss();
            }
            showUnbindDialog(device, position);
        }));

        TextView deviceNumLabel = new TextView(this);
        deviceNumLabel.setText(getString(R.string.device_num));
        deviceNumLabel.setTextSize(12);
        deviceNumLabel.setTextColor(labelColor);
        layout.addView(deviceNumLabel);

        TextView deviceNumText = new TextView(this);
        deviceNumText.setText(device.getDeviceNum() != null ? device.getDeviceNum() : device.getDeviceId());
        deviceNumText.setTextSize(16);
        deviceNumText.setTextColor(valueColor);
        deviceNumText.setPadding(0, 0, 0, 24);
        layout.addView(deviceNumText);

        TextView nickNameLabel = new TextView(this);
        nickNameLabel.setText(getString(R.string.device_nickname));
        nickNameLabel.setTextSize(12);
        nickNameLabel.setTextColor(labelColor);
        layout.addView(nickNameLabel);

        final EditText nickNameEditText = new EditText(this);
        nickNameEditText.setHint(getString(R.string.enter_device_nickname));
        nickNameEditText.setText(device.getName());
        nickNameEditText.setSingleLine();
        if (darkMode) {
            nickNameEditText.setTextColor(valueColor);
            nickNameEditText.setHintTextColor(labelColor);
        }
        nickNameEditText.setPadding(0, 0, 0, 16);
        layout.addView(nickNameEditText);

        TextView tagLabel = new TextView(this);
        tagLabel.setText(getString(R.string.device_tag));
        tagLabel.setTextSize(12);
        tagLabel.setTextColor(labelColor);
        layout.addView(tagLabel);

        final java.util.List<String> tagList = TagPickerHelper.buildTagList(this);

        final Spinner tagSpinner = new Spinner(this);
        TagPickerHelper.setupTagSpinner(this, tagSpinner, device.getTag());
        layout.addView(tagSpinner);

        builder.setView(layout);

        builder.setPositiveButton(getString(R.string.save), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String newNickName = nickNameEditText.getText().toString().trim();

                if (newNickName.isEmpty()) {
                    ToastHelper.show(DeviceListActivity.this, R.string.nickname_empty);
                    return;
                }

                int tagPosition = tagSpinner.getSelectedItemPosition();
                String newTag = TagPickerHelper.getTagFromPosition(tagList, tagPosition);

                // 直接从文本获取设备号，确保值正确
                final String deviceNumFromUI = deviceNumText.getText().toString().trim();
                final String deviceIdFromDevice = device.getDeviceId();
                final String deviceNumFromDevice = device.getDeviceNum();
                final String finalNickName = newNickName;
                final String finalTag = newTag;

                LogUtil.d(TAG, "=== Save button clicked ===");
                LogUtil.d(TAG, "deviceIdFromDevice: " + deviceIdFromDevice);
                LogUtil.d(TAG, "deviceNumFromDevice: " + deviceNumFromDevice);
                LogUtil.d(TAG, "deviceNumFromUI: " + deviceNumFromUI);
                LogUtil.d(TAG, "newName: " + finalNickName);
                LogUtil.d(TAG, "newTag: " + finalTag);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // 更新服务器（传递customerCode以确保使用正确的API Key）
                            if (!deviceNumFromUI.isEmpty()) {
                                String customerCode = device.getCustomerCode();
                                NewApiService.ApiResponse response = apiService.updateDevice(ApiConfig.getMyServerUrl(deviceNumFromUI), deviceNumFromUI, finalNickName, customerCode);
                                LogUtil.d(TAG, "Server update response: " + (response != null ? response.isSuccess() : "null"));
                                if (response != null && !response.isSuccess()) {
                                    Log.e(TAG, "Server update failed: " + response.getMessage());
                                }
                            }
                            
                            // 使用设备号更新数据库
                            boolean updated = databaseHelper.updateDeviceNameAndTag(
                                deviceIdFromDevice, 
                                deviceNumFromUI, 
                                finalNickName, 
                                finalTag
                            );
                            
                            LogUtil.d(TAG, "Database update result: " + updated);
                            
                            // 验证更新是否成功
                            TagDevice verifyDevice = databaseHelper.getDevice(deviceIdFromDevice);
                            if (verifyDevice != null) {
                                LogUtil.d(TAG, "VERIFICATION: After update, device name = " + verifyDevice.getName() + ", tag = " + verifyDevice.getTag());
                            }
                            
                            // 更新 device 对象
                            device.setName(finalNickName);
                            device.setTag(finalTag);
                            
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    BoundDevicesHelper.updateNickName(
                                            DeviceListActivity.this, deviceNumFromUI, finalNickName);
                                    LogUtil.d(TAG, "=== UI Thread: Reloading devices ===");
                                                                        
                                    // 方法1：直接更新列表中的设备对象
                                    boolean found = false;
                                    for (int i = 0; i < boundDeviceList.size(); i++) {
                                        TagDevice d = boundDeviceList.get(i);
                                        if (d.getDeviceId().equals(deviceIdFromDevice)) {
                                            d.setName(finalNickName);
                                            d.setTag(finalTag);
                                            LogUtil.d(TAG, "✅ Updated device in list at position " + i + ": " + d.getName());
                                            found = true;
                                            break;
                                        }
                                    }
                                                                        
                                    if (!found) {
                                        Log.w(TAG, "⚠️ Device not found in list, reloading all...");
                                    }
                                                                        
                                    // 方法2：重新从数据库加载
                                    loadBoundDevices();
                                                                        
                                    // 方法3：强制刷新 Adapter
                                    boundDeviceAdapter.notifyDataSetChanged();
                                                                        
                                    String selectedDeviceId = prefs.getString("selected_device_id", "");
                                    if (selectedDeviceId.equals(deviceIdFromDevice)) {
                                        prefs.edit().putString("selected_device_id", deviceIdFromDevice).apply();
                                        LogUtil.d(TAG, "Updated selected device in preferences");
                                    }
                                                                        
                                    setResult(RESULT_OK);
                                                                        
                                    if (updated) {
                                        ToastHelper.show(DeviceListActivity.this,
                                            "保存成功");
                                    } else {
                                        ToastHelper.showLong(DeviceListActivity.this,
                                            "保存可能失败，请检查日志");
                                    }
                                }
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating device: " + e.getMessage(), e);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ToastHelper.showLong(DeviceListActivity.this, 
                                        "❌ 更新失败: " + e.getMessage());
                                }
                            });
                        }
                    }
                }).start();
            }
        });

        builder.setNegativeButton(getString(R.string.cancel), null);

        dialogHolder[0] = builder.show();
    }

    private void showUnbindDialog(final TagDevice device, final int position) {
        AlertDialog.Builder builder = ThemedDialogHelper.createBuilder(this);
        builder.setTitle(getString(R.string.unbind_device));
        builder.setMessage(getString(R.string.unbind_confirm_message, device.getName()));
        builder.setPositiveButton(getString(R.string.confirm), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                unbindDeviceLocal(device, position);
            }
        });
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.show();
    }

    private void unbindDeviceLocal(final TagDevice device, final int position) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    LogUtil.d(TAG, "Starting unbind for device: " + device.getName());
                    LogUtil.d(TAG, "Device ID: " + device.getDeviceId());
                    LogUtil.d(TAG, "Device Num: " + device.getDeviceNum());
                    
                    // 1. 先调用后端解绑 API
                    String deviceNum = device.getDeviceNum();
                    if (deviceNum != null && !deviceNum.isEmpty()) {
                        String token = prefs.getString("auth_token", null);
                        if (token != null && !token.isEmpty()) {
                            DeviceApiService.DeviceApiResponse apiResponse = 
                                DeviceApiService.getInstance().unbindDevice(token, deviceNum);
                            LogUtil.d(TAG, "Server unbind response: success=" + apiResponse.isSuccess() 
                                + ", message=" + apiResponse.getMessage());
                            
                            if (!apiResponse.isSuccess()) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        ToastHelper.showLong(DeviceListActivity.this, 
                                            getString(R.string.unbind_failed, apiResponse.getMessage()));
                                    }
                                });
                                return;
                            }
                            
                            // 2. 更新 bound_devices SharedPreferences
                            BoundDevicesHelper.removeDevice(DeviceListActivity.this, deviceNum);
                            LogUtil.d(TAG, "Removed from bound_devices: " + deviceNum);
                        }
                    }
                    
                    // 3. 记录到已解绑列表（防止自动重新绑定）
                    if (deviceNum != null && !deviceNum.isEmpty()) {
                        unboundDeviceManager.addUnboundDevice(deviceNum);
                        LogUtil.d(TAG, "Added to unbound list: " + deviceNum);
                    }
                    
                    // 4. 删除本地数据库记录
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String selectedDeviceId = prefs.getString("selected_device_id", "");
                            if (selectedDeviceId.equals(device.getDeviceId())) {
                                prefs.edit().remove("selected_device_id").apply();
                                LogUtil.d(TAG, "Removed selected_device_id from preferences");
                            }
                            
                            databaseHelper.deleteDevice(device.getDeviceId());
                            boundDeviceList.remove(position);
                            boundDeviceAdapter.notifyDataSetChanged();
                            updateEmptyView();
                            
                            setResult(RESULT_OK);
                            ToastHelper.show(DeviceListActivity.this, R.string.device_unbound);
                        }
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error unbinding device: " + e.getMessage(), e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ToastHelper.show(DeviceListActivity.this, getString(R.string.unbind_failed, e.getMessage()));
                        }
                    });
                }
            }
        }).start();
    }

    private void loadBoundDevices() {
        LogUtil.d(TAG, "=== loadBoundDevices called ===");

        boundDeviceList.clear();

        List<TagDevice> devices = BoundDevicesHelper.loadDisplayedDevices(this, databaseHelper);
        LogUtil.d(TAG, "Loaded " + devices.size() + " displayed devices (loggedIn="
                + BoundDevicesHelper.isLoggedIn(this) + ")");

        if (!devices.isEmpty()) {
            java.util.Collections.sort(devices, new java.util.Comparator<TagDevice>() {
                @Override
                public int compare(TagDevice d1, TagDevice d2) {
                    String num1 = d1.getDeviceNum() != null ? d1.getDeviceNum() : d1.getDeviceId();
                    String num2 = d2.getDeviceNum() != null ? d2.getDeviceNum() : d2.getDeviceId();

                    try {
                        long n1 = Long.parseLong(num1);
                        long n2 = Long.parseLong(num2);
                        return Long.compare(n1, n2);
                    } catch (NumberFormatException e) {
                        return num1.compareTo(num2);
                    }
                }
            });

            for (TagDevice d : devices) {
                if (d != null) {
                    LogUtil.d(TAG, "  Device: " + d.getName() + ", id=" + d.getDeviceId()
                            + ", num=" + d.getDeviceNum() + ", tag=" + d.getTag());
                }
            }
            boundDeviceList.addAll(devices);
        }

        // 已登录但无缓存时尝试拉取（与 DeviceListFragment 共用防重试逻辑）
        String token = BoundDevicesHelper.getAuthToken(this);
        if (token != null && !token.isEmpty() && BoundDevicesHelper.needsServerFetch(this)) {
            BoundDevicesHelper.fetchBoundDevicesFromServer(this, token,
                    new BoundDevicesHelper.FetchCallback() {
                        @Override
                        public void onSuccess(List<DeviceApiService.BoundDevice> boundDevices) {
                            runOnUiThread(() -> {
                                loadBoundDevices();
                            });
                        }

                        @Override
                        public void onFailure(String message) {
                            LogUtil.e(TAG, "fetchBoundDevices failed in Activity: " + message);
                        }
                    });
        }

        boundDeviceAdapter.notifyDataSetChanged();
        updateEmptyView();

        LogUtil.d(TAG, "Adapter count: " + boundDeviceAdapter.getCount());
        LogUtil.d(TAG, "=== loadBoundDevices completed ===");
    }

    private void updateEmptyView() {
        if (boundDeviceList.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            boundDeviceListView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            boundDeviceListView.setVisibility(View.VISIBLE);
        }
    }

    private void selectDeviceAndFinish(TagDevice device) {
        LogUtil.d(TAG, "=== selectDeviceAndFinish called ===");
        LogUtil.d(TAG, "Device: " + device.getName() + ", deviceId: " + device.getDeviceId() + ", deviceNum: " + device.getDeviceNum());
        
        if (device == null) {
            Log.e(TAG, "Device is null, cannot select");
            return;
        }
        
        String deviceId = device.getDeviceId();
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = device.getDeviceNum();
        }
        
        if (deviceId == null || deviceId.isEmpty()) {
            Log.e(TAG, "Device ID is null or empty, cannot select");
            ToastHelper.show(this, R.string.invalid_device_id);
            return;
        }
        
        prefs.edit().putString("selected_device_id", deviceId).apply();
        LogUtil.d(TAG, "Saved selected_device_id: " + deviceId);
        
        setResult(RESULT_OK);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBoundDevices();
    }
    
    @Override
    public void onBackPressed() {
        if (isMultiSelectMode) {
            exitMultiSelectMode();
        } else {
            super.onBackPressed();
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

    @Override
    protected void onDestroy() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        super.onDestroy();
    }
}
