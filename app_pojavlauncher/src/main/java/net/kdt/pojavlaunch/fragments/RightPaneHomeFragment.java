package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
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
import net.kdt.pojavlaunch.utils.MinecraftSkinPreviewRenderer;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lucifer home dashboard. It keeps the original profile actions and launcher event pipeline,
 * while presenting instances and launch controls in the new liquid-glass layout.
 */
public class RightPaneHomeFragment extends Fragment {

    public static final String TAG = "RightPaneHomeFragment";
    public static final String CUSTOM_BG_PATH = Tools.DIR_DATA + "/custom_launcher_bg";
    private static final String TAG_SKIN_PREVIEW = "LuciferSkinPreview";
    private static final long SKIN_PREVIEW_CACHE_MS = 24L * 60L * 60L * 1000L;

    private RecyclerView mRecyclerView;
    private HomeProfileAdapter mAdapter;
    private ImageView mAvatar;
    private TextView mProfileName;
    private TextView mVersion;
    private TextView mLoader;
    private TextView mRam;
    private int mAvatarBindGeneration;
    private boolean mAvatarLoadPending;
    private long mAvatarLoadStartedAt;
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
        showDefaultSkinPreview();
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
            refresh.setOnClickListener(v -> PojavApplication.sExecutorService.execute(() -> {
                LauncherProfiles.load();
                LauncherProfiles.rescanProfiles();
                Tools.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    setupProfileAdapter(true);
                    Toast.makeText(getContext(), R.string.profiles_refreshed, Toast.LENGTH_SHORT).show();
                });
            }));
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

        View openModsFolder = view.findViewById(R.id.btn_open_mods_folder);
        if (openModsFolder != null) {
            openModsFolder.setOnClickListener(v ->
                    MainMenuFragment.openSelectedModsFolder(requireContext()));
        }

        View manageSkins = view.findViewById(R.id.btn_home_skins);
        if (manageSkins != null) {
            manageSkins.setOnClickListener(v -> openPane(
                    SkinManagerFragment.class, SkinManagerFragment.TAG, null));
        }

        View wiki = view.findViewById(R.id.btn_home_wiki);
        if (wiki != null) wiki.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
        View discord = view.findViewById(R.id.btn_home_discord);
        if (discord != null) discord.setOnClickListener(v ->
                Tools.openDiscordInvite(requireActivity(), getString(R.string.discord_invite)));

        bindAccountPreview();
        setupProfileAdapter(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Instance selection is stored synchronously by the picker; refresh its hero from the
        // already-loaded map even when the short adapter-refresh guard is active.
        refreshSelectedInstanceFromLoadedProfiles();
        if (mAdapter != null) {
            mAdapter.setSelectedProfileKey(LauncherPreferences.DEFAULT_PREF.getString(
                    LauncherPreferences.PREF_KEY_CURRENT_PROFILE, ""));
        }
        // Avoid re-reading the same profile file twice during the initial fragment resume. A
        // later return from the profile editor still refreshes the shelf after the short guard.
        setupProfileAdapter(false);
        bindAccountPreview();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mRecyclerView = null;
        mAvatarBindGeneration++;
        mAvatarLoadPending = false;
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
                List<Map.Entry<String, MinecraftProfile>> entries =
                        LauncherProfiles.getProfilesInDisplayOrder();
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
                public void onProfileSelect(String profileKey, MinecraftProfile profile) {
                    selectHomeProfile(profileKey, profile);
                }

                @Override
                public void onProfilePlay(String profileKey, MinecraftProfile profile) {
                    selectHomeProfile(profileKey, profile);
                    ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
                }

                @Override
                public void onProfileEdit(String profileKey, MinecraftProfile profile) {
                    selectHomeProfile(profileKey, profile);
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
            mAdapter.setSelectedProfileKey(currentKey);
            mRecyclerView.setAdapter(mAdapter);
            mLastProfileLoadAt = SystemClock.elapsedRealtime();
        });
    }

    private void selectHomeProfile(String profileKey, MinecraftProfile profile) {
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, profileKey)
                .apply();
        if (mAdapter != null) mAdapter.setSelectedProfileKey(profileKey);
        updateSelectedInstance(profileKey, profile);

        Fragment parent = getParentFragment();
        while (parent != null && !(parent instanceof MainMenuFragment)) {
            parent = parent.getParentFragment();
        }
        if (parent instanceof MainMenuFragment) {
            ((MainMenuFragment) parent).selectInstance(profileKey);
        }
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

    private void showDefaultSkinPreview() {
        if (mAvatar == null) return;
        Bitmap preview = createDefaultSkinPreview(requireContext().getApplicationContext());
        if (preview != null) mAvatar.setImageBitmap(preview);
        mAvatar.setContentDescription(getString(R.string.player_skin_preview));
    }

    private void bindAccountPreview() {
        if (mAvatar == null) return;
        long now = SystemClock.elapsedRealtime();
        if (mAvatarLoadPending && now - mAvatarLoadStartedAt < 1000L) return;
        mAvatarLoadPending = true;
        mAvatarLoadStartedAt = now;
        final ImageView avatar = mAvatar;
        final int bindGeneration = ++mAvatarBindGeneration;
        final Context appContext = requireContext().getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            Bitmap preview = loadPlayerSkinPreview(appContext);
            if (preview == null) preview = createDefaultSkinPreview(appContext);
            final Bitmap result = preview;
            Tools.runOnUiThread(() -> {
                if (mAvatarBindGeneration != bindGeneration) {
                    if (result != null && !result.isRecycled()) result.recycle();
                    return;
                }
                mAvatarLoadPending = false;
                if (result != null && isAdded() && mAvatar == avatar) avatar.setImageBitmap(result);
            });
        });
    }

    @Nullable
    private static Bitmap loadPlayerSkinPreview(@NonNull Context context) {
        try {
            MinecraftAccount account = PojavProfile.getCurrentProfileContent(context, null);
            if (account == null || account.username == null) return null;

            File customSkin = new File(Tools.DIR_DATA + "/skins/" + account.username + "_skin.png");
            if (customSkin.isFile()) {
                Bitmap texture = BitmapFactory.decodeFile(customSkin.getAbsolutePath());
                if (texture != null) {
                    Bitmap preview = MinecraftSkinPreviewRenderer.renderFront(
                            texture, isSlimSkin(account.username));
                    if (!texture.isRecycled()) texture.recycle();
                    if (preview != null) return preview;
                }
            }

            // Offline profiles use a generated local UUID; only Microsoft accounts have a Mojang
            // UUID that can be resolved to the account's current remote skin render.
            if (account.isMicrosoft) return downloadCachedPlayerBody(account.profileId);
        } catch (Exception error) {
            Log.d(TAG_SKIN_PREVIEW, "Could not load the account skin preview", error);
        }
        return null;
    }

    private static boolean isSlimSkin(@NonNull String username) {
        File metadata = new File(Tools.DIR_DATA + "/skins/" + username + "_metadata.json");
        if (!metadata.isFile()) return false;
        try {
            String content = Tools.read(metadata.getAbsolutePath());
            return content != null && content.toLowerCase(Locale.ROOT).contains("slim");
        } catch (Exception ignored) {
            return false;
        }
    }

    @Nullable
    private static Bitmap createDefaultSkinPreview(@NonNull Context context) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap texture = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_steve, options);
        if (texture == null) return null;
        try {
            return MinecraftSkinPreviewRenderer.renderFront(texture, false);
        } finally {
            if (!texture.isRecycled()) texture.recycle();
        }
    }

    @Nullable
    private static Bitmap downloadCachedPlayerBody(@Nullable String profileId) {
        if (profileId == null) return null;
        String uuid = profileId.replace("-", "").trim();
        if (!uuid.matches("(?i)[0-9a-f]{32}")) return null;

        File cache = new File(Tools.DIR_CACHE, "lucifer_skin_body_" + uuid.toLowerCase(Locale.ROOT) + ".png");
        Bitmap staleCache = null;
        if (cache.isFile()) {
            staleCache = BitmapFactory.decodeFile(cache.getAbsolutePath());
            if (staleCache != null && System.currentTimeMillis() - cache.lastModified() < SKIN_PREVIEW_CACHE_MS) {
                return staleCache;
            }
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://mc-heads.net/body/" + uuid + "/128");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", Tools.APP_NAME);
            connection.setRequestProperty("Accept", "image/png,image/*;q=0.8");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return staleCache;

            Bitmap bodyPreview;
            try (InputStream input = connection.getInputStream()) {
                bodyPreview = BitmapFactory.decodeStream(input);
            }
            if (bodyPreview == null) return staleCache;

            File parent = cache.getParentFile();
            if (parent != null && !parent.isDirectory()) parent.mkdirs();
            try (FileOutputStream output = new FileOutputStream(cache)) {
                bodyPreview.compress(Bitmap.CompressFormat.PNG, 100, output);
            } catch (Exception cacheError) {
                Log.d(TAG_SKIN_PREVIEW, "Could not cache the full-body skin preview", cacheError);
            }
            if (staleCache != null && !staleCache.isRecycled()) staleCache.recycle();
            return bodyPreview;
        } catch (Exception error) {
            Log.d(TAG_SKIN_PREVIEW, "Remote full-body skin preview is unavailable", error);
            return staleCache;
        } finally {
            if (connection != null) connection.disconnect();
        }
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
