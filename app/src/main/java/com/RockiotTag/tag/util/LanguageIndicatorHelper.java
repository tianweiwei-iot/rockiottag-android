package com.RockiotTag.tag.util;

import android.app.Activity;
import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.RockiotTag.tag.LanguageUtils;
import com.RockiotTag.tag.MainActivity;
import com.RockiotTag.tag.R;

/**
 * 在所有界面标题栏或右上角展示当前选中的语言。
 */
public final class LanguageIndicatorHelper {

    private LanguageIndicatorHelper() {}

    public static void bind(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        if (activity instanceof MainActivity) {
            refreshMainActivity((MainActivity) activity);
            return;
        }
        View contentRoot = activity.findViewById(android.R.id.content);
        if (contentRoot instanceof ViewGroup) {
            ViewGroup contentGroup = (ViewGroup) contentRoot;
            if (contentGroup.getChildCount() == 1 && contentGroup.getChildAt(0) instanceof ViewGroup) {
                bindInto((ViewGroup) contentGroup.getChildAt(0), activity);
            } else {
                bindInto(contentGroup, activity);
            }
        }
    }

    public static void bind(Fragment fragment) {
        if (fragment == null || fragment.getContext() == null) {
            return;
        }
        View root = fragment.getView();
        if (root instanceof ViewGroup) {
            bindInto((ViewGroup) root, fragment.requireContext());
        }
    }

    public static void refreshMainActivity(MainActivity activity) {
        if (activity == null) {
            return;
        }
        TextView homeIndicator = activity.findViewById(R.id.current_language_text);
        int tab = activity.getCurrentTabIndex();
        if (homeIndicator != null) {
            boolean showOnHome = tab == 0;
            homeIndicator.setVisibility(showOnHome ? View.VISIBLE : View.GONE);
            if (showOnHome) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) homeIndicator.getLayoutParams();
                if (lp != null) {
                    lp.topMargin = getStatusBarHeight(activity) + dp(activity, 8);
                    homeIndicator.setLayoutParams(lp);
                }
                updateView(homeIndicator, activity);
            }
        }

        FragmentManager fm = activity.getSupportFragmentManager();
        bindVisibleMainFragment(fm.findFragmentByTag("list"), tab == 1);
        bindVisibleMainFragment(fm.findFragmentByTag("track"), tab == 2);
        bindVisibleMainFragment(fm.findFragmentByTag("profile"), tab == 3);
    }

    private static void bindVisibleMainFragment(Fragment fragment, boolean visible) {
        if (!visible || fragment == null || fragment.getView() == null) {
            return;
        }
        bind(fragment);
    }

    public static void updateView(TextView textView, Context context) {
        if (textView == null || context == null) {
            return;
        }
        textView.setText(LanguageUtils.getCurrentLanguageLabel(context));
        applyStyle(textView, context);
    }

    private static void bindInto(ViewGroup root, Context context) {
        TextView existing = root.findViewById(R.id.current_language_text);
        if (existing != null) {
            updateView(existing, context);
            existing.setVisibility(View.VISIBLE);
            return;
        }

        View titleBar = root.findViewById(R.id.title_bar);
        if (titleBar == null) {
            titleBar = root.findViewById(R.id.top_bar);
        }
        if (titleBar instanceof ViewGroup) {
            injectIntoTitleBar((ViewGroup) titleBar, context);
            return;
        }

        attachOverlay(root, context);
    }

    private static void injectIntoTitleBar(ViewGroup titleBar, Context context) {
        TextView existing = titleBar.findViewById(R.id.current_language_text);
        if (existing != null) {
            updateView(existing, context);
            return;
        }

        TextView indicator = createIndicatorView(context);
        indicator.setId(R.id.current_language_text);

        if (titleBar instanceof LinearLayout
                && ((LinearLayout) titleBar).getOrientation() == LinearLayout.HORIZONTAL) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.CENTER_VERTICAL;
            lp.setMarginStart(dp(context, 8));
            int insertIndex = titleBar.getChildCount();
            if (insertIndex > 0) {
                View lastChild = titleBar.getChildAt(insertIndex - 1);
                if (isTrailingActionOrSpacer(lastChild)) {
                    insertIndex--;
                }
            }
            titleBar.addView(indicator, insertIndex, lp);
        } else if (titleBar instanceof ConstraintLayout) {
            ConstraintLayout cl = (ConstraintLayout) titleBar;
            ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.topMargin = dp(context, 8);
            lp.setMarginEnd(dp(context, 12));
            cl.addView(indicator, lp);
        } else {
            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            titleBar.addView(indicator, lp);
        }
        updateView(indicator, context);
    }

    private static void attachOverlay(ViewGroup root, Context context) {
        TextView indicator = createIndicatorView(context);
        indicator.setId(R.id.current_language_text);

        if (root instanceof FrameLayout) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.topMargin = getStatusBarHeight(context) + dp(context, 8);
            lp.setMarginEnd(dp(context, 12));
            root.addView(indicator, lp);
        } else if (root instanceof ConstraintLayout) {
            ConstraintLayout cl = (ConstraintLayout) root;
            ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            cl.addView(indicator, lp);
            ConstraintSet set = new ConstraintSet();
            set.clone(cl);
            set.connect(indicator.getId(), ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP,
                    getStatusBarHeight(context) + dp(context, 8));
            set.connect(indicator.getId(), ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END,
                    dp(context, 12));
            set.applyTo(cl);
            indicator.setElevation(dp(context, 4));
        } else if (root instanceof LinearLayout
                && ((LinearLayout) root).getOrientation() == LinearLayout.VERTICAL) {
            LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.END);
            int horizontal = dp(context, 12);
            header.setPadding(horizontal, getStatusBarHeight(context) + dp(context, 8), horizontal, dp(context, 4));
            header.addView(indicator);
            root.addView(header, 0);
        } else if (root.getChildCount() == 1 && root.getChildAt(0) instanceof ViewGroup) {
            bindInto((ViewGroup) root.getChildAt(0), context);
            return;
        } else {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.topMargin = getStatusBarHeight(context) + dp(context, 8);
            lp.setMarginEnd(dp(context, 12));
            root.addView(indicator, lp);
        }
        updateView(indicator, context);
    }

    private static TextView createIndicatorView(Context context) {
        TextView textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        textView.setMaxLines(1);
        textView.setContentDescription(context.getString(R.string.current_language_cd));
        textView.setBackgroundResource(R.drawable.bg_language_indicator);
        int paddingH = dp(context, 8);
        int paddingV = dp(context, 4);
        textView.setPadding(paddingH, paddingV, paddingH, paddingV);
        applyStyle(textView, context);
        return textView;
    }

    private static void applyStyle(TextView textView, Context context) {
        boolean darkMode = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getBoolean("dark_mode", false);
        int textColor = context.getResources().getColor(
                darkMode ? R.color.dark_text_secondary : R.color.text_secondary, null);
        textView.setTextColor(textColor);
    }

    private static boolean isTrailingActionOrSpacer(View view) {
        if (view == null) {
            return false;
        }
        int id = view.getId();
        return id == R.id.save_btn
                || id == R.id.add_device_btn
                || id == R.id.scan_qr_btn
                || id == R.id.multi_select_btn
                || (id == View.NO_ID && view.getWidth() == 0 && !(view instanceof TextView));
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static int getStatusBarHeight(Context context) {
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return context.getResources().getDimensionPixelSize(resourceId);
        }
        return dp(context, 24);
    }
}
