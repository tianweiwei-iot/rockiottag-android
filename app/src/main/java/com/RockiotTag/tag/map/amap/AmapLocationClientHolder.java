package com.RockiotTag.tag.map.amap;

import android.content.Context;

import com.RockiotTag.tag.util.LogUtil;
import com.amap.api.location.AMapLocationClient;

/**
 * 高德定位 SDK 要求进程内仅保留一个 {@link AMapLocationClient} 实例。
 */
public final class AmapLocationClientHolder {

    private static final String TAG = "AmapLocationClientHolder";
    private static AMapLocationClient client;

    private AmapLocationClientHolder() {
    }

    public static synchronized AMapLocationClient obtain(Context context) throws Exception {
        Context appContext = context.getApplicationContext();
        if (client == null) {
            AMapLocationClient.updatePrivacyShow(appContext, true, true);
            AMapLocationClient.updatePrivacyAgree(appContext, true);
            client = new AMapLocationClient(appContext);
            LogUtil.d(TAG, "AMapLocationClient created");
        }
        return client;
    }

    public static synchronized void release() {
        if (client != null) {
            try {
                client.stopLocation();
            } catch (Exception ignored) {
            }
            try {
                client.onDestroy();
            } catch (Exception ignored) {
            }
            client = null;
            LogUtil.d(TAG, "AMapLocationClient released");
        }
    }
}
