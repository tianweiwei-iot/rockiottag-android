package com.RockiotTag.tag.helper;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.MainActivity;
import com.RockiotTag.tag.R;
import com.RockiotTag.tag.util.LogUtil;

/**
 * TrackActivity 底部导航栏逻辑。
 */
public class TrackBottomNavHelper {

    private final AppCompatActivity activity;
    private final LinearLayout tabHome;
    private final LinearLayout tabList;
    private final LinearLayout tabTrack;
    private final LinearLayout tabProfile;
    private final ImageView tabHomeIcon;
    private final ImageView tabListIcon;
    private final ImageView tabTrackIcon;
    private final ImageView tabProfileIcon;

    public TrackBottomNavHelper(
            AppCompatActivity activity,
            LinearLayout tabHome, LinearLayout tabList, LinearLayout tabTrack, LinearLayout tabProfile,
            ImageView tabHomeIcon, ImageView tabListIcon, ImageView tabTrackIcon, ImageView tabProfileIcon) {
        this.activity = activity;
        this.tabHome = tabHome;
        this.tabList = tabList;
        this.tabTrack = tabTrack;
        this.tabProfile = tabProfile;
        this.tabHomeIcon = tabHomeIcon;
        this.tabListIcon = tabListIcon;
        this.tabTrackIcon = tabTrackIcon;
        this.tabProfileIcon = tabProfileIcon;
    }

    public void initBottomNavigation() {
        updateTabSelection(2);

        View.OnClickListener tabClickListener = v -> {
            int tabIndex;
            if (v.getId() == R.id.tab_home) tabIndex = 0;
            else if (v.getId() == R.id.tab_list) tabIndex = 1;
            else if (v.getId() == R.id.tab_track) tabIndex = 2;
            else if (v.getId() == R.id.tab_profile) tabIndex = 3;
            else return;

            if (tabIndex == 2) return;

            MainActivity.pendingTabSwitch = tabIndex;
            activity.finish();
        };

        tabHome.setOnClickListener(tabClickListener);
        tabList.setOnClickListener(tabClickListener);
        tabTrack.setOnClickListener(tabClickListener);
        tabProfile.setOnClickListener(tabClickListener);
    }

    public void updateTabSelection(int tabIndex) {
        tabHome.setSelected(tabIndex == 0);
        tabList.setSelected(tabIndex == 1);
        tabTrack.setSelected(tabIndex == 2);
        tabProfile.setSelected(tabIndex == 3);

        int selectedColor = activity.getResources().getColor(R.color.brand_primary, null);
        boolean isDarkMode = activity.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
                .getBoolean("dark_mode", false);
        int unselectedColor = isDarkMode
                ? activity.getResources().getColor(R.color.dark_text_secondary, null)
                : activity.getResources().getColor(R.color.text_secondary, null);

        tabHomeIcon.setColorFilter(tabIndex == 0 ? selectedColor : unselectedColor);
        tabListIcon.setColorFilter(tabIndex == 1 ? selectedColor : unselectedColor);
        tabTrackIcon.setColorFilter(tabIndex == 2 ? selectedColor : unselectedColor);
        tabProfileIcon.setColorFilter(tabIndex == 3 ? selectedColor : unselectedColor);
    }
}
