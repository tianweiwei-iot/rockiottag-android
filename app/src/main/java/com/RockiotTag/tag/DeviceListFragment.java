package com.RockiotTag.tag;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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
        adapter = new DeviceListAdapter(deviceList, this::onDeviceClick);
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
            // 切换到首页显示选中设备
            ((MainActivity) getActivity()).switchToTab(0);
        }
    }

    /**
     * 简单的设备列表适配器
     */
    private static class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.ViewHolder> {
        private final List<Device> devices;
        private final OnDeviceClickListener listener;

        interface OnDeviceClickListener {
            void onDeviceClick(Device device);
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText;
            TextView addressText;

            ViewHolder(View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.device_name);
                addressText = itemView.findViewById(R.id.device_address);
            }
        }

        DeviceListAdapter(List<Device> devices, OnDeviceClickListener listener) {
            this.devices = devices;
            this.listener = listener;
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
            if (device.getLatitude() != 0 && device.getLongitude() != 0) {
                holder.addressText.setText(device.getAddress());
            } else {
                holder.addressText.setText(R.string.position_not_reported);
            }
            holder.itemView.setOnClickListener(v -> listener.onDeviceClick(device));
        }

        @Override
        public int getItemCount() {
            return devices.size();
        }
    }
}
