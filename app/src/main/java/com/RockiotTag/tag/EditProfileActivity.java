package com.RockiotTag.tag;

import com.RockiotTag.tag.util.ToastHelper;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.RockiotTag.tag.util.LogUtil;
import com.RockiotTag.tag.util.StatusBarHelper;

/**
 * 编辑资料Activity
 * 支持修改昵称和头像
 */
public class EditProfileActivity extends AppCompatActivity {

    private static final String TAG = "EditProfileActivity";

    // 12个默认头像资源ID
    public static final int[] AVATAR_RESOURCES = {
        R.drawable.avatar_01, R.drawable.avatar_02, R.drawable.avatar_03,
        R.drawable.avatar_04, R.drawable.avatar_05, R.drawable.avatar_06,
        R.drawable.avatar_07, R.drawable.avatar_08, R.drawable.avatar_09,
        R.drawable.avatar_10, R.drawable.avatar_11, R.drawable.avatar_12
    };

    private ImageView currentAvatar;
    private EditText nicknameEdit;
    private int selectedAvatarIndex = -1;
    private String savedNickname = "";
    private int savedAvatarIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LanguageUtils.applyLanguage(this, LanguageUtils.getSavedLanguage(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        StatusBarHelper.setupStatusBar(this);
        StatusBarHelper.applyTitleBarPadding(this);

        // 返回按钮
        View backBtn = findViewById(R.id.back_btn);
        if (backBtn != null) backBtn.setOnClickListener(v -> finish());

        currentAvatar = findViewById(R.id.current_avatar);
        nicknameEdit = findViewById(R.id.nickname_edit);

        // 加载当前用户信息
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        savedNickname = prefs.getString("user_nickname", "");
        savedAvatarIndex = prefs.getInt("user_avatar_index", -1);
        if (nicknameEdit != null) nicknameEdit.setText(savedNickname);
        if (savedAvatarIndex >= 0 && savedAvatarIndex < AVATAR_RESOURCES.length) {
            selectedAvatarIndex = savedAvatarIndex;
            if (currentAvatar != null) currentAvatar.setImageResource(AVATAR_RESOURCES[savedAvatarIndex]);
        }

        // 头像选择网格（4列 x 3行，全部展示）
        RecyclerView avatarGrid = findViewById(R.id.avatar_grid);
        if (avatarGrid != null) {
            GridLayoutManager layoutManager = new GridLayoutManager(this, 4) {
                @Override
                public boolean canScrollVertically() {
                    return false;
                }
            };
            avatarGrid.setLayoutManager(layoutManager);
            avatarGrid.setNestedScrollingEnabled(false);
            AvatarAdapter adapter = new AvatarAdapter(AVATAR_RESOURCES, selectedAvatarIndex);
            adapter.setOnAvatarClickListener(position -> {
                selectedAvatarIndex = position;
                adapter.setSelectedIndex(position);
                if (currentAvatar != null) {
                    currentAvatar.setImageResource(AVATAR_RESOURCES[position]);
                }
            });
            avatarGrid.setAdapter(adapter);
        }

        // 保存按钮
        View saveBtn = findViewById(R.id.save_btn);
        if (saveBtn != null) saveBtn.setOnClickListener(v -> saveProfile());
    }

    private void saveProfile() {
        String nickname = nicknameEdit != null ? nicknameEdit.getText().toString().trim() : "";
        if (nickname.isEmpty()) {
            ToastHelper.show(this, R.string.profile_nickname_empty);
            return;
        }

        String token = getSharedPreferences("app_settings", MODE_PRIVATE).getString("auth_token", null);
        if (token == null || token.isEmpty()) {
            ToastHelper.show(this, R.string.not_logged_in);
            return;
        }

        View saveBtn = findViewById(R.id.save_btn);
        if (saveBtn != null) saveBtn.setEnabled(false);

        final int avatarIndex = selectedAvatarIndex;
        final boolean nicknameChanged = !nickname.equals(savedNickname);
        final boolean avatarChanged = avatarIndex >= 0 && avatarIndex != savedAvatarIndex;

        if (!nicknameChanged && !avatarChanged) {
            ToastHelper.show(this, R.string.profile_saved);
            finish();
            return;
        }

        new Thread(() -> {
            UserApiService.ProfileUpdateResult result;
            try {
                result = UserApiService.getInstance().updateProfile(
                        token,
                        nickname,
                        avatarIndex >= 0 ? avatarIndex : null,
                        nicknameChanged,
                        avatarChanged);
            } catch (Exception e) {
                result = new UserApiService.ProfileUpdateResult(false, false, e.getMessage());
            }

            final UserApiService.ProfileUpdateResult finalResult = result;
            runOnUiThread(() -> {
                if (saveBtn != null) saveBtn.setEnabled(true);
                if (isFinishing()) return;

                if (finalResult.isSuccess()) {
                    android.content.SharedPreferences.Editor editor =
                            getSharedPreferences("app_settings", MODE_PRIVATE).edit();
                    editor.putString("user_nickname", nickname);
                    if (avatarIndex >= 0) {
                        editor.putInt("user_avatar_index", avatarIndex);
                    }
                    editor.apply();
                    ToastHelper.show(this, R.string.profile_saved);
                    setResult(RESULT_OK);
                    finish();
                } else {
                    LogUtil.d(TAG, "Error updating profile: " + finalResult.errorMessage);
                    ToastHelper.show(this, R.string.save_failed);
                }
            });
        }).start();
    }

    /**
     * 获取头像资源ID
     */
    public static int getAvatarResource(int index) {
        if (index >= 0 && index < AVATAR_RESOURCES.length) {
            return AVATAR_RESOURCES[index];
        }
        return R.drawable.ic_tab_profile;
    }
}
