package com.RockiotTag.tag;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPreferencesManager {
    private static final String PREF_NAME = "RockiotTagPrefs";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_CID = "cid";
    
    public static void saveAuth(Context context, int userId, String token, String userName, String cid) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_USER_ID, userId);
        editor.putString(KEY_TOKEN, token);
        editor.putString(KEY_USER_NAME, userName);
        editor.putString(KEY_CID, cid);
        editor.apply();
    }
    
    public static void loadAuth(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int userId = prefs.getInt(KEY_USER_ID, 0);
        String token = prefs.getString(KEY_TOKEN, null);
        String userName = prefs.getString(KEY_USER_NAME, null);
        String cid = prefs.getString(KEY_CID, null);
        
        NewApiService apiService = NewApiService.getInstance();
        apiService.setAuth(userId, token, userName, cid);
    }
    
    public static void clearAuth(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
        
        NewApiService apiService = NewApiService.getInstance();
        apiService.clearAuth();
    }
    
    public static int getUserId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_USER_ID, 0);
    }
    
    public static String getToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_TOKEN, null);
    }
    
    public static String getUserName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_USER_NAME, null);
    }
    
    public static boolean isAuthenticated(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int userId = prefs.getInt(KEY_USER_ID, 0);
        String token = prefs.getString(KEY_TOKEN, null);
        return userId > 0 && token != null && !token.isEmpty();
    }
}
