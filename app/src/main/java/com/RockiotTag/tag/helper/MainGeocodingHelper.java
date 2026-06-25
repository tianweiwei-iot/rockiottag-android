package com.RockiotTag.tag.helper;

import com.RockiotTag.tag.map.IMapAdapter;
import com.RockiotTag.tag.util.LogUtil;
import com.RockiotTag.tag.viewmodel.MainViewModel;

/**
 * MainActivity 逆地理编码请求逻辑。
 */
public class MainGeocodingHelper {

    private static final String TAG = "MainGeocodingHelper";

    public static void getAddressFromLocation(
            MainViewModel viewModel,
            IMapAdapter mapAdapter,
            double latitude,
            double longitude,
            boolean forceRefresh,
            String languageCode) {

        LogUtil.d(TAG, "Getting address: lat=" + latitude + ", lng=" + longitude
                + ", forceRefresh=" + forceRefresh);

        boolean isGoogleMap = mapAdapter != null && mapAdapter.getProvider().equals("google");
        boolean useAMapGeocoder = !isGoogleMap;
        String mapMode = isGoogleMap ? "google" : "amap";

        if (isGoogleMap && !forceRefresh) {
            forceRefresh = true;
        }

        if (viewModel != null) {
            viewModel.getAddress(latitude, longitude, languageCode, forceRefresh, useAMapGeocoder, mapMode);
        }
    }
}
