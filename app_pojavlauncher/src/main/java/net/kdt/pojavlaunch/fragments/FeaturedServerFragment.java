package net.kdt.pojavlaunch.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.MinecraftServerPing;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.QuickPlayManager;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

/** Full-screen, one-tap join page for the LuciferMC Lifesteel SMP. */
public class FeaturedServerFragment extends Fragment {
    public static final String TAG = "FeaturedServerFragment";
    private static final String SERVER_ADDRESS = "play.lucifermc.fun";

    @Nullable private TextView mOnlineBadge;
    private int mPingGeneration;

    public FeaturedServerFragment() {
        super(R.layout.fragment_featured_server);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mOnlineBadge = view.findViewById(R.id.featured_server_status);
        view.findViewById(R.id.btn_featured_server_play).setOnClickListener(v -> launchServer());
        view.findViewById(R.id.btn_featured_server_discord).setOnClickListener(v ->
                Tools.openDiscordInvite(requireActivity(), getString(R.string.discord_invite)));
        showStatus(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    public void onDestroyView() {
        mPingGeneration++;
        mOnlineBadge = null;
        super.onDestroyView();
    }

    private void refreshStatus() {
        final int request = ++mPingGeneration;
        PojavApplication.sExecutorService.execute(() -> {
            boolean online;
            try {
                MinecraftServerPing.ping(SERVER_ADDRESS);
                online = true;
            } catch (Exception ignored) {
                online = false;
            }
            final boolean result = online;
            Tools.runOnUiThread(() -> {
                if (!isAdded() || request != mPingGeneration) return;
                showStatus(result);
            });
        });
    }

    private void showStatus(boolean online) {
        if (mOnlineBadge == null) return;
        mOnlineBadge.setText(online ? "ONLINE" : "OFFLINE");
        mOnlineBadge.setTextColor(Color.parseColor(online ? "#82F59A" : "#FF8790"));
        mOnlineBadge.setBackgroundResource(online
                ? R.drawable.bg_server_badge_online
                : R.drawable.bg_server_badge_offline);
        mOnlineBadge.setContentDescription(online
                ? "LuciferMC server is online"
                : "LuciferMC server is offline or not responding");
    }

    private void launchServer() {
        LauncherProfiles.loadAsync(() -> {
            if (!isAdded()) return;
            String profileKey = LauncherPreferences.DEFAULT_PREF.getString(
                    LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
            MinecraftProfile profile = LauncherProfiles.mainProfileJson != null &&
                    LauncherProfiles.mainProfileJson.profiles != null
                    ? LauncherProfiles.mainProfileJson.profiles.get(profileKey) : null;
            if (profile == null || profile.lastVersionId == null) {
                Toast.makeText(requireContext(), R.string.error_no_version, Toast.LENGTH_LONG).show();
                return;
            }
            if (!QuickPlayManager.request(SERVER_ADDRESS)) {
                Toast.makeText(requireContext(), "Could not prepare the server connection", Toast.LENGTH_LONG).show();
                return;
            }
            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        });
    }
}
