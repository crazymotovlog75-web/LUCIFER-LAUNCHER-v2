package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.liquid.Liquid;
import net.kdt.pojavlaunch.liquid.ServerPinger;
import net.kdt.pojavlaunch.liquid.ServerStore;
import net.kdt.pojavlaunch.value.MinecraftAccount;
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

    private RecyclerView mRecyclerView;
    private HomeProfileAdapter mAdapter;

    public RightPaneHomeFragment() {
        super(R.layout.fragment_right_pane_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        loadCustomWallpaper(view);
        bindNexusHome(view);

        mRecyclerView = view.findViewById(R.id.rv_home_profiles);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        // Performance: the instance list never changes size, so skip layout re-measurement and the
        // per-change item animations, and keep a few extra rows cached while scrolling.
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setItemAnimator(null);
        mRecyclerView.setItemViewCacheSize(8);
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
        setupProfileAdapter();
    }

    public void reloadBackground() {
        View v = getView();
        if (v != null) loadCustomWallpaper(v);
    }

    private void setupProfileAdapter() {
        // Load profiles on background thread to avoid blocking the UI thread on file I/O
        LauncherProfiles.loadAsync(() -> {
            if (!isAdded() || getContext() == null) return;

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
            updateHomeHeader(keys.size(), profiles.isEmpty() ? null : profiles.get(0));
        });
    }

    /** Keeps the version pill / instance counter in sync with the selected profile. */
    private void updateHomeHeader(int instanceCount, @Nullable MinecraftProfile mostRecent) {
        View root = getView();
        if (root == null) return;
        TextView count = root.findViewById(R.id.lc_instances_count);
        if (count != null) count.setText(String.valueOf(instanceCount));

        TextView pill = root.findViewById(R.id.lc_version_pill);
        if (pill == null) return;
        String currentKey = LauncherPreferences.DEFAULT_PREF
                .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        MinecraftProfile profile = (LauncherProfiles.mainProfileJson != null
                && LauncherProfiles.mainProfileJson.profiles != null)
                ? LauncherProfiles.mainProfileJson.profiles.get(currentKey) : null;
        if (profile == null) profile = mostRecent;
        if (profile != null && profile.lastVersionId != null && !profile.lastVersionId.isEmpty()) {
            String loader = loaderOf(profile);
            pill.setText(loader.isEmpty() ? profile.lastVersionId
                    : loader + "  " + profile.lastVersionId);
        } else {
            pill.setText(R.string.lc_no_version);
        }
    }

    private String loaderOf(MinecraftProfile profile) {
        if (profile.isOptiFine()) return "OptiFine";
        String id = profile.lastVersionId == null ? "" : profile.lastVersionId.toLowerCase();
        if (id.contains("fabric")) return "Fabric";
        if (id.contains("neoforge")) return "NeoForge";
        if (id.contains("forge")) return "Forge";
        if (id.contains("quilt")) return "Quilt";
        return "";
    }

    // ── Liquid Crystal home: stage, launch orb, beacon, quick orbs ───────────

    /**
     * Wires the Nexus home: the liquid launch orb, the player stage, the featured server beacon
     * and the quick-action orbs. Every action reuses an existing launcher code path, so behaviour
     * is unchanged — only the presentation is new.
     */
    private void bindNexusHome(@NonNull View view) {
        // ── launch orb ────────────────────────────────────────────────────
        View launchOrb = view.findViewById(R.id.lc_launch_orb);
        if (launchOrb != null) {
            Liquid.pressFeedback(launchOrb);
            launchOrb.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
            Liquid.reveal(launchOrb, 120L);
        }
        // orbiting halo: GPU transform only (no redraw of the scene behind it)
        View halo = view.findViewById(R.id.lc_launch_halo);
        if (halo != null && Liquid.animationsEnabled()) {
            android.animation.ObjectAnimator spin = android.animation.ObjectAnimator.ofFloat(
                    halo, View.ROTATION, 0f, 360f);
            spin.setDuration(16000L);
            spin.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            spin.setInterpolator(new android.view.animation.LinearInterpolator());
            spin.start();
        }

        // ── player stage ──────────────────────────────────────────────────
        final TextView stageName = view.findViewById(R.id.lc_stage_name);
        final ImageView stageHead = view.findViewById(R.id.lc_stage_head);
        final Context appContext = requireContext().getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            Bitmap head = null;
            String username = null;
            try {
                MinecraftAccount account = PojavProfile.getCurrentProfileContent(appContext, null);
                if (account != null) {
                    username = account.username;
                    head = account.getSkinFace();
                }
            } catch (Throwable ignored) {
            }
            final Bitmap finalHead = head;
            final String finalName = username;
            Tools.runOnUiThread(() -> {
                if (!isAdded()) return;
                if (stageName != null && finalName != null) stageName.setText(finalName);
                if (stageHead != null) {
                    if (finalHead != null) stageHead.setImageBitmap(finalHead);
                    else stageHead.setImageResource(R.drawable.ic_steve);
                }
            });
        });

        // ── quick orbs: skin studio + input studio ────────────────────────
        View skinOrb = view.findViewById(R.id.lc_orb_skin);
        if (skinOrb != null) {
            Liquid.pressFeedback(skinOrb);
            skinOrb.setOnClickListener(v ->
                    openChild(SkinManagerFragment.class, SkinManagerFragment.TAG));
        }
        View gamepadOrb = view.findViewById(R.id.lc_orb_gamepad);
        if (gamepadOrb != null) {
            Liquid.pressFeedback(gamepadOrb);
            gamepadOrb.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        }

        // ── version pill → instance picker ────────────────────────────────
        TextView versionPill = view.findViewById(R.id.lc_version_pill);
        if (versionPill != null) {
            versionPill.setOnClickListener(v ->
                    openChild(VersionManagerFragment.class, VersionManagerFragment.TAG));
        }

        // ── featured server beacon → Server Hub ───────────────────────────
        View beacon = view.findViewById(R.id.lc_beacon);
        View beaconPlay = view.findViewById(R.id.lc_beacon_play);
        View.OnClickListener hub = v ->
                openChild(ServerHubFragment.class, ServerHubFragment.TAG);
        if (beacon != null) beacon.setOnClickListener(hub);
        if (beaconPlay != null) beaconPlay.setOnClickListener(hub);

        bindBeaconContent(view);
    }

    /** Fills the beacon with the first stored server and pings it for a live status line. */
    private void bindBeaconContent(@NonNull View view) {
        final TextView name = view.findViewById(R.id.lc_beacon_name);
        final TextView address = view.findViewById(R.id.lc_beacon_address);
        final TextView status = view.findViewById(R.id.lc_beacon_status);
        final View dot = view.findViewById(R.id.lc_beacon_dot);
        Context context = getContext();
        if (context == null) return;

        List<ServerStore.Entry> servers = ServerStore.load(context);
        if (servers.isEmpty()) return;
        final ServerStore.Entry featured = servers.get(0);
        if (name != null) name.setText(featured.name);
        if (address != null) address.setText(featured.address);

        PojavApplication.sExecutorService.execute(() -> {
            final ServerPinger.Status result =
                    ServerPinger.ping(featured.host(), featured.port(), 2500);
            Tools.runOnUiThread(() -> {
                if (!isAdded() || status == null) return;
                status.setText(result.online
                        ? getString(R.string.lc_status_online, result.latencyMs)
                        : getString(R.string.lc_status_offline));
                if (dot != null) {
                    dot.setBackgroundResource(result.online
                            ? R.drawable.lc_dot_mint : R.drawable.lc_dot_red);
                }
            });
        });
    }

    /** Opens a screen in the right pane when the two-pane layout is active. */
    private void openChild(Class<? extends Fragment> fragmentClass, String tag) {
        Fragment parent = getParentFragment();
        if (parent instanceof MainMenuFragment) {
            ((MainMenuFragment) parent).openChildPane(fragmentClass, tag, null);
        } else if (getActivity() != null) {
            Tools.swapFragment(requireActivity(), fragmentClass, tag, null);
        }
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
