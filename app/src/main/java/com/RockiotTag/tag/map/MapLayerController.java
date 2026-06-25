package com.RockiotTag.tag.map;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.RockiotTag.tag.util.LogUtil;
import com.amap.api.maps.MapView;
import com.google.android.gms.maps.SupportMapFragment;

/**
 * 高德 / Google 地图视图层隔离：同一时刻仅显示并激活一套地图 SDK 对应的 View。
 * 国内用高德、国外用 Google；未激活的一侧保持 GONE，不参与生命周期（由 Activity 控制）。
 */
public final class MapLayerController {

    private static final String TAG = "MapLayerController";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private MapLayerController() {
    }

    public static boolean isAmapProvider(String provider) {
        return MapAdapterFactory.PROVIDER_AMAP.equals(provider);
    }

    public static boolean isGoogleProvider(String provider) {
        return MapAdapterFactory.PROVIDER_GOOGLE.equals(provider);
    }

    /** 按当前提供商显示对应地图层，隐藏另一层。 */
    public static void applyLayers(String provider, MapView amapView, SupportMapFragment googleFragment) {
        if (isAmapProvider(provider)) {
            showAmapHideGoogle(amapView, googleFragment);
        } else {
            showGoogleHideAmap(amapView, googleFragment);
        }
    }

    public static void showAmapHideGoogle(MapView amapView, SupportMapFragment googleFragment) {
        hideGoogle(googleFragment);
        if (amapView != null) {
            amapView.setVisibility(View.VISIBLE);
            bringToFront(amapView);
            LogUtil.d(TAG, "Active map layer: AMap");
        }
    }

    private static final int GOOGLE_SHOW_MAX_RETRIES = 15;
    private static final long GOOGLE_SHOW_RETRY_MS = 100L;

    public static void showGoogleHideAmap(MapView amapView, SupportMapFragment googleFragment) {
        hideAmap(amapView);
        showGoogle(googleFragment);
        if (isGoogleViewSized(googleFragment)) {
            LogUtil.d(TAG, "Active map layer: Google");
        } else {
            LogUtil.w(TAG, "Google map view not sized yet, will retry");
            scheduleGoogleShowRetry(amapView, googleFragment, 0);
        }
    }

    /** recreate 前取消 Google 层显示重试，避免旧 Activity 的 Fragment 被延迟任务访问 */
    public static void cancelPendingRetries() {
        MAIN.removeCallbacksAndMessages(null);
    }

    private static void scheduleGoogleShowRetry(MapView amapView, SupportMapFragment googleFragment, int attempt) {
        if (attempt >= GOOGLE_SHOW_MAX_RETRIES) {
            LogUtil.w(TAG, "Google map view not ready after " + GOOGLE_SHOW_MAX_RETRIES + " retries");
            return;
        }
        MAIN.postDelayed(() -> {
            hideAmap(amapView);
            showGoogle(googleFragment);
            if (isGoogleViewSized(googleFragment)) {
                LogUtil.d(TAG, "Active map layer: Google (retry " + attempt + ")");
            } else {
                scheduleGoogleShowRetry(amapView, googleFragment, attempt + 1);
            }
        }, attempt == 0 ? 0 : GOOGLE_SHOW_RETRY_MS);
    }

    private static boolean isGoogleViewSized(SupportMapFragment googleFragment) {
        if (googleFragment == null || googleFragment.getView() == null) {
            return false;
        }
        View view = googleFragment.getView();
        return view.getVisibility() == View.VISIBLE
                && view.getWidth() > 0
                && view.getHeight() > 0;
    }

    public static void hideAmap(MapView amapView) {
        if (amapView != null) {
            amapView.setVisibility(View.GONE);
        }
    }

    public static void hideGoogle(SupportMapFragment googleFragment) {
        if (googleFragment != null && googleFragment.getView() != null) {
            googleFragment.getView().setVisibility(View.GONE);
        }
    }

    private static boolean showGoogle(SupportMapFragment googleFragment) {
        if (googleFragment == null || googleFragment.getView() == null) {
            return false;
        }
        View view = googleFragment.getView();
        view.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            view.setLayoutParams(lp);
        }
        ViewParent parent = view.getParent();
        if (parent instanceof View) {
            ((View) parent).requestLayout();
        }
        view.requestLayout();
        bringToFront(view);
        return true;
    }

    private static void bringToFront(View view) {
        if (view == null) {
            return;
        }
        ViewParent parent = view.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).bringChildToFront(view);
        }
    }
}
