package com.RockiotTag.tag;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.List;

import com.RockiotTag.tag.model.DeviceTag;

public class BoundDeviceAdapter extends ArrayAdapter<Device> {

    private OnDeviceEditListener editListener;
    private OnCheckedChangeListener checkedChangeListener;
    private boolean isMultiSelectMode = false;
    private boolean isDarkMode = false;
    private java.util.Set<Integer> selectedPositions = new java.util.HashSet<>();

    public interface OnDeviceEditListener {
        void onEditDevice(Device device, int position);
    }

    public interface OnCheckedChangeListener {
        void onCheckedChanged(int position, boolean isChecked);
    }

    public void setOnDeviceEditListener(OnDeviceEditListener listener) {
        this.editListener = listener;
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.checkedChangeListener = listener;
    }

    public BoundDeviceAdapter(Context context, List<Device> devices) {
        super(context, 0, devices);
    }

    public void setDarkMode(boolean darkMode) {
        this.isDarkMode = darkMode;
    }

    public void setMultiSelectMode(boolean mode) {
        this.isMultiSelectMode = mode;
        if (!mode) {
            selectedPositions.clear();
        }
        notifyDataSetChanged();
    }

    public void setSelection(int position, boolean selected) {
        if (selected) {
            selectedPositions.add(position);
        } else {
            selectedPositions.remove(position);
        }
        notifyDataSetChanged();
    }

    public void selectAll() {
        selectedPositions.clear();
        for (int i = 0; i < getCount(); i++) {
            selectedPositions.add(i);
        }
        notifyDataSetChanged();
    }

    public void clearSelections() {
        selectedPositions.clear();
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_bound_device, parent, false);
            holder = new ViewHolder();
            holder.deviceNameText = convertView.findViewById(R.id.device_name);
            holder.deviceAddressText = convertView.findViewById(R.id.device_address);
            holder.deviceTagText = convertView.findViewById(R.id.device_tag);
            holder.editBtn = convertView.findViewById(R.id.edit_btn);
            holder.checkBox = convertView.findViewById(R.id.device_checkbox);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Device device = (Device) getItem(position);
        if (device != null) {
            String name = device.getName();
            holder.deviceNameText.setText(name != null && !name.isEmpty() ? name : getContext().getString(R.string.unknown_device));
            
            // 在设备号后面显示 MAC 地址
            String address = device.getAddress();
            String mac = device.getMac();
            
            // 【调试】打印MAC地址信息
            android.util.Log.d("BoundDeviceAdapter", "Displaying device: name=" + device.getName() + ", address=" + address + ", mac=" + mac);
            
            if (mac != null && !mac.isEmpty()) {
                holder.deviceAddressText.setText(address + " (" + mac + ")");
            } else {
                holder.deviceAddressText.setText(address);
            }

            // 设置设备标签图标（使用 DeviceTag 枚举）
            String tag = device.getTag();
            if (tag != null && !tag.isEmpty()) {
                holder.deviceTagText.setVisibility(View.VISIBLE);
                String icon = DeviceTag.getEmoji(tag);
                holder.deviceTagText.setText(icon + " " + tag);
            } else {
                holder.deviceTagText.setVisibility(View.GONE);
            }

            int onSurfaceColor = getContext().getResources().getColor(
                    isDarkMode ? R.color.dark_onSurface : R.color.onSurface, null);
            int textSecColor = getContext().getResources().getColor(
                    isDarkMode ? R.color.dark_text_secondary : R.color.text_secondary, null);
            int itemBgColor = getContext().getResources().getColor(
                    isDarkMode ? R.color.dark_card_background : R.color.white, null);

            holder.deviceNameText.setTextColor(onSurfaceColor);
            holder.deviceAddressText.setTextColor(textSecColor);
            holder.deviceTagText.setTextColor(textSecColor);
            convertView.setBackgroundColor(itemBgColor);
            
            if (isMultiSelectMode) {
                holder.checkBox.setVisibility(View.VISIBLE);
                holder.editBtn.setVisibility(View.GONE);
                holder.checkBox.setChecked(selectedPositions.contains(position));
            } else {
                holder.checkBox.setVisibility(View.GONE);
                holder.editBtn.setVisibility(View.VISIBLE);
            }
            
            final int pos = position;
            final Device finalDevice = device;
            
            holder.editBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (editListener != null) {
                        editListener.onEditDevice(finalDevice, pos);
                    }
                }
            });
            
            holder.checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean isChecked = holder.checkBox.isChecked();
                    if (isChecked) {
                        selectedPositions.add(pos);
                    } else {
                        selectedPositions.remove(pos);
                    }
                    if (checkedChangeListener != null) {
                        checkedChangeListener.onCheckedChanged(pos, isChecked);
                    }
                }
            });
        }

        return convertView;
    }
    
    private static class ViewHolder {
        TextView deviceNameText;
        TextView deviceAddressText;
        TextView deviceTagText;
        ImageButton editBtn;
        CheckBox checkBox;
    }
}
