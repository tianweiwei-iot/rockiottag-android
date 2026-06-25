package com.RockiotTag.tag.helper;

import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.RockiotTag.tag.CoordinateUtils;
import com.RockiotTag.tag.R;
import com.RockiotTag.tag.map.IMapAdapter;
import com.RockiotTag.tag.util.GeocodeHelper;
import com.RockiotTag.tag.util.LogUtil;
import com.amap.api.maps.model.LatLng;

/**
 * TrackActivity 轨迹点地址解析逻辑。
 */
public class TrackGeocodeHelper {

    private static final String TAG = "TrackGeocodeHelper";

    public interface Host {
        AppCompatActivity getActivity();
        IMapAdapter getMapAdapter();
        TextView getTrackPointAddress();
        void runOnUiThread(Runnable runnable);
    }

    public static void getAddressForLocation(Host host, double[] wgs84LatLng) {
        if (wgs84LatLng == null || wgs84LatLng.length < 2) {
            Log.w(TAG, "Invalid coordinates");
            return;
        }

        AppCompatActivity activity = host.getActivity();
        IMapAdapter mapAdapter = host.getMapAdapter();
        TextView addressView = host.getTrackPointAddress();
        if (activity == null || mapAdapter == null || addressView == null) return;

        try {
            double lat = wgs84LatLng[0];
            double lng = wgs84LatLng[1];
            if (mapAdapter.getProvider().equals("google")) {
                String address = GeocodeHelper.reverseGeocodeWithAndroidGeocoder(activity, lat, lng);
                addressView.setText(address != null && !address.isEmpty()
                        ? address : activity.getString(R.string.unknown_location));
            } else {
                LatLng gcj02 = CoordinateUtils.wgs84ToGcj02(lat, lng);
                GeocodeHelper.reverseGeocodeWithAMap(activity, gcj02.latitude, gcj02.longitude,
                        new GeocodeHelper.OnGeocodeResultListener() {
                            @Override
                            public void onGeocodeSuccess(String address) {
                                host.runOnUiThread(() -> addressView.setText(address));
                            }

                            @Override
                            public void onGeocodeFailed(String error) {
                                host.runOnUiThread(() -> {
                                    Log.e(TAG, "Geocode failed: " + error);
                                    addressView.setText(activity.getString(R.string.get_address_failed));
                                });
                            }
                        });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in getAddressForLocation: " + e.getMessage(), e);
            addressView.setText(activity.getString(R.string.get_address_failed));
        }
    }
}
