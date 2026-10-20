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

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/** Full-screen, one-tap join page for the LuciferMC Lifesteel SMP. */
public class FeaturedServerFragment extends Fragment {
    public static final String TAG = "FeaturedServerFragment";
    private static final String SERVER_ADDRESS = "play.lucifermc.fun";

    @Nullable private TextView mOnlineBadge;
    private int mPingGeneration;
    private boolean mPingInFlight;

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
        showStatus(Status.CHECKING);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    public void onDestroyView() {
        mPingGeneration++;
        mPingInFlight = false;
        mOnlineBadge = null;
        super.onDestroyView();
    }

    private void refreshStatus() {
        if (mPingInFlight || mOnlineBadge == null) return;
        final int request = ++mPingGeneration;
        mPingInFlight = true;
        showStatus(Status.CHECKING);
        PojavApplication.sExecutorService.execute(() -> {
            Status result;
            try {
                MinecraftServerPing.ping(SERVER_ADDRESS);
                result = Status.ONLINE;
            } catch (Exception error) {
                if (error instanceof SocketTimeoutException || error instanceof ConnectException) {
                    result = Status.OFFLINE;
                } else if (error instanceof UnknownHostException) {
                    result = Status.UNKNOWN;
                } else {
                    result = Status.UNKNOWN;
                }
            }
            final Status finalResult = result;
            Tools.runOnUiThread(() -> {
                if (request == mPingGeneration) mPingInFlight = false;
                if (!isAdded() || request != mPingGeneration) return;
                showStatus(finalResult);
            });
        });
    }

    private void showStatus(Status status) {
        if (mOnlineBadge == null) return;
        switch (status) {
            case ONLINE:
                mOnlineBadge.setText("ONLINE");
                mOnlineBadge.setTextColor(Color.parseColor("#82F59A"));
                mOnlineBadge.setBackgroundResource(R.drawable.bg_server_badge_online);
                mOnlineBadge.setContentDescription("LuciferMC server responded online to a live ping");
                break;
            case OFFLINE:
                mOnlineBadge.setText("OFFLINE");
                mOnlineBadge.setTextColor(Color.parseColor("#FF8790"));
                mOnlineBadge.setBackgroundResource(R.drawable.bg_server_badge_offline);
                mOnlineBadge.setContentDescription("LuciferMC server did not respond to a live ping");
                break;
            case UNKNOWN:
                mOnlineBadge.setText("UNKNOWN");
                mOnlineBadge.setTextColor(Color.parseColor("#C7CED8"));
                mOnlineBadge.setBackgroundResource(R.drawable.bg_server_badge_unknown);
                mOnlineBadge.setContentDescription("LuciferMC server status could not be determined");
                break;
            default:
                mOnlineBadge.setText("CHECKING");
                mOnlineBadge.setTextColor(Color.parseColor("#FFE59A"));
                mOnlineBadge.setBackgroundResource(R.drawable.bg_server_badge_checking);
                mOnlineBadge.setContentDescription("Checking LuciferMC server status");
        }
    }

    private void launchServer() {
        LauncherProfiles.loadAsync(() -> {
            if (!isAdded()) return;
            String profileKey = LauncherPreferences.DEFAULT_PREF.getString(
                    LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
            MinecraftProfile profile = LauncherProfiles.mainProfileJson != null &&
                    LauncherProfiles.mainProfileJson.profiles != null
                    ? LauncherProfiles.mainProfileJson.profiles.get(profileKey) : null;
            if (profile == null || profile.lastVersionId == null || profile.lastVersionId.trim().isEmpty() ||
                    "Unknown".equalsIgnoreCase(profile.lastVersionId)) {
                Toast.makeText(requireContext(), R.string.please_select_version, Toast.LENGTH_LONG).show();
                return;
            }
            if (!QuickPlayManager.request(SERVER_ADDRESS)) {
                Toast.makeText(requireContext(), "Could not prepare the server connection", Toast.LENGTH_LONG).show();
                return;
            }
            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        });
    }

    private enum Status { CHECKING, ONLINE, OFFLINE, UNKNOWN }
}
