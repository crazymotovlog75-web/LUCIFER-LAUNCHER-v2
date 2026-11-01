package net.kdt.pojavlaunch.fragments;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RightPaneHomeFragment extends Fragment {

    public static final String TAG = "RightPaneHomeFragment";
    public static final String CUSTOM_BG_PATH = Tools.DIR_DATA + "/custom_launcher_bg";

    private int mSkinRequest;
    private android.opengl.GLSurfaceView mHomeSkin;
    private SkinManagerFragment.SkinRenderer mHomeRenderer;
    private com.kdt.mcgui.mcVersionSpinner mHomeVersionSpinner;
    private android.widget.TextView mHomeVersionId;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener mAccountListener =
            (prefs, key) -> refreshHomeSkin();
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener mProfileListener =
            (prefs, key) -> {
                if (LauncherPreferences.PREF_KEY_CURRENT_PROFILE.equals(key)) updateVersionLabel();
            };
    private RecyclerView mRecyclerView;
    private HomeProfileAdapter mAdapter;

    public RightPaneHomeFragment() {
        super(R.layout.fragment_right_pane_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        loadCustomWallpaper(view);
        mHomeSkin = view.findViewById(R.id.home_skin_surface);
        if (mHomeSkin != null) {
            setupLauncherHome(view);
            return;
        }

        mRecyclerView = view.findViewById(R.id.rv_home_profiles);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        setupProfileAdapter();

        // Floating "+" FAB opens the Version Setup Hub (3-category grid)
        View fab = view.findViewById(R.id.fab_create_profile);
        if (fab != null) {
            // Apply 200ms scale-up reveal with DecelerateInterpolator
            fab.setScaleX(0.6f);
            fab.setScaleY(0.6f);
            fab.setAlpha(0f);
            // Enable hardware layer during animation to reduce jank on low-end devices
            fab.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            fab.animate()
                    .scaleX(1f).scaleY(1f)
                    .alpha(1f)
                    .setDuration(200)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> fab.setLayerType(View.LAYER_TYPE_NONE, null))
                    .start();

            fab.setOnClickListener(v -> {
                if (!isAdded() || getContext() == null) return;
                Fragment parent = getParentFragment();
                if (parent instanceof MainMenuFragment) {
                    ((MainMenuFragment) parent).openChildPane(
                            ProfileTypeSelectFragment.class,
                            ProfileTypeSelectFragment.TAG, null);
                } else if (getActivity() != null) {
                    Tools.swapFragment(getActivity(),
                            ProfileTypeSelectFragment.class,
                            ProfileTypeSelectFragment.TAG, null);
                }
            });
        }

        View refreshBtn = view.findViewById(R.id.btn_refresh_profiles);
        if (refreshBtn != null) {
            refreshBtn.setOnClickListener(v -> {
                setupProfileAdapter();
                Toast.makeText(getContext(), "Profiles refreshed", Toast.LENGTH_SHORT).show();
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mHomeSkin != null) {
            mHomeSkin.onResume();
            refreshHomeSkin();
            mHomeVersionSpinner.reloadProfiles();
            updateVersionLabel();
        } else if (mRecyclerView != null) {
            setupProfileAdapter();
        }
    }

    @Override
    public void onPause() {
        if (mHomeSkin != null) mHomeSkin.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (mHomeSkin != null) {
            net.kdt.pojavlaunch.PojavProfile.getPrefs(requireContext()).unregisterOnSharedPreferenceChangeListener(mAccountListener);
            LauncherPreferences.DEFAULT_PREF.unregisterOnSharedPreferenceChangeListener(mProfileListener);
        }
        mSkinRequest++;
        mHomeSkin = null;
        mHomeRenderer = null;
        mHomeVersionSpinner = null;
        mHomeVersionId = null;
        mRecyclerView = null;
        super.onDestroyView();
    }

    private void setupLauncherHome(View view) {
        mHomeSkin.setEGLContextClientVersion(2);
        mHomeSkin.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        mHomeSkin.getHolder().setFormat(android.graphics.PixelFormat.TRANSLUCENT);
        mHomeSkin.setZOrderOnTop(true);
        mHomeRenderer = new SkinManagerFragment.SkinRenderer(requireContext(), true);
        mHomeRenderer.mAngleX = -15f;
        mHomeSkin.setRenderer(mHomeRenderer);
        mHomeSkin.setRenderMode(android.opengl.GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        mHomeVersionSpinner = view.findViewById(R.id.home_version_spinner);
        mHomeVersionId = view.findViewById(R.id.home_version_id);
        mHomeVersionSpinner.setOnClickListener(v -> openHomePane(InstancePickerFragment.class, InstancePickerFragment.TAG));
        view.findViewById(R.id.home_play_button).setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
        view.findViewById(R.id.home_edit_skin).setOnClickListener(v -> openHomePane(SkinManagerFragment.class, SkinManagerFragment.TAG));
        net.kdt.pojavlaunch.PojavProfile.getPrefs(requireContext()).registerOnSharedPreferenceChangeListener(mAccountListener);
        LauncherPreferences.DEFAULT_PREF.registerOnSharedPreferenceChangeListener(mProfileListener);
        refreshHomeSkin();
        updateVersionLabel();
    }

    private void openHomePane(Class<? extends Fragment> cls, String tag) {
        Fragment parent = getParentFragment();
        if (parent instanceof MainMenuFragment) ((MainMenuFragment) parent).openChildPane(cls, tag, null);
        else Tools.swapFragment(requireActivity(), cls, tag, null);
    }

    private void updateVersionLabel() {
        if (mHomeVersionId == null) return;
        String key = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
        MinecraftProfile profile = LauncherProfiles.mainProfileJson != null && LauncherProfiles.mainProfileJson.profiles != null
                ? LauncherProfiles.mainProfileJson.profiles.get(key) : null;
        String version = profile == null ? null : profile.lastVersionId;
        mHomeVersionId.setText(version == null || version.isEmpty() ? getString(R.string.lucifer_no_version) : version);
    }

    private void refreshHomeSkin() {
        if (!isAdded() || mHomeRenderer == null || mHomeSkin == null) return;
        String name = net.kdt.pojavlaunch.PojavProfile.getCurrentProfileName(requireContext());
        int request = ++mSkinRequest;
        // Local custom skins use the same files as the existing skin editor.
        File skinFile = new File(Tools.DIR_DATA + "/skins", name + "_skin.png");
        File metaFile = new File(Tools.DIR_DATA + "/skins", name + "_metadata.json");
        android.graphics.Bitmap skin = skinFile.isFile() ? android.graphics.BitmapFactory.decodeFile(skinFile.getAbsolutePath()) : null;
        if (skin == null) {
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inScaled = false;
            skin = android.graphics.BitmapFactory.decodeResource(getResources(), R.drawable.ic_steve, options);
        }
        boolean slim = false;
        if (metaFile.isFile()) {
            try { slim = Tools.read(metaFile.getAbsolutePath()).contains("slim"); }
            catch (Exception ignored) { }
        }
        mHomeRenderer.mIsSlim = slim;
        mHomeRenderer.setTexture(skin, null);
        mHomeSkin.requestRender();

        // For Microsoft accounts, use the official full skin when no local override exists.
        net.kdt.pojavlaunch.value.MinecraftAccount account =
                net.kdt.pojavlaunch.PojavProfile.getCurrentProfileContent(requireContext(), null);
        if (!skinFile.isFile() && account != null && account.isMicrosoft && account.profileId != null) {
            String uuid = account.profileId;
            android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
            net.kdt.pojavlaunch.PojavApplication.sExecutorService.execute(() -> {
                HomeSkinLoader.Result result = HomeSkinLoader.load(uuid);
                if (result == null) return;
                main.post(() -> {
                    if (!isAdded() || request != mSkinRequest || mHomeRenderer == null || mHomeSkin == null) return;
                    mHomeRenderer.mIsSlim = result.slim;
                    mHomeRenderer.setTexture(result.skin, null);
                    mHomeSkin.requestRender();
                });
            });
        }
    }

    public void reloadBackground() {
        View v = getView();
        if (v != null) loadCustomWallpaper(v);
    }

    private void setupProfileAdapter() {
        // Load profiles on background thread to avoid blocking the UI thread on file I/O
        LauncherProfiles.loadAsync(() -> {
            if (!isAdded() || getContext() == null || mRecyclerView == null) return;

            Map<String, MinecraftProfile> profilesMap = LauncherProfiles.mainProfileJson != null
                    ? LauncherProfiles.mainProfileJson.profiles : null;

            // Note: Do NOT drop all cached icons here — doing so recycles the underlying
            // bitmaps while RecyclerView items may still be drawing them, causing
            // "Canvas: trying to use a recycled bitmap" crashes.
            // Per-profile icon invalidation happens in ProfileEditorFragment.dropIcon()
            // when the user actually edits a profile's icon.

            List<String> keys = new ArrayList<>();
            List<MinecraftProfile> profiles = new ArrayList<>();

            if (profilesMap != null) {
                List<Map.Entry<String, MinecraftProfile>> entries =
                        new ArrayList<>(profilesMap.entrySet());
                // Sort by lastUsed descending (most recently used first)
                Collections.sort(entries, (a, b) -> {
                    String ua = a.getValue().lastUsed != null ? a.getValue().lastUsed : "";
                    String ub = b.getValue().lastUsed != null ? b.getValue().lastUsed : "";
                    return ub.compareTo(ua);
                });
                for (Map.Entry<String, MinecraftProfile> entry : entries) {
                    String key = entry.getKey();
                    MinecraftProfile profile = entry.getValue();
                    // Skip invalid/corrupted profiles
                    if (key == null || key.isEmpty()) continue;
                    if (profile == null) continue;
                    if (profile.name == null || profile.name.trim().isEmpty()) continue;
                    keys.add(key);
                    profiles.add(profile);
                }
            }

            mAdapter = new HomeProfileAdapter(keys, profiles,
                    new HomeProfileAdapter.OnProfileActionListener() {
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
                    Tools.swapFragment(requireActivity(),
                            ProfileEditorFragment.class, ProfileEditorFragment.TAG, null);
                }

                @Override
                public void onProfileAddShortcut(String profileKey, MinecraftProfile profile) {
                    Bundle args = new Bundle();
                    args.putString(
                            net.kdt.pojavlaunch.shortcuts.ShortcutIconPickerFragment.ARG_PROFILE_KEY,
                            profileKey);
                    Fragment parent = getParentFragment();
                    if (parent instanceof MainMenuFragment) {
                        ((MainMenuFragment) parent).openChildPane(
                                net.kdt.pojavlaunch.shortcuts.ShortcutIconPickerFragment.class,
                                net.kdt.pojavlaunch.shortcuts.ShortcutIconPickerFragment.TAG,
                                args);
                    } else if (getActivity() != null) {
                        Tools.swapFragment(requireActivity(),
                                net.kdt.pojavlaunch.shortcuts.ShortcutIconPickerFragment.class,
                                net.kdt.pojavlaunch.shortcuts.ShortcutIconPickerFragment.TAG,
                                args);
                    }
                }
            });

            mRecyclerView.setAdapter(mAdapter);
        });
    }

    private void loadCustomWallpaper(@NonNull View view) {
        ImageView wallpaper = view.findViewById(R.id.right_pane_wallpaper);
        File bgFile = new File(CUSTOM_BG_PATH);
        if (bgFile.exists()) {
            Drawable d = Drawable.createFromPath(bgFile.getAbsolutePath());
            if (d != null) {
                wallpaper.setImageDrawable(d);
                wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
                wallpaper.setBackground(null);
                wallpaper.setVisibility(View.VISIBLE);
                return;
            }
        }
        wallpaper.setImageDrawable(null);
        wallpaper.setBackground(null);
        wallpaper.setVisibility(View.GONE);
    }
}
