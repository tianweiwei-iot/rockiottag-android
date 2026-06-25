package com.RockiotTag.tag;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 萌宠头像选择适配器（4 列网格，全部展示不滚动）
 */
public class AvatarAdapter extends RecyclerView.Adapter<AvatarAdapter.ViewHolder> {

    public interface OnAvatarClickListener {
        void onAvatarClick(int position);
    }

    private final int[] avatarResources;
    private int selectedIndex;
    private OnAvatarClickListener clickListener;

    public AvatarAdapter(int[] avatarResources, int selectedIndex) {
        this.avatarResources = avatarResources;
        this.selectedIndex = selectedIndex;
    }

    public void setSelectedIndex(int index) {
        selectedIndex = index;
        notifyDataSetChanged();
    }

    public void setOnAvatarClickListener(OnAvatarClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_avatar, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.avatarImage.setImageResource(avatarResources[position]);
        if (position == selectedIndex) {
            holder.avatarImage.setBackgroundResource(R.drawable.avatar_selected_border);
        } else {
            holder.avatarImage.setBackgroundResource(R.drawable.avatar_item_bg);
        }
        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && clickListener != null) {
                clickListener.onAvatarClick(pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return avatarResources.length;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView avatarImage;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImage = itemView.findViewById(R.id.avatar_image);
        }
    }
}
