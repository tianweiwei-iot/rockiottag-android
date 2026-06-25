# RockiotTag ProGuard Rules

# ========================
# 日志移除配置
# ========================
# 移除 Log.d/v/i 调用（Release 构建优化）
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ========================
# 通用配置
# ========================
-dontoptimize
-dontpreverify

-keepattributes Exceptions,InnerClasses,Signature,Deprecated,*Annotation*,SourceFile,LineNumberTable,EnclosingMethod

# ========================
# Android 基础类
# ========================
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Fragment
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.Application

# ========================
# 项目自有类（Release 稳定性：保留完整包，避免 Helper 回调 / Gson 被误删）
# ========================
-keep class com.RockiotTag.tag.BuildConfig { *; }
-keep class com.RockiotTag.tag.** { *; }
-keep interface com.RockiotTag.tag.** { *; }

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ========================
# ViewModel 和 LiveData
# ========================
-keep class androidx.lifecycle.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

# ========================
# Room 数据库
# ========================
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# ========================
# Gson 序列化（TypeToken 在 Release 必须保留）
# ========================
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ========================
# Volley（高德 SDK 依赖）
# ========================
-keep class com.android.volley.** { *; }
-dontwarn com.android.volley.**

# ========================
# 高德地图 SDK
# ========================
-keep class com.amap.api.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.a.a.** { *; }
-keep class com.loc.** { *; }
-dontwarn com.amap.ams.gnss.**
-dontwarn net.jafama.**

# ========================
# Google Maps / Play Services
# ========================
-keep class com.google.android.gms.** { *; }
-keep class com.google.android.gms.maps.** { *; }
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

# ========================
# ZXing 条码扫描
# ========================
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }

# ========================
# Google ML Kit
# ========================
-keep class com.google.mlkit.** { *; }

# ========================
# CameraX
# ========================
-keep class androidx.camera.** { *; }

# ========================
# Glide 图片加载
# ========================
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder { *** rewind(); }
