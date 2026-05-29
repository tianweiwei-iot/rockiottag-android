package com.RockiotTag.tag.usecase;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.util.Log;

import com.RockiotTag.tag.DatabaseHelper;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * 逆地理编码UseCase
 * 
 * 职责：
 * 1. 根据经纬度获取地址信息
 * 2. 支持缓存机制（提高性能）
 * 3. 支持多语言（根据系统语言自动选择）
 * 
 * 使用示例：
 * <pre>
 * ReverseGeocodeUseCase.Params params = new ReverseGeocodeUseCase.Params(
 *     22.543611, 113.881944, "zh-CN", false
 * );
 * useCase.execute(params).observe(this, resource -> {
 *     if (resource.isSuccess()) {
 *         String address = resource.data;
 *         updateAddressUI(address);
 *     }
 * });
 * </pre>
 */
public class ReverseGeocodeUseCase extends BaseUseCase<ReverseGeocodeUseCase.Params, String> {
    
    private static final String TAG = "ReverseGeocodeUseCase";
    private static final long CACHE_EXPIRY_MS = 7L * 24 * 60 * 60 * 1000; // 7天
    
    /**
     * UseCase参数类
     */
    public static class Params {
        public final double latitude;
        public final double longitude;
        public final String languageCode;
        public final boolean forceRefresh;
        public final boolean useAMapGeocoder; // 是否使用高德地理编码
        public final String mapMode; // 地图模式标识
        
        /**
         * 构造函数
         * 
         * @param latitude 纬度
         * @param longitude 经度
         * @param languageCode 语言代码（如"zh-CN", "en"）
         * @param forceRefresh 是否强制刷新（忽略缓存）
         * @param useAMapGeocoder 是否使用高德地理编码（高德地图模式）
         * @param mapMode 地图模式（"amap" 或 "google"）
         */
        public Params(double latitude, double longitude, String languageCode, boolean forceRefresh, boolean useAMapGeocoder, String mapMode) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.languageCode = languageCode != null ? languageCode : "zh-CN";
            this.forceRefresh = forceRefresh;
            this.useAMapGeocoder = useAMapGeocoder;
            this.mapMode = mapMode != null ? mapMode : "amap";
        }
    }
    
    private final Context context;
    private final DatabaseHelper dbHelper;
    
    /**
     * 构造函数
     * 
     * @param context 上下文
     * @param dbHelper 数据库助手
     */
    public ReverseGeocodeUseCase(Context context, DatabaseHelper dbHelper) {
        this.context = context.getApplicationContext(); // 避免内存泄漏
        this.dbHelper = dbHelper;
    }
    
    /**
     * 执行逆地理编码
     * 
     * @param params 参数（经纬度、语言等）
     * @return 地址字符串
     * @throws Exception 如果编码失败
     */
    @Override
    protected String executeSync(Params params) throws Exception {
        Log.d(TAG, "Reverse geocoding: " + params.latitude + ", " + params.longitude);
        
        // 1. 检查缓存（如果不是强制刷新）
        if (!params.forceRefresh) {
            String cachedAddress = dbHelper.getCachedAddress(
                params.latitude,
                params.longitude,
                params.languageCode,
                CACHE_EXPIRY_MS,
                params.mapMode
            );
            
            if (cachedAddress != null && !cachedAddress.isEmpty()) {
                Log.d(TAG, "Cache hit: " + cachedAddress);
                return cachedAddress;
            }
            Log.d(TAG, "Cache miss, performing geocoding");
        }
        
        // 2. 验证坐标有效性
        if (!isValidCoordinate(params.latitude, params.longitude)) {
            throw new IllegalArgumentException("无效的坐标: " + params.latitude + ", " + params.longitude);
        }
        
        // 3. 执行地理编码
        String address = performGeocoding(params.latitude, params.longitude, params.languageCode, params.useAMapGeocoder);
        
        if (address == null || address.isEmpty()) {
            Log.w(TAG, "Geocoding returned empty result");
            throw new RuntimeException("无法获取地址信息");
        }
        
        Log.d(TAG, "Geocoding success: " + address);
        
        // 4. 保存到缓存
        try {
            dbHelper.saveAddressToCache(
                params.latitude,
                params.longitude,
                params.languageCode,
                params.mapMode,
                address
            );
            Log.d(TAG, "Address saved to cache");
        } catch (Exception e) {
            Log.w(TAG, "Failed to save address to cache: " + e.getMessage());
            // 缓存失败不影响主流程
        }
        
        return address;
    }
    
    /**
     * 执行地理编码
     * @param useAMapGeocoder 是否使用高德地理编码（高德地图模式）
     */
    private String performGeocoding(double latitude, double longitude, String languageCode, boolean useAMapGeocoder) {
        if (useAMapGeocoder) {
            // 高德地图模式：使用高德地理编码服务
            return performAMapGeocoding(latitude, longitude);
        } else {
            // Google地图模式：使用Android原生Geocoder
            // 注：Google Geocoding REST API需要Billing账号，暂时使用Android Geocoder
            // Android Geocoder已有地址验证逻辑，会过滤错误的深圳地址
            Log.d(TAG, "Using Android native Geocoder for Google Map mode");
            return performAndroidGeocoding(latitude, longitude, languageCode);
        }
    }
    
    /**
     * 使用高德地理编码服务
     */
    private String performAMapGeocoding(double latitude, double longitude) {
        try {
            com.RockiotTag.tag.map.amap.AMapGeocoder aMapGeocoder = 
                new com.RockiotTag.tag.map.amap.AMapGeocoder(context);
            
            // 同步调用高德地理编码
            String address = aMapGeocoder.getAddressFromLocationSync(latitude, longitude);
            
            if (address != null && !address.isEmpty()) {
                Log.d(TAG, "AMap geocoding success: " + address);
                return address;
            }
            
            Log.w(TAG, "AMap geocoding returned empty result");
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, "AMap geocoding error: " + e.getMessage(), e);
            throw new RuntimeException("高德地理编码失败: " + e.getMessage());
        }
    }
    
    /**
     * 使用Android原生Geocoder
     */
    private String performAndroidGeocoding(double latitude, double longitude, String languageCode) {
        try {
            // 创建Geocoder，指定语言
            Locale locale = parseLocale(languageCode);
            Geocoder geocoder = new Geocoder(context, locale);
            
            // 获取地址（最多1个结果）
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String result = formatAddress(address);
                Log.d(TAG, "Android Geocoder result: " + result);
                
                // 关键修复：验证返回的地址是否与坐标匹配
                // Android Geocoder可能受VPN影响返回错误地址
                boolean containsShenzhen = result.contains("Shenzhen") || result.contains("深圳");
                boolean isNearShenzhen = Math.abs(latitude - 22.54) < 1 && Math.abs(longitude - 113.88) < 1;
                
                if (containsShenzhen && !isNearShenzhen) {
                    // 坐标不是深圳，但返回了深圳地址，说明Geocoder可能受VPN或缓存影响
                    Log.w(TAG, "Geocoder returned incorrect Shenzhen address for non-Shenzhen location: " + latitude + ", " + longitude);
                    Log.w(TAG, "This may be caused by VPN or Geocoder cache. Using coordinates as address.");
                    // 返回坐标作为地址
                    return String.format("%.6f, %.6f", latitude, longitude);
                }
                
                // 额外的校验：如果地址包含"China"或"中国"但坐标明显不是中国
                boolean containsChina = result.contains("China") || result.contains("中国");
                boolean isNearChina = longitude > 73 && longitude < 135 && latitude > 3 && latitude < 54;
                
                if (containsChina && !isNearChina) {
                    Log.w(TAG, "Geocoder returned incorrect China address for non-China location: " + latitude + ", " + longitude);
                    Log.w(TAG, "This may be caused by VPN or Geocoder cache. Using coordinates as address.");
                    return String.format("%.6f, %.6f", latitude, longitude);
                }
                
                // 关键修复：验证返回的国家是否与坐标匹配
                // 支持多个国家：阿尔及利亚、乌克兰、俄罗斯、巴西、土耳其、印度
                String countryName = address.getCountryName();
                if (countryName != null && !countryName.isEmpty()) {
                    Log.d(TAG, "Geocoder countryName: " + countryName + ", lat: " + latitude + ", lng: " + longitude);
                    
                    // 阿尔及利亚的坐标范围
                    boolean isAlgeriaCoords = latitude > 19 && latitude < 38 && longitude > -9 && longitude < 12;
                    if (isAlgeriaCoords) {
                        String countryLower = countryName.toLowerCase();
                        if (!countryLower.contains("algeria") && !countryName.contains("阿尔及利亚") 
                            && !countryLower.contains("algérie") && !countryLower.contains("algerie")) {
                            Log.w(TAG, "Geocoder returned country '" + countryName + "' but coordinates are in Algeria: " + latitude + ", " + longitude);
                            Log.w(TAG, "This may be caused by VPN or Geocoder cache. Using coordinates as address.");
                            return String.format("%.6f, %.6f", latitude, longitude);
                        }
                        // 国家匹配，跳过后续验证
                        return result;
                    }
                    
                    // 乌克兰的坐标范围（约44-53°N, 22-41°E）
                    boolean isUkraineCoords = latitude > 44 && latitude < 53 && longitude > 22 && longitude < 41;
                    if (isUkraineCoords) {
                        String countryLower = countryName.toLowerCase();
                        if (!countryLower.contains("ukraine") && !countryName.contains("乌克兰")
                            && !countryLower.contains("україна") && !countryLower.contains("украина")) {
                            Log.w(TAG, "Geocoder returned country '" + countryName + "' but coordinates are in Ukraine: " + latitude + ", " + longitude);
                            Log.w(TAG, "This may be caused by VPN or Geocoder cache. Using coordinates as address.");
                            return String.format("%.6f, %.6f", latitude, longitude);
                        }
                        // 国家匹配，跳过后续验证（避免与俄罗斯范围重叠）
                        return result;
                    }
                    
                    // 俄罗斯的坐标范围（约41-82°N, 19-180°E）
                    // 注意：乌克兰坐标也在俄罗斯范围内，所以必须先验证乌克兰
                    boolean isRussiaCoords = latitude > 41 && latitude < 82 && longitude > 19 && longitude < 180;
                    if (isRussiaCoords) {
                        String countryLower = countryName.toLowerCase();
                        if (!countryLower.contains("russia") && !countryName.contains("俄罗斯")
                            && !countryLower.contains("россия")) {
                            Log.w(TAG, "Geocoder returned country '" + countryName + "' but coordinates are in Russia: " + latitude + ", " + longitude);
                            Log.w(TAG, "This may be caused by VPN or Geocoder cache. Using coordinates as address.");
                            return String.format("%.6f, %.6f", latitude, longitude);
                        }
                        return result;
                    }
                    
                    // 巴西的坐标范围（约-34-5°N, -74-34°W）
                    boolean isBrazilCoords = latitude > -34 && latitude < 5 && longitude < -34 && longitude > -74;
                    if (isBrazilCoords) {
                        String countryLower = countryName.toLowerCase();
                        if (!countryLower.contains("brazil") && !countryName.contains("巴西")
                            && !countryLower.contains("brasil")) {
                            Log.w(TAG, "Geocoder returned country '" + countryName + "' but coordinates are in Brazil: " + latitude + ", " + longitude);
                            Log.w(TAG, "This may be caused by VPN or Geocoder cache. Using coordinates as address.");
                            return String.format("%.6f, %.6f", latitude, longitude);
                        }
                        return result;
                    }
                    
                    // 土耳其的坐标范围（约36-42°N, 26-45°E）
                    boolean isTurkeyCoords = latitude > 36 && latitude < 42 && longitude > 26 && longitude < 45;
                    if (isTurkeyCoords) {
                        String countryLower = countryName.toLowerCase();
                        if (!countryLower.contains("turkey") && !countryName.contains("土耳其")
                            && !countryLower.contains("türkiye") && !countryLower.contains("turkiye")) {
                            Log.w(TAG, "Geocoder returned country '" + countryName + "' but coordinates are in Turkey: " + latitude + ", " + longitude);
                            Log.w(TAG, "This may be caused by VPN or Geocoder cache. Using coordinates as address.");
                            return String.format("%.6f, %.6f", latitude, longitude);
                        }
                        return result;
                    }
                    
                    // 印度的坐标范围（约6-37°N, 68-98°E）
                    boolean isIndiaCoords = latitude > 6 && latitude < 37 && longitude > 68 && longitude < 98;
                    if (isIndiaCoords) {
                        String countryLower = countryName.toLowerCase();
                        if (!countryLower.contains("india") && !countryName.contains("印度")
                            && !countryLower.contains("bharat")) {
                            Log.w(TAG, "Geocoder returned country '" + countryName + "' but coordinates are in India: " + latitude + ", " + longitude);
                            Log.w(TAG, "This may be caused by VPN or Geocoder cache. Using coordinates as address.");
                            return String.format("%.6f, %.6f", latitude, longitude);
                        }
                        return result;
                    }
                }
                
                return result;
            }
            
            Log.w(TAG, "No address found for location");
            return null;
            
        } catch (IOException e) {
            Log.e(TAG, "Geocoder IO error: " + e.getMessage(), e);
            throw new RuntimeException("地理编码服务不可用，请检查网络连接");
            
        } catch (Exception e) {
            Log.e(TAG, "Geocoding error: " + e.getMessage(), e);
            throw new RuntimeException("地理编码失败: " + e.getMessage());
        }
    }
    
    /**
     * 格式化地址
     */
    private String formatAddress(Address address) {
        StringBuilder sb = new StringBuilder();
        
        // 组合地址行
        for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            String line = address.getAddressLine(i);
            if (line != null && !line.isEmpty()) {
                sb.append(line);
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 解析语言代码
     */
    private Locale parseLocale(String languageCode) {
        if (languageCode == null || languageCode.isEmpty()) {
            return Locale.getDefault();
        }
        
        // 处理"zh-CN"、"en-US"等格式
        if (languageCode.contains("-")) {
            String[] parts = languageCode.split("-");
            if (parts.length == 2) {
                return new Locale(parts[0], parts[1]);
            }
        }
        
        return new Locale(languageCode);
    }
    
    /**
     * 验证坐标有效性
     */
    private boolean isValidCoordinate(double latitude, double longitude) {
        return latitude >= -90 && latitude <= 90 &&
               longitude >= -180 && longitude <= 180;
    }
}
