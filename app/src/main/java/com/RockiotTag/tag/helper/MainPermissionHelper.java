package com.RockiotTag.tag.helper;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * MainActivity 运行时权限请求逻辑。
 */
public class MainPermissionHelper {

    public interface Host {
        AppCompatActivity getActivity();
        void showPermissionRationale(String[] permissions);
        void requestPermissions(String[] permissions, int requestCode);
        /** 全部权限已具备时回调（含首次启动已授权、或用户刚授权） */
        void onPermissionsGranted();
    }

    public static String[] getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        return permissions.toArray(new String[0]);
    }

    public static boolean hasAllPermissions(AppCompatActivity activity) {
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(activity, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static String[] getMissingPermissions(AppCompatActivity activity) {
        List<String> missing = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(activity, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        return missing.toArray(new String[0]);
    }

    public static void checkPermissions(Host host, int requestCode) {
        AppCompatActivity activity = host.getActivity();
        if (hasAllPermissions(activity)) {
            host.onPermissionsGranted();
            return;
        }

        String[] missing = getMissingPermissions(activity);
        if (missing.length == 0) {
            host.onPermissionsGranted();
            return;
        }

        boolean shouldShowRationale = false;
        for (String permission : missing) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                shouldShowRationale = true;
                break;
            }
        }

        if (shouldShowRationale) {
            host.showPermissionRationale(missing);
        } else {
            host.requestPermissions(missing, requestCode);
        }
    }
}
