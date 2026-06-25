package com.RockiotTag.tag;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.RockiotTag.tag.model.TagDevice;

import java.util.ArrayList;
import java.util.List;

public class DeviceAdapter extends ArrayAdapter<TagDevice> {

    private List<TagDevice> deviceList;
    private OnDeviceClickListener listener;

    public interface OnDeviceClickListener {
        void onDeviceClick(TagDevice device);
    }

    public DeviceAdapter(Context context, List<TagDevice> devices) {
        super(context, 0, devices);
        this.deviceList = new ArrayList<>(devices);
    }

    public void setOnDeviceClickListener(OnDeviceClickListener listener) {
        this.listener = listener;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_device, parent, false);
        }

        TagDevice device = getItem(position);
        if (device != null) {
            TextView deviceNameText = convertView.findViewById(R.id.device_name);
            TextView deviceAddressText = convertView.findViewById(R.id.device_address);

            String name = device.getName();
            deviceNameText.setText(name != null && !name.isEmpty() ? name : "未知设备");
            deviceAddressText.setText(device.getAddress());

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onDeviceClick(device);
                    }
                }
            });
        }

        return convertView;
    }

    public void updateDeviceList(List<TagDevice> newDevices) {
        clear();
        addAll(newDevices);
        notifyDataSetChanged();
    }

    public void addDevice(TagDevice device) {
        String deviceName = device.getName();
        if (deviceName == null || deviceName.isEmpty() || !deviceName.contains("D_Card")) {
            return;
        }
        
        boolean exists = false;
        for (int i = 0; i < getCount(); i++) {
            TagDevice d = getItem(i);
            if (d != null && d.getDeviceId().equals(device.getDeviceId())) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            add(device);
            notifyDataSetChanged();
        }
    }
}
