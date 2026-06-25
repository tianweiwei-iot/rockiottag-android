package com.RockiotTag.tag.helper;

import com.RockiotTag.tag.util.ToastHelper;

import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.RockiotTag.tag.DeviceListFragment;
import com.RockiotTag.tag.HomeFragment;
import com.RockiotTag.tag.MainActivity;
import com.RockiotTag.tag.ProfileFragment;
import com.RockiotTag.tag.MainActivity;
import com.RockiotTag.tag.R;
import com.RockiotTag.tag.TrackActivity;
import com.RockiotTag.tag.TrackFragment;
import com.RockiotTag.tag.model.TagDevice;
import com.RockiotTag.tag.util.LanguageIndicatorHelper;
import com.RockiotTag.tag.util.LogUtil;

/**
 * MainActivity 底部导航与 Fragment 切换逻辑。
 */
public class MainTabHelper {

    private static final String TAG = "MainTabHelper";
    private static final long TAB_CLICK_INTERVAL = 500L;

    public interface Host {
        AppCompatActivity getActivity();
        TagDevice getSelectedDevice();
        int getCurrentTab();
        void setCurrentTab(int tab);
        View getRefreshBtn();
        View getMapTypeBtn();
        View getLocateBtn();
        View getCustomCompass();
        View getBottomInfo();
        void onHomeFragmentVisibleInternal();
    }

    private final Host host;
    private final LinearLayout tabHome;
    private final LinearLayout tabList;
    private final LinearLayout tabTrack;
    private final LinearLayout tabProfile;
    private final ImageView tabHomeIcon;
    private final ImageView tabListIcon;
    private final ImageView tabTrackIcon;
    private final ImageView tabProfileIcon;
    private final TextView tabHomeText;
    private final TextView tabListText;
    private final TextView tabTrackText;
    private final TextView tabProfileText;

    private HomeFragment homeFragment;
    private DeviceListFragment deviceListFragment;
    private TrackFragment trackFragment;
    private ProfileFragment profileFragment;

    private long lastTabClickTime;

    public MainTabHelper(
            Host host,
            LinearLayout tabHome, LinearLayout tabList, LinearLayout tabTrack, LinearLayout tabProfile,
            ImageView tabHomeIcon, ImageView tabListIcon, ImageView tabTrackIcon, ImageView tabProfileIcon,
            TextView tabHomeText, TextView tabListText, TextView tabTrackText, TextView tabProfileText) {
        this.host = host;
        this.tabHome = tabHome;
        this.tabList = tabList;
        this.tabTrack = tabTrack;
        this.tabProfile = tabProfile;
        this.tabHomeIcon = tabHomeIcon;
        this.tabListIcon = tabListIcon;
        this.tabTrackIcon = tabTrackIcon;
        this.tabProfileIcon = tabProfileIcon;
        this.tabHomeText = tabHomeText;
        this.tabListText = tabListText;
        this.tabTrackText = tabTrackText;
        this.tabProfileText = tabProfileText;
    }

    public HomeFragment getHomeFragment() { return homeFragment; }
    public DeviceListFragment getDeviceListFragment() { return deviceListFragment; }
    public TrackFragment getTrackFragment() { return trackFragment; }
    public ProfileFragment getProfileFragment() { return profileFragment; }

    public void initBottomNavigation() {
        AppCompatActivity activity = host.getActivity();
        View.OnClickListener tabClickListener = v -> {
            long now = System.currentTimeMillis();
            if (now - lastTabClickTime < TAB_CLICK_INTERVAL) {
                return;
            }
            lastTabClickTime = now;

            int tabIndex;
            if (v.getId() == R.id.tab_home) tabIndex = 0;
            else if (v.getId() == R.id.tab_list) tabIndex = 1;
            else if (v.getId() == R.id.tab_track) tabIndex = 2;
            else if (v.getId() == R.id.tab_profile) tabIndex = 3;
            else return;

            if (tabIndex == host.getCurrentTab()) return;

            if (tabIndex == 2) {
                if (host.getSelectedDevice() == null) {
                    ToastHelper.show(activity, R.string.please_select_device);
                    return;
                }
                updateTabSelection(2);
                host.setCurrentTab(2);
                updateHomeUIVisibility(false);
                activity.startActivity(new Intent(activity, TrackActivity.class));
                return;
            }

            switchToTab(tabIndex);
        };

        tabHome.setOnClickListener(tabClickListener);
        tabList.setOnClickListener(tabClickListener);
        tabTrack.setOnClickListener(tabClickListener);
        tabProfile.setOnClickListener(tabClickListener);
        updateTabSelection(host.getCurrentTab());
    }

    public void initFragments() {
        LogUtil.d(TAG, "=== initFragments START ===");
        AppCompatActivity activity = host.getActivity();
        FragmentManager fm = activity.getSupportFragmentManager();

        homeFragment = (HomeFragment) fm.findFragmentByTag("home");
        deviceListFragment = (DeviceListFragment) fm.findFragmentByTag("list");
        trackFragment = (TrackFragment) fm.findFragmentByTag("track");
        profileFragment = (ProfileFragment) fm.findFragmentByTag("profile");

        boolean isFirstCreate = homeFragment == null;
        FragmentTransaction ft = fm.beginTransaction();

        if (homeFragment == null) {
            homeFragment = new HomeFragment();
            ft.add(R.id.fragment_container, homeFragment, "home");
        }
        if (deviceListFragment == null) {
            deviceListFragment = new DeviceListFragment();
            ft.add(R.id.fragment_container, deviceListFragment, "list");
        }
        if (trackFragment == null) {
            trackFragment = new TrackFragment();
            ft.add(R.id.fragment_container, trackFragment, "track");
        }
        if (profileFragment == null) {
            profileFragment = new ProfileFragment();
            ft.add(R.id.fragment_container, profileFragment, "profile");
        }

        if (isFirstCreate) {
            ft.hide(deviceListFragment);
            ft.hide(trackFragment);
            ft.hide(profileFragment);
            ft.commitNowAllowingStateLoss();

            host.setCurrentTab(0);
            updateTabSelection(0);
            updateHomeUIVisibility(true);
            LogUtil.d(TAG, "=== initFragments END (first create) ===");
        } else {
            removeOrphanFragments(fm);
            if (!ft.isEmpty()) {
                ft.commitNowAllowingStateLoss();
            }
            LogUtil.d(TAG, "=== initFragments END (reused restored fragments) ===");
        }
    }

    /** recreate 后清除历史重复添加、无 tag 的 Fragment，避免 Tab 切换时 hide 错对象 */
    private void removeOrphanFragments(FragmentManager fm) {
        Fragment keepHome = fm.findFragmentByTag("home");
        Fragment keepList = fm.findFragmentByTag("list");
        Fragment keepTrack = fm.findFragmentByTag("track");
        Fragment keepProfile = fm.findFragmentByTag("profile");

        FragmentTransaction ft = fm.beginTransaction();
        boolean changed = false;
        for (Fragment fragment : fm.getFragments()) {
            if (fragment == null || !fragment.isAdded() || fragment.getId() != R.id.fragment_container) {
                continue;
            }
            if (fragment == keepHome || fragment == keepList
                    || fragment == keepTrack || fragment == keepProfile) {
                continue;
            }
            LogUtil.w(TAG, "Removing orphan fragment: " + fragment.getClass().getSimpleName()
                    + ", tag=" + fragment.getTag());
            ft.remove(fragment);
            changed = true;
        }
        if (changed) {
            ft.commitNowAllowingStateLoss();
        }
    }

    /** 语言/主题/地图切换 recreate 后恢复 Tab */
    public void restoreTabIfNeeded() {
        android.content.SharedPreferences prefs = host.getActivity()
                .getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);
        int restoreTab = prefs.getInt("restore_tab_after_config_change", -1);
        if (restoreTab < 0 || restoreTab > 3) {
            return;
        }
        prefs.edit().remove("restore_tab_after_config_change").apply();
        LogUtil.d(TAG, "Restoring tab after config change: " + restoreTab);
        switchToTab(restoreTab);
    }

    /** 强制同步 Fragment 可见性与 currentTab，修复 recreate 后 Tab 栏与内容不一致 */
    public void syncTabToCurrentState() {
        ensureFragmentRefs();
        if (homeFragment == null) {
            return;
        }
        int tab = host.getCurrentTab();
        applyTabVisibility(tab);
        updateTabSelection(tab);
        updateHomeUIVisibility(tab == 0);
    }

    public void switchToTab(int tabIndex) {
        ensureFragmentRefs();
        if (homeFragment == null) {
            LogUtil.w(TAG, "switchToTab: fragments not ready, tab=" + tabIndex);
            return;
        }
        if (tabIndex == host.getCurrentTab()) {
            applyTabVisibility(tabIndex);
            updateTabSelection(tabIndex);
            updateHomeUIVisibility(tabIndex == 0);
            if (host.getActivity() instanceof MainActivity) {
                LanguageIndicatorHelper.refreshMainActivity((MainActivity) host.getActivity());
            }
            return;
        }

        applyTabVisibility(tabIndex);
        host.setCurrentTab(tabIndex);
        updateTabSelection(tabIndex);
        updateHomeUIVisibility(tabIndex == 0);
        if (host.getActivity() instanceof MainActivity) {
            LanguageIndicatorHelper.refreshMainActivity((MainActivity) host.getActivity());
        }
    }

    private void ensureFragmentRefs() {
        AppCompatActivity activity = host.getActivity();
        FragmentManager fm = activity.getSupportFragmentManager();
        if (homeFragment == null) {
            homeFragment = (HomeFragment) fm.findFragmentByTag("home");
        }
        if (deviceListFragment == null) {
            deviceListFragment = (DeviceListFragment) fm.findFragmentByTag("list");
        }
        if (trackFragment == null) {
            trackFragment = (TrackFragment) fm.findFragmentByTag("track");
        }
        if (profileFragment == null) {
            profileFragment = (ProfileFragment) fm.findFragmentByTag("profile");
        }
    }

    private void applyTabVisibility(int tabIndex) {
        ensureFragmentRefs();
        if (homeFragment == null) {
            return;
        }

        FragmentManager fm = host.getActivity().getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        ft.hide(homeFragment);
        if (deviceListFragment != null) {
            ft.hide(deviceListFragment);
        }
        if (trackFragment != null) {
            ft.hide(trackFragment);
        }
        if (profileFragment != null) {
            ft.hide(profileFragment);
        }

        switch (tabIndex) {
            case 0:
                ft.show(homeFragment);
                break;
            case 1:
                if (deviceListFragment != null) {
                    ft.show(deviceListFragment);
                }
                break;
            case 2:
                if (trackFragment != null) {
                    ft.show(trackFragment);
                }
                break;
            case 3:
                if (profileFragment != null) {
                    ft.show(profileFragment);
                }
                break;
            default:
                break;
        }
        ft.commitNowAllowingStateLoss();
    }

    public void handlePendingTabSwitch() {
        if (MainActivity.pendingTabSwitch >= 0) {
            LogUtil.d(TAG, "Returning from TrackActivity, switching to tab " + MainActivity.pendingTabSwitch);
            int targetTab = MainActivity.pendingTabSwitch;
            MainActivity.pendingTabSwitch = -1;
            switchToTab(targetTab);
        } else if (host.getCurrentTab() == 2) {
            LogUtil.d(TAG, "Returning from TrackActivity, switching to home tab");
            switchToTab(0);
        }
    }

    public void updateTabSelection(int tabIndex) {
        tabHome.setSelected(tabIndex == 0);
        tabList.setSelected(tabIndex == 1);
        tabTrack.setSelected(tabIndex == 2);
        tabProfile.setSelected(tabIndex == 3);

        AppCompatActivity activity = host.getActivity();
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

        tabHomeText.setTextColor(tabIndex == 0 ? selectedColor : unselectedColor);
        tabListText.setTextColor(tabIndex == 1 ? selectedColor : unselectedColor);
        tabTrackText.setTextColor(tabIndex == 2 ? selectedColor : unselectedColor);
        tabProfileText.setTextColor(tabIndex == 3 ? selectedColor : unselectedColor);
    }

    public void updateHomeUIVisibility(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        View locateBtn = host.getLocateBtn();
        if (locateBtn != null && locateBtn.getParent() instanceof View) {
            ((View) locateBtn.getParent()).setVisibility(visibility);
        } else {
            View refreshBtn = host.getRefreshBtn();
            View mapTypeBtn = host.getMapTypeBtn();
            if (refreshBtn != null) refreshBtn.setVisibility(visibility);
            if (mapTypeBtn != null) mapTypeBtn.setVisibility(visibility);
            if (locateBtn != null) locateBtn.setVisibility(visibility);
        }
        View customCompass = host.getCustomCompass();
        View bottomInfo = host.getBottomInfo();

        if (customCompass != null) customCompass.setVisibility(visibility);
        if (visible) {
            showBottomInfo();
        } else if (bottomInfo != null) {
            bottomInfo.setVisibility(View.GONE);
        }
    }

    public void showBottomInfo() {
        View bottomInfo = host.getBottomInfo();
        if (bottomInfo != null && host.getCurrentTab() == 0) {
            bottomInfo.setVisibility(View.VISIBLE);
        }
    }

    public void updateCustomCompassRotation(float bearing) {
        View customCompass = host.getCustomCompass();
        if (customCompass != null) {
            customCompass.setRotation(-bearing);
        }
    }

    public void updateTabColors(int selectedColor, int unselectedColor) {
        AppCompatActivity activity = host.getActivity();
        int[][] tabPairs = {
                {R.id.tab_home_icon, R.id.tab_home_text},
                {R.id.tab_list_icon, R.id.tab_list_text},
                {R.id.tab_track_icon, R.id.tab_track_text},
                {R.id.tab_profile_icon, R.id.tab_profile_text}
        };

        for (int i = 0; i < tabPairs.length; i++) {
            ImageView icon = activity.findViewById(tabPairs[i][0]);
            TextView text = activity.findViewById(tabPairs[i][1]);
            boolean isSelected = (i == host.getCurrentTab());
            int color = isSelected ? selectedColor : unselectedColor;
            if (icon != null) icon.setColorFilter(color);
            if (text != null) {
                text.setTextColor(color);
                text.setSelected(isSelected);
            }
        }
    }

    public void onHomeFragmentVisible() {
        updateHomeUIVisibility(true);
    }

    public boolean isCurrentTabHome() {
        return host.getCurrentTab() == 0;
    }

    public void notifyFragmentsThemeChanged(boolean isDarkMode) {
        if (deviceListFragment != null) deviceListFragment.applyTheme(isDarkMode);
        if (profileFragment != null) profileFragment.applyTheme(isDarkMode);
    }
}
