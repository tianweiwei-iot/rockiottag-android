package com.RockiotTag.tag;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

public class LanguageUtils {
    
    public static void applyLanguage(Context context, String languageCode) {
        Locale locale;
        if (languageCode.equals("pt-rBR")) {
            locale = new Locale("pt", "BR");
        } else {
            locale = new Locale(languageCode);
        }
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.setLocale(locale);
        context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
    }
    
    public static String getSystemLanguage() {
        Locale systemLocale = Locale.getDefault();
        String language = systemLocale.getLanguage();
        if (language.equals("en")) {
            return "en";
        } else if (language.equals("pt")) {
            String country = systemLocale.getCountry();
            if (country != null && country.equals("BR")) {
                return "pt-rBR";
            }
            return "pt-rBR";
        } else if (language.equals("tr")) {
            return "tr";
        } else if (language.equals("ru")) {
            return "ru";
        } else if (language.equals("hi")) {
            return "hi";
        } else {
            return "zh";
        }
    }
    
    public static boolean hasUserSelectedLanguage(Context context) {
        return context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getBoolean("user_selected_language", false);
    }
    
    public static String getSavedLanguage(Context context) {
        if (hasUserSelectedLanguage(context)) {
            return context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    .getString("language", getSystemLanguage());
        } else {
            return getSystemLanguage();
        }
    }
    
    public static void saveLanguage(Context context, String languageCode) {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit()
                .putString("language", languageCode)
                .putBoolean("user_selected_language", true)
                .apply();
    }
}
