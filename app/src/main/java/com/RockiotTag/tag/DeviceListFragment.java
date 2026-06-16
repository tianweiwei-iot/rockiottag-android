package com.RockiotTag.tag;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
        deviceList.addAll(databaseHelper.getAllDevices());
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
            ((MainActivity) getActivity()).selectDevice(device);
            ((MainActivity) getActivity()).switchToTab(0);
        }
    }

    private void onEditDevice(Device device) {
        showEditDialog(device);
    }

    private void showEditDialog(Device device) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(R.string.edit_device);

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText nicknameInput = new EditText(requireContext());
        nicknameInput.setHint(R.string.nickname);
        nicknameInput.setText(device.getName());
        layout.addView(nicknameInput);

        final EditText tagInput = new EditText(requireContext());
        tagInput.setHint(R.string.tag);
        tagInput.setText(device.getTag() != null ? device.getTag() : "");
        layout.addView(tagInput);

        builder.setView(layout);

        builder.setPositiveButton(R.string.save, (dialog, which) -> {
            String nickname = nicknameInput.getText().toString().trim();
            String tag = tagInput.getText().toString().trim();

            if (nickname.isEmpty()) {
                Toast.makeText(requireContext(), R.string.nickname_empty, Toast.LENGTH_SHORT).show();
                return;
            }

            device.setName(nickname);
            device.setTag(tag);

            if (databaseHelper != null) {
                databaseHelper.updateDeviceNameAndTag(device.getDeviceId(), device.getDeviceNum(), nickname, tag);
            }

            // 同步到服务器
            new Thread(() -> {
                try {
                    NewApiService apiService = NewApiService.getInstance();
                    String customerCode = device.getCustomerCode();
                    String apiKey = ApiConfig.getApiKeyForCustomer(customerCode);
                    if (apiKey != null) {
                        apiService.updateDevice(device.getDeviceNum(), nickname, customerCode);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            loadDevices();
            Toast.makeText(requireContext(), R.string.save_success, Toast.LENGTH_SHORT).show();
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

            ViewHolder(View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.device_name);
                tagText = itemView.findViewById(R.id.device_tag);
                numText = itemView.findViewById(R.id.device_num);
                macText = itemView.findViewById(R.id.device_mac);
                editBtn = itemView.findViewById(R.id.edit_btn);
            }
        }

        DeviceListAdapter(List<Device> devices, OnDeviceClickListener clickListener, OnEditClickListener editListener) {
            this.devices = devices;
            this.clickListener = clickListener;
            this.editListener = editListener;
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

            holder.nameText.setText(device.getName());

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

            holder.itemView.setOnClickListener(v -> clickListener.onDeviceClick(device));
            holder.editBtn.setOnClickListener(v -> editListener.onEditDevice(device));
        }

        @Override
        public int getItemCount() {
            return devices.size();
        }
    }
}
