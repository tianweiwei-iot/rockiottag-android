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

    public static String getLanguageDisplayName(String languageCode) {
        if (languageCode == null) {
            return "";
        }
        switch (languageCode) {
            case "en":
                return "English";
            case "pt-rBR":
                return "Português";
            case "ru":
                return "Русский";
            case "hi":
                return "हिंदी";
            case "tr":
                return "Türkçe";
            case "zh":
            default:
                return "中文";
        }
    }

    public static String getLanguageFlagEmoji(String languageCode) {
        if (languageCode == null) {
            return "";
        }
        switch (languageCode) {
            case "en":
                return "\uD83C\uDDEC\uD83C\uDDE7";
            case "pt-rBR":
                return "\uD83C\uDDE7\uD83C\uDDF7";
            case "ru":
                return "\uD83C\uDDF7\uD83C\uDDFA";
            case "hi":
                return "\uD83C\uDDEE\uD83C\uDDF3";
            case "tr":
                return "\uD83C\uDDF9\uD83C\uDDF7";
            case "zh":
            default:
                return "\uD83C\uDDE8\uD83C\uDDF3";
        }
    }

    /** 当前语言展示标签，如 "🇨🇳 中文" */
    public static String getCurrentLanguageLabel(Context context) {
        String code = getSavedLanguage(context);
        return getLanguageFlagEmoji(code) + " " + getLanguageDisplayName(code);
    }
}
