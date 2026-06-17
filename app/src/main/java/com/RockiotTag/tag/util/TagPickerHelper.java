package com.RockiotTag.tag.util;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.ContextThemeWrapper;
import android.widget.Spinner;

import com.RockiotTag.tag.R;
import com.RockiotTag.tag.TagAdapter;
import com.RockiotTag.tag.model.DeviceTag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 设备标签选择器辅助类，与绑定设备时的标签列表保持一致。
 */
public final class TagPickerHelper {

    /** 与 AddDeviceActivity / DeviceListActivity 绑定时使用的标签顺序一致 */
    public static final String[] TAG_CODES = {
            "dog", "boy", "car", "bike", "bank_card", "girl", "key", "moto", "pig", "wallet", "bag", "cat", "bird"
    };

    private TagPickerHelper() {}

    public static List<String> buildTagList(Context context) {
        List<String> tagList = new ArrayList<>();
        tagList.add(context.getString(R.string.no_tag));
        tagList.addAll(Arrays.asList(TAG_CODES));
        return tagList;
    }

    public static List<String> buildIconList() {
        List<String> iconList = new ArrayList<>();
        iconList.add("");
        for (String code : TAG_CODES) {
            iconList.add(DeviceTag.getEmoji(code));
        }
        return iconList;
    }

    public static int findTagPosition(List<String> tagList, String currentTag) {
        if (currentTag == null || currentTag.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < tagList.size(); i++) {
            if (tagList.get(i).equalsIgnoreCase(currentTag)) {
                return i;
            }
        }
        return 0;
    }

    public static String getTagFromPosition(List<String> tagList, int position) {
        return position > 0 ? tagList.get(position) : "";
    }

    public static void setupTagSpinner(Context context, Spinner spinner, String currentTag) {
        boolean darkMode = ThemedDialogHelper.isDarkModeEnabled(context);
        Context adapterContext = darkMode
                ? new ContextThemeWrapper(context, R.style.AlertDialogTheme_Dark)
                : context;

        List<String> tagList = buildTagList(context);
        List<String> iconList = buildIconList();
        TagAdapter adapter = new TagAdapter(adapterContext, tagList, iconList);
        spinner.setAdapter(adapter);
        spinner.setSelection(findTagPosition(tagList, currentTag));

        if (darkMode) {
            int bgColor = context.getResources().getColor(R.color.dark_surface, null);
            spinner.setBackgroundColor(bgColor);
            spinner.setPopupBackgroundDrawable(new ColorDrawable(bgColor));
        }
    }
}
