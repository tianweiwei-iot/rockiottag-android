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
                holder.numText.setText("SN: " + deviceNum);
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

            holder.itemView.setOnClickListener(v -> clickListener.onDeviceClick(device));
            holder.editBtn.setOnClickListener(v -> editListener.onEditDevice(device));
        }

        @Override
        public int getItemCount() {
            return devices.size();
        }
    }
}
