package com.RockiotTag.tag.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.RockiotTag.tag.R;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.model.DeviceTag;

/**
 * 设备列表适配器 - 使用RecyclerView和DiffUtil优化性能
 * 
 * 优势：
 * 1. DiffUtil自动计算差异，只更新变化的项
 * 2. 支持动画效果
 * 3. 更高的滚动性能
 * 4. ViewHolder复用机制
 */
public class DeviceListAdapter extends ListAdapter<TagDevice, DeviceListAdapter.DeviceViewHolder> {
    
    private static final String TAG = "DeviceListAdapter";
    
    private OnDeviceClickListener clickListener;
    private OnDeviceEditListener editListener;
    
    public interface OnDeviceClickListener {
        void onDeviceClick(TagDevice device, int position);
    }
    
    public interface OnDeviceEditListener {
        void onEditDevice(TagDevice device, int position);
    }
    
    public DeviceListAdapter(Context context) {
        super(new DeviceDiffCallback());
    }
    
    public void setOnDeviceClickListener(OnDeviceClickListener listener) {
        this.clickListener = listener;
    }
    
    public void setOnDeviceEditListener(OnDeviceEditListener listener) {
        this.editListener = listener;
    }
    
    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_bound_device, parent, false);
        return new DeviceViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        TagDevice device = getItem(position);
        if (device != null) {
            holder.bind(device, position);
        }
    }
    
    class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView deviceNameText;
        TextView deviceAddressText;
        TextView deviceTagText;
        ImageButton editBtn;
        
        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceNameText = itemView.findViewById(R.id.device_name);
            deviceAddressText = itemView.findViewById(R.id.device_address);
            deviceTagText = itemView.findViewById(R.id.device_tag);
            editBtn = itemView.findViewById(R.id.edit_btn);
        }
        
        public void bind(final TagDevice device, final int position) {
            // 设置设备名称
            String name = device.getName();
            deviceNameText.setText(name != null && !name.isEmpty() ? 
                name : itemView.getContext().getString(R.string.unknown_device));
            
            // 设置设备地址/编号（带MAC地址）
            String deviceNum = device.getAddress();
            String mac = device.getMac();
            if (mac != null && !mac.isEmpty()) {
                deviceAddressText.setText(deviceNum + " (" + mac + ")");
            } else {
                deviceAddressText.setText(deviceNum);
            }
            
            // 设置设备标签图标（使用 DeviceTag 枚举）
            String tag = device.getTag();
            if (tag != null && !tag.isEmpty()) {
                deviceTagText.setVisibility(View.VISIBLE);
                String icon = DeviceTag.getEmoji(tag);
                deviceTagText.setText(icon + " " + tag);
            } else {
                deviceTagText.setVisibility(View.GONE);
            }
            
            // 编辑按钮点击事件
            editBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (editListener != null) {
                        editListener.onEditDevice(device, position);
                    }
                }
            });
            
            // 整个item点击事件
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (clickListener != null) {
                        clickListener.onDeviceClick(device, position);
                    }
                }
            });
        }
    }
    
    /**
     * DiffUtil回调 - 用于计算列表差异
     */
    private static class DeviceDiffCallback extends DiffUtil.ItemCallback<TagDevice> {
        
        @Override
        public boolean areItemsTheSame(@NonNull TagDevice oldItem, @NonNull TagDevice newItem) {
            // 判断是否是同一个设备（根据唯一ID）
            return oldItem.getDeviceId().equals(newItem.getDeviceId());
        }
        
        @Override
        public boolean areContentsTheSame(@NonNull TagDevice oldItem, @NonNull TagDevice newItem) {
            // 判断内容是否相同
            return oldItem.equals(newItem);
        }
        
        @Override
        public Object getChangePayload(@NonNull TagDevice oldItem, @NonNull TagDevice newItem) {
            // 可选：返回具体的变化字段，用于更精细的动画
            return super.getChangePayload(oldItem, newItem);
        }
    }
}
