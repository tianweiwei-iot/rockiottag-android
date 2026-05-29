package com.RockiotTag.tag.helper;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.util.Log;
import android.widget.TextView;

import com.RockiotTag.tag.R;
import com.amap.api.maps.model.LatLng;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * 轨迹地址解析助手
 * 职责：封装逆地理编码逻辑
 */
public class TrackAddressHelper {
    private static final String TAG = "TrackAddressHelper";
    
    /**
     * 获取地址（高德地图）
     */
    public static void getAddressForLocation(
        Context context,
        LatLng latLng,
        TextView addressTextView) {
        
        if (latLng == null || addressTextView == null) {
            return;
        }
        
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(
                latLng.latitude, latLng.longitude, 1);
            
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder addressBuilder = new StringBuilder();
                
                if (address.getCountryName() != null) {
                    addressBuilder.append(address.getCountryName());
                }
                if (address.getAdminArea() != null) {
                    addressBuilder.append(address.getAdminArea());
                }
                if (address.getLocality() != null) {
                    addressBuilder.append(address.getLocality());
                }
                if (address.getThoroughfare() != null) {
                    addressBuilder.append(address.getThoroughfare());
                }
                
                addressTextView.setText(addressBuilder.toString());
            } else {
                addressTextView.setText(R.string.unknown_location_short);
            }
        } catch (IOException e) {
            Log.e(TAG, "Geocoder error: " + e.getMessage());
            addressTextView.setText(R.string.get_address_failed_short);
        }
    }
    
    /**
     * 获取地址（Google 地图）
     */
    public static void getAddressForLocationOnGoogleMap(
        Context context,
        com.google.android.gms.maps.model.LatLng latLng,
        TextView addressTextView) {
        
        if (latLng == null || addressTextView == null) {
            return;
        }
        
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(
                latLng.latitude, latLng.longitude, 1);
            
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder addressBuilder = new StringBuilder();
                
                if (address.getCountryName() != null) {
                    addressBuilder.append(address.getCountryName());
                }
                if (address.getAdminArea() != null) {
                    addressBuilder.append(address.getAdminArea());
                }
                if (address.getLocality() != null) {
                    addressBuilder.append(address.getLocality());
                }
                if (address.getThoroughfare() != null) {
                    addressBuilder.append(address.getThoroughfare());
                }
                
                addressTextView.setText(addressBuilder.toString());
            } else {
                addressTextView.setText(R.string.unknown_location_short);
            }
        } catch (IOException e) {
            Log.e(TAG, "Geocoder error: " + e.getMessage());
            addressTextView.setText(R.string.get_address_failed_short);
        }
    }
}
