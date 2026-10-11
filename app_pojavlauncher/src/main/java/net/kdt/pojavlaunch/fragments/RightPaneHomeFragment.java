package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Lucifer home dashboard. It keeps the original profile actions and launcher event pipeline,
 * while presenting instances and launch controls in the new liquid-glass layout.
 */
public class RightPaneHomeFragment extends Fragment {

    public static final String TAG = "RightPaneHomeFragment";
    public static final String CUSTOM_BG_PATH = Tools.DIR_DATA + "/custom_launcher_bg";

    private RecyclerView mRecyclerView;
    private HomeProfileAdapter mAdapter;
    private ImageView mAvatar;
    private TextView mProfileName;
    private TextView mVersion;
    private TextView mLoader;
    private TextView mRam;
    private boolean mProfileLoadPending;
    private long mLastProfileLoadAt;

    public RightPaneHomeFragment() {
        super(R.layout.fragment_right_pane_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        loadCustomWallpaper(view);

        mRecyclerView = view.findViewById(R.id.rv_home_profiles);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setItemViewCacheSize(4);
        mRecyclerView.setItemAnimator(null);

        mAvatar = view.findViewById(R.id.iv_home_avatar);
        mProfileName = view.findViewById(R.id.tv_home_profile_name);
        mVersion = view.findViewById(R.id.tv_home_version);
        mLoader = view.findViewById(R.id.tv_home_loader);
        mRam = view.findViewById(R.id.tv_home_ram);

        View launchPortal = view.findViewById(R.id.btn_launch_portal);
        launchPortal.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

        View createInstance = view.findViewById(R.id.fab_create_profile);
        if (createInstance != null) {
            createInstance.setOnClickListener(v -> openPane(
                    ProfileTypeSelectFragment.class, ProfileTypeSelectFragment.TAG, null));
        }

        View refresh = view.findViewById(R.id.btn_refresh_profiles);
        if (refresh != null) {
            refresh.setOnClickListener(v -> {
                setupProfileAdapter(true);
                Toast.makeText(getContext(), R.string.profiles_refreshed, Toast.LENGTH_SHORT).show();
            });
        }

        View chooseInstance = view.findViewById(R.id.btn_home_instances);
        if (chooseInstance != null) {
            chooseInstance.setOnClickListener(v -> openPane(
                    InstancePickerFragment.class, InstancePickerFragment.TAG, null));
        }

        View openServerHub = view.findViewById(R.id.btn_home_servers);
        View serverCard = view.findViewById(R.id.home_server_card);
        View.OnClickListener serverHubListener = v -> openPane(
                ServerHubFragment.class, ServerHubFragment.TAG, null);
        if (openServerHub != null) openServerHub.setOnClickListener(serverHubListener);
        if (serverCard != null) serverCard.setOnClickListener(serverHubListener);

        View manageSkins = view.findViewById(R.id.btn_home_skins);
        if (manageSkins != null) {
            manageSkins.setOnClickListener(v -> openPane(
                    SkinManagerFragment.class, SkinManagerFragment.TAG, null));
        }

        View wiki = view.findViewById(R.id.btn_home_wiki);
        if (wiki != null) wiki.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
        View discord = view.findViewById(R.id.btn_home_discord);
        if (discord != null) discord.setOnClickListener(v ->
                Tools.openURL(requireActivity(), getString(R.string.discord_invite)));

        bindAccountPreview();
        setupProfileAdapter(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Instance selection is stored synchronously by the picker; refresh its hero from the
        // already-loaded map even when the short adapter-refresh guard is active.
        refreshSelectedInstanceFromLoadedProfiles();
        // Avoid re-reading the same profile file twice during the initial fragment resume. A
        // later return from the profile editor still refreshes the shelf after the short guard.
        setupProfileAdapter(false);
        bindAccountPreview();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mRecyclerView = null;
        mAvatar = null;
        mProfileName = null;
        mVersion = null;
        mLoader = null;
        mRam = null;
        mAdapter = null;
        mProfileLoadPending = false;
    }

    public void reloadBackground() {
        View view = getView();
        if (view != null) loadCustomWallpaper(view);
    }

    private void setupProfileAdapter(boolean force) {
        if (mRecyclerView == null || mProfileLoadPending) return;
        long now = SystemClock.elapsedRealtime();
        if (!force && mAdapter != null && now - mLastProfileLoadAt < 1500L) return;

        mProfileLoadPending = true;
        LauncherProfiles.loadAsync(() -> {
            mProfileLoadPending = false;
            if (!isAdded() || getContext() == null || mRecyclerView == null) return;

            Map<String, MinecraftProfile> profilesMap = LauncherProfiles.mainProfileJson != null
                    ? LauncherProfiles.mainProfileJson.profiles : null;
            List<String> keys = new ArrayList<>();
            List<MinecraftProfile> profiles = new ArrayList<>();

            if (profilesMap != null) {
                List<Map.Entry<String, MinecraftProfile>> entries = new ArrayList<>(profilesMap.entrySet());
                Collections.sort(entries, (left, right) -> {
                    String leftUsed = left.getValue() != null && left.getValue().lastUsed != null
                            ? left.getValue().lastUsed : "";
                    String rightUsed = right.getValue() != null && right.getValue().lastUsed != null
                            ? right.getValue().lastUsed : "";
                    return rightUsed.compareTo(leftUsed);
                });
                for (Map.Entry<String, MinecraftProfile> entry : entries) {
                    String key = entry.getKey();
                    MinecraftProfile profile = entry.getValue();
                    if (key == null || key.trim().isEmpty() || profile == null ||
                            profile.name == null || profile.name.trim().isEmpty()) continue;
                    keys.add(key);
                    profiles.add(profile);
                }
            }

            String currentKey = LauncherPreferences.DEFAULT_PREF.getString(
                    LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
            updateSelectedInstance(currentKey, profilesMap != null ? profilesMap.get(currentKey) : null);

            mAdapter = new HomeProfileAdapter(keys, profiles, new HomeProfileAdapter.OnProfileActionListener() {
                @Override
                public void onProfilePlay(String profileKey, MinecraftProfile profile) {
                    LauncherPreferences.DEFAULT_PREF.edit()
                            .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, profileKey)
                            .apply();
                    ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
                }

                @Override
                public void onProfileEdit(String profileKey, MinecraftProfile profile) {
                    LauncherPreferences.DEFAULT_PREF.edit()
                            .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, profileKey)
                            .apply();
                    Tools.swapFragment(requireActivity(), ProfileEditorFragment.class,
                            ProfileEditorFragment.TAG, null);
                }

                @Override
                public void onProfileAddShortcut(String profileKey, MinecraftProfile profile) {
                    Bundle args = new Bundle();
                    args.putString(net.kdt.pojavlaunch.shortcuts.ShortcutIconPickerFragment.ARG_PROFILE_KEY,
                            profileKey);
                    openPane(net.kdt.pojavlaunch.shortcuts.ShortcutIconPickerFragment.class,
                            net.kdt.pojavlaunch.shortcuts.ShortcutIconPickerFragment.TAG, args);
                }
            });
            mRecyclerView.setAdapter(mAdapter);
            mLastProfileLoadAt = SystemClock.elapsedRealtime();
        });
    }

    private void updateSelectedInstance(String profileKey, @Nullable MinecraftProfile profile) {
        if (mProfileName == null || mVersion == null || mLoader == null || mRam == null) return;
        if (profile == null) {
            mProfileName.setText(getString(R.string.no_profile_selected));
            mVersion.setText(getString(R.string.choose_minecraft_version));
            mLoader.setText(getString(R.string.vanilla));
        } else {
            mProfileName.setText(profile.name != null ? profile.name : profileKey);
            mVersion.setText(profile.lastVersionId != null && !profile.lastVersionId.isEmpty()
                    ? profile.lastVersionId : getString(R.string.unknown));
            mLoader.setText(getLoaderName(profile));
        }
        mRam.setText(getString(R.string.ram_summary_gb,
                LauncherPreferences.PREF_RAM_ALLOCATION / 1024.0));
    }

    private void refreshSelectedInstanceFromLoadedProfiles() {
        if (mProfileName == null || LauncherProfiles.mainProfileJson == null ||
                LauncherProfiles.mainProfileJson.profiles == null) return;
        String currentKey = LauncherPreferences.DEFAULT_PREF.getString(
                LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
        updateSelectedInstance(currentKey,
                LauncherProfiles.mainProfileJson.profiles.get(currentKey));
    }

    private String getLoaderName(MinecraftProfile profile) {
        if (profile.isOptiFine()) return "OptiFine";
        String versionId = profile.lastVersionId == null ? "" : profile.lastVersionId.toLowerCase();
        if (versionId.contains("fabric")) return "Fabric";
        if (versionId.contains("neoforge")) return "NeoForge";
        if (versionId.contains("forge")) return "Forge";
        if (versionId.contains("quilt")) return "Quilt";
        return getString(R.string.vanilla);
    }

    private void bindAccountPreview() {
        if (mAvatar == null) return;
        final ImageView avatar = mAvatar;
        final Context appContext = requireContext().getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            Bitmap head = null;
            try {
                MinecraftAccount account = PojavProfile.getCurrentProfileContent(appContext, null);
                if (account != null) head = account.getSkinFace();
            } catch (Exception ignored) {}
            final Bitmap result = head;
            if (result != null) Tools.runOnUiThread(() -> {
                if (isAdded() && mAvatar == avatar) avatar.setImageBitmap(result);
            });
        });
    }

    private void openPane(Class<? extends Fragment> fragmentClass, String tag, @Nullable Bundle args) {
        Fragment parent = getParentFragment();
        if (parent instanceof MainMenuFragment) {
            ((MainMenuFragment) parent).openChildPane(fragmentClass, tag, args);
        } else if (getActivity() != null) {
            Tools.swapFragment(requireActivity(), fragmentClass, tag, args);
        }
    }

    private void loadCustomWallpaper(@NonNull View view) {
        final ImageView wallpaper = view.findViewById(R.id.right_pane_wallpaper);
        final File background = new File(CUSTOM_BG_PATH);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaper.setBackground(null);
        if (!background.isFile()) {
            wallpaper.setImageDrawable(null);
            wallpaper.setVisibility(View.GONE);
            return;
        }

        // Decode large user-selected images off the UI thread and downsample them to the pane's
        // rendered size. This avoids a synchronous full-resolution bitmap allocation on resume.
        wallpaper.post(() -> {
            if (!isAdded() || getView() != view) return;
            final int targetWidth = Math.max(1, wallpaper.getWidth());
            final int targetHeight = Math.max(1, wallpaper.getHeight());
            PojavApplication.sExecutorService.execute(() -> {
                Bitmap decoded = null;
                try {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(background.getAbsolutePath(), bounds);
                    int sample = 1;
                    while (bounds.outWidth / (sample * 2) >= targetWidth &&
                            bounds.outHeight / (sample * 2) >= targetHeight) {
                        sample *= 2;
                    }
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = sample;
                    decoded = BitmapFactory.decodeFile(background.getAbsolutePath(), options);
                } catch (OutOfMemoryError | RuntimeException ignored) {}

                final Bitmap result = decoded;
                Tools.runOnUiThread(() -> {
                    if (result == null || !isAdded() || getView() != view || !background.isFile()) {
                        if (result != null && !result.isRecycled()) result.recycle();
                        return;
                    }
                    wallpaper.setImageBitmap(result);
                    wallpaper.setVisibility(View.VISIBLE);
                });
            });
        });
    }
}
