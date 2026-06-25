package com.RockiotTag.tag.map;

import android.content.Context;

import com.RockiotTag.tag.MapManager;
import com.RockiotTag.tag.map.amap.AMapManager;
import com.RockiotTag.tag.map.google.GoogleMapManager;
import com.amap.api.maps.MapView;
import com.google.android.gms.maps.SupportMapFragment;

/**
 * 地图适配器工厂
 * 根据当前地图提供商返回对应的 IMapAdapter 实例
 */
public class MapAdapterFactory {

    public static final String PROVIDER_AMAP = "amap";
    public static final String PROVIDER_GOOGLE = "google";

    private MapAdapterFactory() {
    }

    /**
     * 读取用户保存的地图提供商；未手动切换时默认高德（与 MapManager 一致）。
     * 注意：不得使用 Locale.getDefault() 自动切换，否则切换应用语言会误改地图。
     */
    public static String getSavedProvider(Context context) {
        return context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getString("map_provider", MapManager.MAP_PROVIDER_AMAP);
    }

    /**
     * 首次安装、尚未写入 map_provider 时的建议默认值（国内高德，国外 Google）。
     */
    public static String getDefaultProviderForRegion() {
        return isLikelyInMainlandChina() ? PROVIDER_AMAP : PROVIDER_GOOGLE;
    }

    /**
     * 粗略判断是否在中国大陆环境（用于切换地图时的提示，非强制）。
     */
    public static boolean isLikelyInMainlandChina() {
        java.util.Locale locale = java.util.Locale.getDefault();
        if ("CN".equalsIgnoreCase(locale.getCountry())) {
            return true;
        }
        String language = locale.getLanguage();
        return "zh".equals(language);
    }

    /** 当前环境是否建议使用高德（国内）。 */
    public static boolean isAmapRecommended() {
        return isLikelyInMainlandChina();
    }

    /** 当前环境是否建议使用 Google（海外）。 */
    public static boolean isGoogleRecommended() {
        return !isLikelyInMainlandChina();
    }

    /**
     * 判断当前应使用的地图提供商（仅用于首次安装、尚未写入 map_provider 时的建议值）
     * 国内使用高德，国际使用 Google
     * @deprecated 请使用 {@link #getDefaultProviderForRegion()}
     */
    @Deprecated
    public static String getDefaultProvider() {
        return getDefaultProviderForRegion();
    }

    /**
     * 创建高德地图适配器
     *
     * @param context 上下文
     * @param mapView 高德 MapView
     * @return AMapManager 实例
     */
    public static IMapAdapter createAMapAdapter(Context context, MapView mapView) {
        return new AMapManager(context, mapView);
    }

    /**
     * 创建谷歌地图适配器
     *
     * @param context       上下文
     * @param mapFragment    Google SupportMapFragment
     * @return GoogleMapManager 实例
     */
    public static IMapAdapter createGoogleMapAdapter(Context context, SupportMapFragment mapFragment) {
        return new GoogleMapManager(context, mapFragment);
    }
}
