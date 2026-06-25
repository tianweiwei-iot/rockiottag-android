package com.RockiotTag.tag.helper;

import android.view.View;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.RockiotTag.tag.R;
import com.RockiotTag.tag.util.MapFloatingButtonHelper;

/**
 * 轨迹页底部信息栏（精度调节 + 时间位置）折叠/展开。
 */
public class TrackInfoPanelHelper {

    private static final long ANIM_DURATION_MS = 220L;

    private final AppCompatActivity activity;
    private final CardView panel;
    private final ImageButton indicator;

    private boolean expanded = true;
    private boolean animating = false;

    public TrackInfoPanelHelper(AppCompatActivity activity) {
        this.activity = activity;
        this.panel = activity.findViewById(R.id.track_info_card);
        this.indicator = activity.findViewById(R.id.track_info_panel_indicator);
    }

    public void setup() {
        if (panel == null || indicator == null) {
            return;
        }
        indicator.setOnClickListener(v -> expand());
        panel.post(this::resetExpandedState);
    }

    /** 点击地图空白区域时切换显示状态 */
    public void onMapAreaTap() {
        if (animating || panel == null) {
            return;
        }
        if (expanded) {
            collapse();
        } else {
            expand();
        }
    }

    public void applyTheme(boolean isDarkMode) {
        MapFloatingButtonHelper.applyTheme(activity, isDarkMode, indicator);
    }

    public boolean isExpanded() {
        return expanded;
    }

    private void resetExpandedState() {
        panel.setVisibility(View.VISIBLE);
        panel.setAlpha(1f);
        panel.setTranslationY(0f);
        indicator.setVisibility(View.GONE);
        expanded = true;
        animating = false;
    }

    private void collapse() {
        if (!expanded || animating) {
            return;
        }
        int distance = panel.getHeight() + dpToPx(12);
        if (distance <= 0) {
            panel.setVisibility(View.GONE);
            indicator.setVisibility(View.VISIBLE);
            expanded = false;
            return;
        }
        animating = true;
        panel.animate()
                .translationY(distance)
                .alpha(0f)
                .setDuration(ANIM_DURATION_MS)
                .withEndAction(() -> {
                    panel.setVisibility(View.GONE);
                    indicator.setVisibility(View.VISIBLE);
                    expanded = false;
                    animating = false;
                })
                .start();
    }

    private void expand() {
        if (expanded || animating) {
            return;
        }
        int distance = panel.getHeight() > 0 ? panel.getHeight() + dpToPx(12) : dpToPx(180);
        animating = true;
        indicator.setVisibility(View.GONE);
        panel.setVisibility(View.VISIBLE);
        panel.setAlpha(0f);
        panel.setTranslationY(distance);
        panel.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(ANIM_DURATION_MS)
                .withEndAction(() -> {
                    expanded = true;
                    animating = false;
                })
                .start();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * activity.getResources().getDisplayMetrics().density);
    }
}
