package com.RockiotTag.tag;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

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
    private List<Device> boundDeviceList;
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            titleBar.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    android.util.Log.d(TAG, "statusBarInsets: " + insets.getInsets(WindowInsets.Type.statusBars()).top);
                    v.setPadding(0, insets.getInsets(WindowInsets.Type.statusBars()).top, 0, 0);
                    return insets;
                }
            });
        } else {
            int statusBarHeight = getStatusBarHeight();
            titleBar.setPadding(0, statusBarHeight, 0, 0);
        }

        databaseHelper = new DatabaseHelper(this);
        apiService = NewApiService.getInstance();
        SharedPreferencesManager.loadAuth(this);
        handler = new Handler();
        
        prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        unboundDeviceManager = UnboundDeviceManager.getInstance(this);

        ImageButton backBtn = findViewById(R.id.back_btn);
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

        Button addDeviceBtn = findViewById(R.id.add_device_btn);
        addDeviceBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DeviceListActivity.this, AddDeviceActivity.class);
                startActivity(intent);
            }
        });
        
        multiSelectBtn = findViewById(R.id.multi_select_btn);
        selectAllBtn = findViewById(R.id.select_all_btn);
        unbindSelectedBtn = findViewById(R.id.unbind_selected_btn);
        multiSelectPanel = findViewById(R.id.multi_select_panel);
        
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
        
        selectAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSelectAll();
            }
        });
        
        unbindSelectedBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBatchUnbindDialog();
            }
        });

        boundDeviceListView = findViewById(R.id.bound_device_list);
        emptyText = findViewById(R.id.empty_text);

        boundDeviceList = new ArrayList<>();
        boundDeviceAdapter = new BoundDeviceAdapter(this, boundDeviceList);
        boundDeviceAdapter.setOnDeviceEditListener(new BoundDeviceAdapter.OnDeviceEditListener() {
            @Override
            public void onEditDevice(Device device, int position) {
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
                Log.d(TAG, "=== onItemClick called, position: " + position + " ===");
                if (isMultiSelectMode) {
                    toggleSelection(position);
                } else {
                    if (position >= 0 && position < boundDeviceList.size()) {
                        Device device = boundDeviceList.get(position);
                        Log.d(TAG, "Clicked device: " + (device != null ? device.getName() : "null"));
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
                    Device device = boundDeviceList.get(position);
                    showUnbindDialog(device, position);
                }
                return true;
            }
        });

        loadBoundDevices();
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
            Toast.makeText(this, R.string.please_select_devices, Toast.LENGTH_SHORT).show();
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
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
        final List<Device> devicesToUnbind = new ArrayList<>();
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
                    
                    for (Device device : devicesToUnbind) {
                        try {
                            String deviceNum = device.getDeviceNum();
                            if (deviceNum != null && !deviceNum.isEmpty()) {
                                unboundDeviceManager.addUnboundDevice(deviceNum);
                            }
                            
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
                            Toast.makeText(DeviceListActivity.this, message, Toast.LENGTH_LONG).show();
                            
                            setResult(RESULT_OK);
                        }
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error in batch unbind: " + e.getMessage(), e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(DeviceListActivity.this, getString(R.string.batch_unbind_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void showEditDeviceDialog(final Device device, final int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.edit_device));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        TextView deviceNumLabel = new TextView(this);
        deviceNumLabel.setText(getString(R.string.device_num));
        deviceNumLabel.setTextSize(12);
        deviceNumLabel.setTextColor(0xFF888888);
        layout.addView(deviceNumLabel);

        TextView deviceNumText = new TextView(this);
        deviceNumText.setText(device.getDeviceNum() != null ? device.getDeviceNum() : device.getDeviceId());
        deviceNumText.setTextSize(16);
        deviceNumText.setTextColor(0xFF333333);
        deviceNumText.setPadding(0, 0, 0, 24);
        layout.addView(deviceNumText);

        TextView nickNameLabel = new TextView(this);
        nickNameLabel.setText(getString(R.string.device_nickname));
        nickNameLabel.setTextSize(12);
        nickNameLabel.setTextColor(0xFF888888);
        layout.addView(nickNameLabel);

        final EditText nickNameEditText = new EditText(this);
        nickNameEditText.setHint(getString(R.string.enter_device_nickname));
        nickNameEditText.setText(device.getName());
        nickNameEditText.setSingleLine();
        nickNameEditText.setPadding(0, 0, 0, 16);
        layout.addView(nickNameEditText);

        TextView tagLabel = new TextView(this);
        tagLabel.setText(getString(R.string.device_tag));
        tagLabel.setTextSize(12);
        tagLabel.setTextColor(0xFF888888);
        layout.addView(tagLabel);

        final java.util.List<String> tagList = new java.util.ArrayList<>(java.util.Arrays.asList(
            getString(R.string.no_tag),
            "dog",
            "boy", 
            "car",
            "bike",
            "bank_card",
            "girl",
            "key",
            "moto",
            "pig",
            "wallet",
            "bag",
            "cat",
            "bird"
        ));

        final java.util.List<String> iconList = new java.util.ArrayList<>(java.util.Arrays.asList(
            "",
            "🐕",
            "👦",
            "🚗",
            "🚲",
            "💳",
            "👧",
            "🔑",
            "🏍️",
            "🐷",
            "👛",
            "👜",
            "🐱",
            "🐦"
        ));

        final Spinner tagSpinner = new Spinner(this);
        TagAdapter tagAdapter = new TagAdapter(this, tagList, iconList);
        tagSpinner.setAdapter(tagAdapter);

        String currentTag = device.getTag();
        int selectedPosition = 0;
        if (currentTag != null && !currentTag.isEmpty()) {
            for (int i = 0; i < tagList.size(); i++) {
                if (tagList.get(i).equals(currentTag)) {
                    selectedPosition = i;
                    break;
                }
            }
        }
        tagSpinner.setSelection(selectedPosition);
        layout.addView(tagSpinner);

        builder.setView(layout);

        builder.setPositiveButton(getString(R.string.save), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String newNickName = nickNameEditText.getText().toString().trim();

                if (newNickName.isEmpty()) {
                    Toast.makeText(DeviceListActivity.this, R.string.nickname_empty, Toast.LENGTH_SHORT).show();
                    return;
                }

                int tagPosition = tagSpinner.getSelectedItemPosition();
                String newTag = tagPosition > 0 ? tagList.get(tagPosition) : "";

                // 直接从文本获取设备号，确保值正确
                final String deviceNumFromUI = deviceNumText.getText().toString().trim();
                final String deviceIdFromDevice = device.getDeviceId();
                final String deviceNumFromDevice = device.getDeviceNum();
                final String finalNickName = newNickName;
                final String finalTag = newTag;

                Log.d(TAG, "=== Save button clicked ===");
                Log.d(TAG, "deviceIdFromDevice: " + deviceIdFromDevice);
                Log.d(TAG, "deviceNumFromDevice: " + deviceNumFromDevice);
                Log.d(TAG, "deviceNumFromUI: " + deviceNumFromUI);
                Log.d(TAG, "newName: " + finalNickName);
                Log.d(TAG, "newTag: " + finalTag);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // 更新服务器
                            if (!deviceNumFromUI.isEmpty()) {
                                NewApiService.setApiBaseUrl(ApiConfig.getMyServerUrl(deviceNumFromUI));
                                NewApiService.ApiResponse response = apiService.updateDevice(deviceNumFromUI, finalNickName);
                                Log.d(TAG, "Server update response: " + (response != null ? response.isSuccess() : "null"));
                            }
                            
                            // 使用设备号更新数据库
                            boolean updated = databaseHelper.updateDeviceNameAndTag(
                                deviceIdFromDevice, 
                                deviceNumFromUI, 
                                finalNickName, 
                                finalTag
                            );
                            
                            Log.d(TAG, "Database update result: " + updated);
                            
                            // 验证更新是否成功
                            Device verifyDevice = databaseHelper.getDevice(deviceIdFromDevice);
                            if (verifyDevice != null) {
                                Log.d(TAG, "VERIFICATION: After update, device name = " + verifyDevice.getName() + ", tag = " + verifyDevice.getTag());
                            }
                            
                            // 更新 device 对象
                            device.setName(finalNickName);
                            device.setTag(finalTag);
                            
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Log.d(TAG, "=== UI Thread: Reloading devices ===");
                                                                        
                                    // 方法1：直接更新列表中的设备对象
                                    boolean found = false;
                                    for (int i = 0; i < boundDeviceList.size(); i++) {
                                        Device d = boundDeviceList.get(i);
                                        if (d.getDeviceId().equals(deviceIdFromDevice)) {
                                            d.setName(finalNickName);
                                            d.setTag(finalTag);
                                            Log.d(TAG, "✅ Updated device in list at position " + i + ": " + d.getName());
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
                                        Log.d(TAG, "Updated selected device in preferences");
                                    }
                                                                        
                                    setResult(RESULT_OK);
                                                                        
                                    if (updated) {
                                        Toast.makeText(DeviceListActivity.this, 
                                            "✅ 保存成功: " + finalNickName, Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(DeviceListActivity.this, 
                                            "⚠️ 保存可能失败，请检查日志", Toast.LENGTH_LONG).show();
                                    }
                                                                        
                                    new android.os.Handler().postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            finish();
                                        }
                                    }, 800);
                                }
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating device: " + e.getMessage(), e);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(DeviceListActivity.this, 
                                        "❌ 更新失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                }).start();
            }
        });

        builder.setNeutralButton(getString(R.string.unbind), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showUnbindDialog(device, position);
            }
        });

        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.show();
    }

    private void showUnbindDialog(final Device device, final int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
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

    private void unbindDeviceLocal(final Device device, final int position) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Starting local unbind for device: " + device.getName());
                    Log.d(TAG, "Device ID: " + device.getDeviceId());
                    Log.d(TAG, "Device Num: " + device.getDeviceNum());
                    
                    String deviceNum = device.getDeviceNum();
                    if (deviceNum != null && !deviceNum.isEmpty()) {
                        unboundDeviceManager.addUnboundDevice(deviceNum);
                        Log.d(TAG, "Added to unbound list: " + deviceNum);
                    }
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String selectedDeviceId = prefs.getString("selected_device_id", "");
                            if (selectedDeviceId.equals(device.getDeviceId())) {
                                prefs.edit().remove("selected_device_id").apply();
                                Log.d(TAG, "Removed selected_device_id from preferences");
                            }
                            
                            databaseHelper.deleteDevice(device.getDeviceId());
                            boundDeviceList.remove(position);
                            boundDeviceAdapter.notifyDataSetChanged();
                            updateEmptyView();
                            
                            setResult(RESULT_OK);
                            Toast.makeText(DeviceListActivity.this, R.string.device_unbound, Toast.LENGTH_SHORT).show();
                        }
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error unbinding device: " + e.getMessage(), e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(DeviceListActivity.this, getString(R.string.unbind_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void loadBoundDevices() {
        Log.d(TAG, "=== loadBoundDevices called ===");
        
        boundDeviceList.clear();
        
        List<Device> devices = databaseHelper.getAllDevices();
        Log.d(TAG, "Loaded " + (devices != null ? devices.size() : 0) + " devices from database");
        
        if (devices != null) {
            java.util.Collections.sort(devices, new java.util.Comparator<Device>() {
                @Override
                public int compare(Device d1, Device d2) {
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
            
            for (Device d : devices) {
                if (d != null) {
                    Log.d(TAG, "  Device: " + d.getName() + ", id=" + d.getDeviceId() + ", num=" + d.getDeviceNum() + ", tag=" + d.getTag());
                }
            }
            boundDeviceList.addAll(devices);
        }
        
        boundDeviceAdapter.notifyDataSetChanged();
        updateEmptyView();
        
        Log.d(TAG, "Adapter count: " + boundDeviceAdapter.getCount());
        Log.d(TAG, "=== loadBoundDevices completed ===");
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

    private void selectDeviceAndFinish(Device device) {
        Log.d(TAG, "=== selectDeviceAndFinish called ===");
        Log.d(TAG, "Device: " + device.getName() + ", deviceId: " + device.getDeviceId() + ", deviceNum: " + device.getDeviceNum());
        
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
            Toast.makeText(this, R.string.invalid_device_id, Toast.LENGTH_SHORT).show();
            return;
        }
        
        prefs.edit().putString("selected_device_id", deviceId).apply();
        Log.d(TAG, "Saved selected_device_id: " + deviceId);
        
        Toast.makeText(this, getString(R.string.selected_device, device.getName()), Toast.LENGTH_SHORT).show();
        
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
}
