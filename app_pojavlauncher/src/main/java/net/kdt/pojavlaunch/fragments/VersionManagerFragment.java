package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.liquid.Liquid;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Liquid Crystal — Version Manager (mockup screen 04).
 *
 * Shows the real Mojang version table that the launcher already keeps in
 * {@link ExtraConstants#RELEASE_TABLE}, lets the user filter it, shows the release
 * details of the selected version and starts the exact same install flows the old
 * profile-type screen used (no feature was dropped, only re-homed).
 */
public class VersionManagerFragment extends Fragment {
    public static final String TAG = "LC_VERSION_MANAGER";

    /** Loader chips → the fragment that already knows how to install that loader. */
    private static final String[] CHIP_TAGS = {"all", "vanilla", "forge", "neoforge",
            "fabric", "quilt", "optifine", "bta"};

    private LinearLayout mList;
    private TextView mChipAll, mChipVanilla, mChipForge, mChipNeoForge, mChipFabric,
            mChipQuilt, mChipOptiFine, mChipBta;
    private TextView mDetailVersion, mDetailMeta;
    private View mDetailInstall;
    private String mSelectedVersion;
    private boolean mShowingLoader;
    private String mFilter = "all";

    public VersionManagerFragment() {
        super(R.layout.fragment_version_manager);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mList = view.findViewById(R.id.lc_version_list);
        mChipAll = view.findViewById(R.id.lc_chip_all);
        mChipVanilla = view.findViewById(R.id.lc_chip_vanilla);
        mChipForge = view.findViewById(R.id.lc_chip_forge);
        mChipNeoForge = view.findViewById(R.id.lc_chip_neoforge);
        mChipFabric = view.findViewById(R.id.lc_chip_fabric);
        mChipQuilt = view.findViewById(R.id.lc_chip_quilt);
        mChipOptiFine = view.findViewById(R.id.lc_chip_optifine);
        mChipBta = view.findViewById(R.id.lc_chip_bta);
        mDetailVersion = view.findViewById(R.id.lc_detail_version);
        mDetailMeta = view.findViewById(R.id.lc_detail_meta);
        mDetailInstall = view.findViewById(R.id.lc_detail_install);

        wireChip(mChipAll, "all");
        wireChip(mChipVanilla, "vanilla");
        wireChip(mChipForge, "forge");
        wireChip(mChipNeoForge, "neoforge");
        wireChip(mChipFabric, "fabric");
        wireChip(mChipQuilt, "quilt");
        wireChip(mChipOptiFine, "optifine");
        wireChip(mChipBta, "bta");

        View search = view.findViewById(R.id.lc_vm_search);
        if (search != null) {
            Liquid.pressFeedback(search);
            search.setOnClickListener(v -> Toast.makeText(requireContext(),
                    R.string.lc_search, Toast.LENGTH_SHORT).show());
        }

        bindTiles(view);

        mDetailInstall.setOnClickListener(v -> installSelection());
        setDetail(null);

        Liquid.reveal(view.findViewById(R.id.lc_version_detail), 80L);
        loadVersions();
    }

    // ── data ────────────────────────────────────────────────────────────────

    private void loadVersions() {
        Object cached = ExtraCore.getValue(ExtraConstants.RELEASE_TABLE);
        if (cached instanceof JMinecraftVersionList) {
            renderVersions((JMinecraftVersionList) cached);
            return;
        }
        mDetailVersion.setText(R.string.lc_status_checking);
        new AsyncVersionList().getVersionList(versions -> {
            ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versions);
            Tools.runOnUiThread(() -> {
                if (isAdded() && versions != null) renderVersions(versions);
            });
        }, false);
    }

    private void renderVersions(JMinecraftVersionList table) {
        if (mList == null) return;
        mList.removeAllViews();

        JMinecraftVersionList.Version[] versions = table.versions;
        if (versions == null) versions = new JMinecraftVersionList.Version[0];

        int shown = 0;
        for (JMinecraftVersionList.Version version : versions) {
            if (version == null || version.id == null) continue;
            if (!passesFilter(version)) continue;
            mList.addView(buildVersionRow(version));
            shown++;
            if (shown >= 40) break;
        }

        if (shown == 0) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.lc_no_version);
            empty.setTextColor(getResources().getColor(R.color.lc_text_dim));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 40, 0, 40);
            mList.addView(empty);
        }
    }

    private boolean passesFilter(JMinecraftVersionList.Version version) {
        if ("all".equals(mFilter)) return true;
        if ("vanilla".equals(mFilter)) return "release".equals(version.type);
        return true;
    }

    private View buildVersionRow(JMinecraftVersionList.Version version) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.lc_ripple_row);
        row.setClickable(true);
        row.setFocusable(true);
        int pad = dp(12);
        row.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        row.setLayoutParams(params);

        TextView id = new TextView(requireContext());
        id.setText(version.id);
        id.setTextColor(getResources().getColor(R.color.lc_text));
        id.setTextSize(16f);
        LinearLayout.LayoutParams idParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        id.setLayoutParams(idParams);
        id.setTypeface(id.getTypeface(), android.graphics.Typeface.BOLD);
        row.addView(id);

        boolean installed = isInstalled(version.id);
        TextView badge = new TextView(requireContext());
        badge.setText(installed ? R.string.lc_installed : version.type);
        badge.setTextSize(10f);
        badge.setBackgroundResource(installed ? R.drawable.lc_badge_mint : R.drawable.lc_badge_local);
        badge.setTextColor(getResources().getColor(installed ? R.color.lc_mint : R.color.lc_text_dim));
        int bpad = dp(8);
        badge.setPadding(bpad, dp(3), bpad, dp(3));
        row.addView(badge);

        row.setOnClickListener(v -> {
            Liquid.pressFeedback(v);
            setDetail(version);
        });
        return row;
    }

    private boolean isInstalled(String versionId) {
        if (LauncherProfiles.mainProfileJson == null
                || LauncherProfiles.mainProfileJson.profiles == null) return false;
        for (Map.Entry<String, MinecraftProfile> entry
                : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
            MinecraftProfile profile = entry.getValue();
            if (profile == null || profile.lastVersionId == null) continue;
            String id = profile.lastVersionId;
            if (id.equals(versionId) || id.startsWith(versionId + "-")
                    || id.contains(versionId + "-")) return true;
        }
        return false;
    }

    private void setDetail(@Nullable JMinecraftVersionList.Version version) {
        mShowingLoader = false;
        if (version == null) {
            mSelectedVersion = null;
            mDetailVersion.setText(R.string.lc_version_manager_title);
            mDetailMeta.setText(R.string.lc_version_manager_sub);
            return;
        }
        mSelectedVersion = version.id;
        mDetailVersion.setText(version.id);
        String size = version.size > 0
                ? String.format(Locale.US, "%.0f MB", version.size / 1048576f)
                : "-";
        String date = version.releaseTime;
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            SimpleDateFormat out = new SimpleDateFormat("MMM d, yyyy", Locale.US);
            date = out.format(in.parse(version.releaseTime));
        } catch (Exception ignored) {
            // keep the raw timestamp when the format is unexpected
        }
        mDetailMeta.setText(getString(R.string.lc_release_prefix, date) + "   ·   "
                + getString(R.string.lc_size_mb, size));
        Liquid.reveal(mDetailInstall, 60L);
    }

    // ── loaders ─────────────────────────────────────────────────────────────

    private void wireChip(TextView chip, String tag) {
        if (chip == null) return;
        Liquid.pressFeedback(chip);
        chip.setOnClickListener(v -> {
            mFilter = tag;
            if (isLoaderTag(tag)) {
                showLoaderCard(tag);
            } else {
                loadVersions();
            }
            highLightChip(chip);
        });
    }

    private boolean isLoaderTag(String tag) {
        return !"all".equals(tag) && !"vanilla".equals(tag);
    }

    private void highLightChip(TextView active) {
        TextView[] chips = {mChipAll, mChipVanilla, mChipForge, mChipNeoForge,
                mChipFabric, mChipQuilt, mChipOptiFine, mChipBta};
        for (TextView chip : chips) {
            if (chip == null) continue;
            boolean isActive = chip == active;
            chip.setBackgroundResource(isActive ? R.drawable.lc_chip_active : R.drawable.lc_chip);
            chip.setTextColor(getResources().getColor(
                    isActive ? R.color.lc_cyan : R.color.lc_text));
        }
    }

    /** A loader chip replaces the list with the loader installer card, exactly like the mockup. */
    private void showLoaderCard(String tag) {
        if (mList == null) return;
        mList.removeAllViews();
        mShowingLoader = true;

        mDetailVersion.setText(loaderLabel(tag));
        mDetailMeta.setText(R.string.lc_version_manager_sub);

        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.lc_glass_card_active);
        int pad = dp(14);
        card.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(requireContext());
        title.setText(loaderLabel(tag));
        title.setTextColor(getResources().getColor(R.color.lc_text));
        title.setTextSize(18f);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        card.addView(title);

        TextView info = new TextView(requireContext());
        info.setText(loaderSubtitle(tag));
        info.setTextColor(getResources().getColor(R.color.lc_text_dim));
        info.setTextSize(12f);
        card.addView(info);

        TextView install = new TextView(requireContext());
        install.setText(R.string.lc_install);
        install.setGravity(Gravity.CENTER);
        install.setTextColor(0xFF06121A);
        install.setTextSize(14f);
        install.setTypeface(install.getTypeface(), android.graphics.Typeface.BOLD);
        install.setBackgroundResource(R.drawable.lc_btn_liquid);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        ip.topMargin = dp(12);
        install.setLayoutParams(ip);
        install.setOnClickListener(v -> openLoader(tag));
        card.addView(install);

        mList.addView(card);
        Liquid.reveal(card, 60L);
    }

    private void bindTiles(View view) {
        wireTile(view.findViewById(R.id.lc_tile_forge), "forge");
        wireTile(view.findViewById(R.id.lc_tile_neoforge), "neoforge");
        wireTile(view.findViewById(R.id.lc_tile_fabric), "fabric");
        wireTile(view.findViewById(R.id.lc_tile_quilt), "quilt");
        wireTile(view.findViewById(R.id.lc_tile_optifine), "optifine");
        wireTile(view.findViewById(R.id.lc_tile_bta), "bta");
    }

    private void wireTile(View tile, String tag) {
        if (tile == null) return;
        Liquid.pressFeedback(tile);
        tile.setOnClickListener(v -> openLoader(tag));
    }

    private String loaderLabel(String tag) {
        switch (tag) {
            case "forge": return getString(R.string.lc_loader_forge);
            case "neoforge": return getString(R.string.lc_loader_neoforge);
            case "fabric": return getString(R.string.lc_loader_fabric);
            case "quilt": return getString(R.string.lc_loader_quilt);
            case "optifine": return getString(R.string.lc_loader_optifine);
            case "bta": return getString(R.string.lc_loader_bta);
            default: return getString(R.string.lc_filter_vanilla);
        }
    }

    private String loaderSubtitle(String tag) {
        switch (tag) {
            case "forge": return "Minecraft Forge";
            case "neoforge": return "NeoForge";
            case "fabric": return "Fabric";
            case "quilt": return "Quilt";
            case "optifine": return "OptiFine";
            case "bta": return "Better than Adventure";
            default: return "";
        }
    }

    /**
     * Every loader opens the installer fragment that already handled it before this redesign,
     * so nothing about the install path changed — only the screen it is started from.
     */
    private void openLoader(String tag) {
        if (!Tools.hasOnlineProfile()) {
            Tools.hasNoOnlineProfileDialog(requireActivity());
            return;
        }
        switch (tag) {
            case "forge":
                navigate(ForgeInstallFragment.class, ForgeInstallFragment.TAG);
                break;
            case "neoforge":
                navigate(NeoForgeInstallFragment.class, NeoForgeInstallFragment.TAG);
                break;
            case "fabric":
                navigate(FabricInstallFragment.class, FabricInstallFragment.TAG);
                break;
            case "quilt":
                navigate(QuiltInstallFragment.class, QuiltInstallFragment.TAG);
                break;
            case "optifine":
                navigate(OptiFineInstallFragment.class, OptiFineInstallFragment.TAG);
                break;
            case "bta":
                navigate(BTAInstallFragment.class, BTAInstallFragment.TAG);
                break;
            default:
                navigate(ProfileEditorFragment.class, ProfileEditorFragment.TAG);
                break;
        }
    }

    private void installSelection() {
        if (mShowingLoader) {
            openLoader(mFilter);
            return;
        }
        if (mSelectedVersion == null) {
            Toast.makeText(requireContext(), R.string.lc_account_select_first,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        // Vanilla installs happen through the profile editor, which is where a new
        // instance is created and its version downloaded.
        navigate(ProfileEditorFragment.class, ProfileEditorFragment.TAG);
    }

    /** Right pane in landscape, full screen otherwise — same rule as the rest of the redesign. */
    private void navigate(Class<? extends Fragment> fragmentClass, String tag) {
        Fragment parent = getParentFragment();
        while (parent != null && !(parent instanceof MainMenuFragment)) {
            parent = parent.getParentFragment();
        }
        if (parent instanceof MainMenuFragment) {
            ((MainMenuFragment) parent).openChildPane(fragmentClass, tag, null);
        } else {
            Tools.swapFragment(requireActivity(), fragmentClass, tag, null);
        }
    }

    private int dp(int value) {
        return (int) (getResources().getDisplayMetrics().density * value);
    }
}
