package com.RockiotTag.tag;

import android.os.Bundle;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.RockiotTag.tag.repository.DeviceRepository;
import com.RockiotTag.tag.util.BoundDevicesHelper;
import com.RockiotTag.tag.util.TagPickerHelper;
import com.RockiotTag.tag.util.ThemedDialogHelper;

import java.util.ArrayList;
import java.util.List;

public class DeviceListFragment extends Fragment {
    private static final String TAG = "DeviceListFragment";
    private RecyclerView recyclerView;
    private TextView emptyView;
    private ImageButton addDeviceBtn;
    private DeviceListAdapter adapter;
    private DatabaseHelper databaseHelper;
    private List<Device> deviceList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_device_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.device_recycler_view);
        emptyView = view.findViewById(R.id.empty_view);
        addDeviceBtn = view.findViewById(R.id.add_device_btn);

        // 添加状态栏高度的顶部内边距，避免标题栏与状态栏重叠
        View titleBar = view.findViewById(R.id.title_bar);
        if (titleBar != null) {
            int statusBarHeight = getStatusBarHeight();
            titleBar.setPadding(titleBar.getPaddingLeft(),
                titleBar.getPaddingTop() + statusBarHeight,
                titleBar.getPaddingRight(),
                titleBar.getPaddingBottom());
        }

        databaseHelper = new DatabaseHelper(requireContext());
        adapter = new DeviceListAdapter(deviceList, this::onDeviceClick, this::onEditDevice);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        addDeviceBtn.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openAddDevice();
            }
        });

        loadDevices();

        // 首次创建时应用深色模式
        boolean isDarkMode = requireContext().getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
            .getBoolean("dark_mode", false);
        applyTheme(isDarkMode);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadDevices();
    }

    private void loadDevices() {
        if (databaseHelper == null) return;
        deviceList.clear();
        
        // 检查用户是否已登录
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);
        String token = prefs.getString("auth_token", null);
        String boundDevicesJson = prefs.getString("bound_devices", null);
        
        if (token != null && !token.isEmpty() && boundDevicesJson != null && !boundDevicesJson.isEmpty()) {
            // 已登录：只显示绑定设备列表中的设备
            try {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                com.google.gson.reflect.TypeToken<java.util.List<DeviceApiService.BoundDevice>> tokenType = 
                    new com.google.gson.reflect.TypeToken<java.util.List<DeviceApiService.BoundDevice>>() {};
                java.util.List<DeviceApiService.BoundDevice> boundDevices = gson.fromJson(boundDevicesJson, tokenType.getType());
                
                if (boundDevices != null && !boundDevices.isEmpty()) {
                    // 获取所有本地设备
                    java.util.List<Device> allLocalDevices = databaseHelper.getAllDevices();
                    
                    // 只保留绑定设备列表中的设备
                    for (DeviceApiService.BoundDevice boundDevice : boundDevices) {
                        String deviceNum = boundDevice.getDeviceNum();
                        for (Device localDevice : allLocalDevices) {
                            if (deviceNum.equals(localDevice.getDeviceNum()) || deviceNum.equals(localDevice.getDeviceId())) {
                                // 显示名以本地数据库为准（编辑后会更新）；bound_devices 仅用于过滤绑定设备
                                deviceList.add(localDevice);
                                break;
                            }
                        }
                    }
                    android.util.Log.d(TAG, "Loaded " + deviceList.size() + " bound devices from local database");
                } else {
                    android.util.Log.d(TAG, "No bound devices found, showing empty list");
                }
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error parsing bound devices: " + e.getMessage(), e);
                // 解析失败，显示空列表
            }
        } else {
            // 未登录：不显示任何设备（设备跟账号绑定）
            android.util.Log.d(TAG, "User not logged in, showing empty list");
        }
        
        // 获取当前选中设备ID，用于高亮显示
        String selectedDeviceId = prefs.getString("selected_device_id", null);
        adapter.setSelectedDeviceId(selectedDeviceId);
        
        adapter.notifyDataSetChanged();

        if (deviceList.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    private void onDeviceClick(Device device) {
        if (getActivity() instanceof MainActivity) {
            // 先更新 adapter 的 selectedDeviceId，确保高亮立即更新
            String deviceId = device.getDeviceId();
            adapter.setSelectedDeviceId(deviceId);
            adapter.notifyDataSetChanged();
            
            ((MainActivity) getActivity()).selectDevice(device);
            ((MainActivity) getActivity()).switchToTab(0);
        }
    }

    private void onEditDevice(Device device) {
        showEditDialog(device);
    }

    private void showEditDialog(Device device) {
        final boolean darkMode = ThemedDialogHelper.isDarkModeEnabled(requireContext());
        int labelColor = getResources().getColor(darkMode ? R.color.dark_text_secondary : R.color.text_secondary, null);
        int valueColor = getResources().getColor(darkMode ? R.color.dark_onSurface : R.color.onSurface, null);

        androidx.appcompat.app.AlertDialog.Builder builder = ThemedDialogHelper.createBuilder(requireContext());
        builder.setTitle(R.string.edit_device);

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        TextView nickNameLabel = new TextView(requireContext());
        nickNameLabel.setText(R.string.device_nickname);
        nickNameLabel.setTextSize(12);
        nickNameLabel.setTextColor(labelColor);
        layout.addView(nickNameLabel);

        final EditText nicknameInput = new EditText(requireContext());
        nicknameInput.setHint(R.string.enter_device_nickname);
        nicknameInput.setText(device.getName());
        nicknameInput.setSingleLine(true);
        if (darkMode) {
            nicknameInput.setTextColor(valueColor);
            nicknameInput.setHintTextColor(labelColor);
        }
        layout.addView(nicknameInput);

        TextView tagLabel = new TextView(requireContext());
        tagLabel.setText(R.string.device_tag);
        tagLabel.setTextSize(12);
        tagLabel.setTextColor(labelColor);
        tagLabel.setPadding(0, 16, 0, 0);
        layout.addView(tagLabel);

        final Spinner tagSpinner = new Spinner(requireContext());
        TagPickerHelper.setupTagSpinner(requireContext(), tagSpinner, device.getTag());
        layout.addView(tagSpinner);

        builder.setView(layout);

        builder.setPositiveButton(R.string.save, (dialog, which) -> {
            String nickname = nicknameInput.getText().toString().trim();
            java.util.List<String> tagList = TagPickerHelper.buildTagList(requireContext());
            String tag = TagPickerHelper.getTagFromPosition(tagList, tagSpinner.getSelectedItemPosition());

            if (nickname.isEmpty()) {
                Toast.makeText(requireContext(), R.string.nickname_empty, Toast.LENGTH_SHORT).show();
                return;
            }

            final String deviceId = device.getDeviceId();
            final String deviceNum = device.getDeviceNum();
            final String customerCode = device.getCustomerCode();

            DeviceRepository.getInstance(requireContext()).updateDeviceNameAndTag(
                    deviceId, deviceNum, nickname, tag, customerCode,
                    new DeviceRepository.UpdateCallback() {
                        @Override
                        public void onSuccess() {
                            BoundDevicesHelper.updateNickName(requireContext(), deviceNum, nickname);
                            if (getActivity() instanceof MainActivity) {
                                ((MainActivity) getActivity()).onDeviceNicknameUpdated(deviceId, nickname, tag);
                            }
                            loadDevices();
                            Toast.makeText(requireContext(), R.string.save_success, Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                        }
                    });
        });

        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
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
        
        rootView.setBackgroundColor(bgColor);
        
        View titleBar = rootView.findViewById(R.id.title_bar);
        if (titleBar != null) titleBar.setBackgroundColor(topBarColor);
        
        // 更新标题文字颜色
        if (titleBar instanceof ViewGroup) {
            ViewGroup titleGroup = (ViewGroup) titleBar;
            for (int i = 0; i < titleGroup.getChildCount(); i++) {
                View child = titleGroup.getChildAt(i);
                if (child instanceof TextView) {
                    ((TextView) child).setTextColor(onSurfaceColor);
                }
            }
        }
        
        // 更新空视图文字颜色
        if (emptyView != null) emptyView.setTextColor(textSecColor);
        
        // 更新RecyclerView中的item颜色
        if (recyclerView != null) {
            for (int i = 0; i < recyclerView.getChildCount(); i++) {
                View item = recyclerView.getChildAt(i);
                applyItemTheme(item, isDarkMode);
            }
            if (adapter != null) adapter.notifyDataSetChanged();
        }
    }
    
    private void applyItemTheme(View itemView, boolean isDarkMode) {
        int onSurfaceColor = getResources().getColor(isDarkMode ? R.color.dark_onSurface : R.color.onSurface, null);
        int textSecColor = getResources().getColor(isDarkMode ? R.color.dark_text_secondary : R.color.text_secondary, null);
        
        TextView nameText = itemView.findViewById(R.id.device_name);
        if (nameText != null) nameText.setTextColor(onSurfaceColor);
        
        TextView tagText = itemView.findViewById(R.id.device_tag);
        if (tagText != null) tagText.setTextColor(textSecColor);
        
        TextView numText = itemView.findViewById(R.id.device_num);
        if (numText != null) numText.setTextColor(textSecColor);
        
        TextView macText = itemView.findViewById(R.id.device_mac);
        if (macText != null) macText.setTextColor(textSecColor);
    }

    private static class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.ViewHolder> {
        private final List<Device> devices;
        private final OnDeviceClickListener clickListener;
        private final OnEditClickListener editListener;
        private String selectedDeviceId = null; // 当前选中设备的ID

        interface OnDeviceClickListener {
            void onDeviceClick(Device device);
        }

        interface OnEditClickListener {
            void onEditDevice(Device device);
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText;
            TextView tagText;
            TextView numText;
            TextView macText;
            ImageButton editBtn;
            View itemViewContainer; // 整个item容器

            ViewHolder(View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.device_name);
                tagText = itemView.findViewById(R.id.device_tag);
                numText = itemView.findViewById(R.id.device_num);
                macText = itemView.findViewById(R.id.device_mac);
                editBtn = itemView.findViewById(R.id.edit_btn);
                itemViewContainer = itemView;
            }
        }

        DeviceListAdapter(List<Device> devices, OnDeviceClickListener clickListener, OnEditClickListener editListener) {
            this.devices = devices;
            this.clickListener = clickListener;
            this.editListener = editListener;
        }

        /**
         * 设置当前选中设备的ID，用于高亮显示
         */
        void setSelectedDeviceId(String deviceId) {
            this.selectedDeviceId = deviceId;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_device, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Device device = devices.get(position);

            // 昵称：显示设备名称
            String displayName = device.getName();
            if (displayName == null || displayName.isEmpty()) {
                holder.nameText.setText(R.string.unknown_device);
            } else {
                holder.nameText.setText(displayName);
            }

            String tag = device.getTag();
            if (tag != null && !tag.isEmpty()) {
                String emoji = com.RockiotTag.tag.model.DeviceTag.getEmoji(tag);
                holder.tagText.setText(emoji.isEmpty() ? tag : emoji + " " + tag);
                holder.tagText.setVisibility(View.VISIBLE);
            } else {
                holder.tagText.setVisibility(View.GONE);
            }

            String deviceNum = device.getDeviceNum();
            if (deviceNum != null && !deviceNum.isEmpty()) {
                holder.numText.setText("ID: " + deviceNum);
                holder.numText.setVisibility(View.VISIBLE);
            } else {
                holder.numText.setVisibility(View.GONE);
            }

            String mac = device.getMac();
            if (mac != null && !mac.isEmpty()) {
                holder.macText.setText("MAC: " + mac);
                holder.macText.setVisibility(View.VISIBLE);
            } else {
                holder.macText.setVisibility(View.GONE);
            }

            // 根据当前深色模式设置颜色
            boolean isDarkMode = holder.itemView.getContext()
                .getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
                .getBoolean("dark_mode", false);
            int onSurfaceColor = holder.itemView.getContext().getResources().getColor(
                isDarkMode ? R.color.dark_onSurface : R.color.onSurface, null);
            int textSecColor = holder.itemView.getContext().getResources().getColor(
                isDarkMode ? R.color.dark_text_secondary : R.color.text_secondary, null);
            
            holder.nameText.setTextColor(onSurfaceColor);
            holder.tagText.setTextColor(textSecColor);
            holder.numText.setTextColor(textSecColor);
            holder.macText.setTextColor(textSecColor);

            // 检查是否是当前选中设备，设置高亮样式
            // 匹配逻辑：selectedDeviceId 可能是 deviceId、deviceNum 或 MAC 地址
            boolean isSelected = selectedDeviceId != null && 
                (selectedDeviceId.equals(device.getDeviceId()) || 
                 selectedDeviceId.equals(device.getDeviceNum()) ||
                 selectedDeviceId.equals(device.getMac()));
            
            if (isSelected) {
                // 选中状态：背景高亮，文字保持常规颜色并加粗，避免与地图紫色混淆
                int selectedBgColor = holder.itemView.getContext().getResources().getColor(
                    isDarkMode ? R.color.dark_card_background_selected : R.color.card_background_selected, null);
                holder.itemViewContainer.setBackgroundColor(selectedBgColor);
                holder.nameText.setTextColor(onSurfaceColor);
                holder.nameText.setTypeface(holder.nameText.getTypeface(), Typeface.BOLD);
            } else {
                // 未选中状态：恢复默认背景
                int normalBgColor = holder.itemView.getContext().getResources().getColor(
                    isDarkMode ? R.color.dark_card_background : R.color.card_background, null);
                holder.itemViewContainer.setBackgroundColor(normalBgColor);
                holder.nameText.setTypeface(holder.nameText.getTypeface(), Typeface.NORMAL);
            }

            holder.itemView.setOnClickListener(v -> clickListener.onDeviceClick(device));
            holder.editBtn.setOnClickListener(v -> editListener.onEditDevice(device));
        }

        @Override
        public int getItemCount() {
            return devices.size();
        }
    }
}
