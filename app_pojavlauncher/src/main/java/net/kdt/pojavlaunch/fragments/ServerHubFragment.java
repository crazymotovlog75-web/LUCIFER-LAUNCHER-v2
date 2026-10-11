package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.liquid.Liquid;
import net.kdt.pojavlaunch.liquid.ServerPinger;
import net.kdt.pojavlaunch.liquid.ServerStore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Liquid Crystal — Server Hub (mockup screen 07).
 *
 * <p>Lists the community servers, pings each one for real (Server List Ping), shows live player
 * counts/latency, lets the user add or remove entries and joins a server by writing it into the
 * selected profile's {@code servers.dat} before launching the game.</p>
 */
public class ServerHubFragment extends Fragment {

    public static final String TAG = "ServerHubFragment";

    private LinearLayout mList;
    private TextView mCount;
    private View mFeatured;
    private int mPendingPings;

    public ServerHubFragment() {
        super(R.layout.fragment_server_hub);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mList = view.findViewById(R.id.lc_server_list);
        mCount = view.findViewById(R.id.lc_servers_count);
        mFeatured = view.findViewById(R.id.lc_featured_card);

        View back = view.findViewById(R.id.lc_hub_back);
        if (back != null) back.setOnClickListener(v -> closeHub());

        View refresh = view.findViewById(R.id.lc_hub_refresh);
        if (refresh != null) refresh.setOnClickListener(v -> refreshAll(true));

        View add = view.findViewById(R.id.lc_hub_add);
        if (add != null) add.setOnClickListener(v -> showAddDialog());

        View viewAll = view.findViewById(R.id.lc_featured_play);
        if (viewAll != null) viewAll.setOnClickListener(v -> joinServer(ServerStore.load(requireContext()).get(0)));

        Liquid.reveal(mFeatured, 0);
        refreshAll(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAll(false);
    }

    private void closeHub() {
        Fragment parent = getParentFragment();
        if (parent instanceof MainMenuFragment && ((MainMenuFragment) parent).isRightPaneActive()) {
            ((MainMenuFragment) parent).popRightPane();
        } else if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        }
    }

    // ── list ────────────────────────────────────────────────────────────────

    private void refreshAll(boolean announce) {
        Context context = getContext();
        if (context == null || mList == null) return;

        List<ServerStore.Entry> entries = ServerStore.load(context);
        mList.removeAllViews();
        mPendingPings = entries.size();

        LayoutInflater inflater = LayoutInflater.from(context);
        for (ServerStore.Entry entry : entries) {
            View row = inflater.inflate(R.layout.item_lc_server, mList, false);
            bindRow(row, entry);
            mList.addView(row);
            Liquid.reveal(row, 30L * mList.getChildCount());
            pingRow(row, entry);
        }

        if (mCount != null) {
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
            mCount.setText(getString(R.string.lc_servers_count, entries.size(), time));
        }

        // featured card mirrors the first entry
        if (mFeatured != null && !entries.isEmpty()) {
            ServerStore.Entry featured = entries.get(0);
            TextView name = mFeatured.findViewById(R.id.lc_featured_name);
            TextView address = mFeatured.findViewById(R.id.lc_featured_address);
            TextView status = mFeatured.findViewById(R.id.lc_featured_status);
            TextView players = mFeatured.findViewById(R.id.lc_featured_players);
            if (name != null) name.setText(featured.name);
            if (address != null) address.setText(featured.address);
            if (status != null) status.setText(R.string.lc_status_checking);
            if (players != null) players.setText("");
            pingFeatured(featured);
        }

        if (announce) {
            Toast.makeText(context, R.string.lc_refresh, Toast.LENGTH_SHORT).show();
        }
    }

    private void bindRow(final View row, final ServerStore.Entry entry) {
        TextView initials = row.findViewById(R.id.lc_srv_initials);
        TextView name = row.findViewById(R.id.lc_srv_name);
        TextView address = row.findViewById(R.id.lc_srv_address);
        TextView kind = row.findViewById(R.id.lc_srv_kind);
        TextView motd = row.findViewById(R.id.lc_srv_motd);
        TextView players = row.findViewById(R.id.lc_srv_players);
        TextView pingText = row.findViewById(R.id.lc_srv_ping_text);

        if (initials != null) initials.setText(initialsOf(entry.name));
        if (name != null) name.setText(entry.name);
        if (address != null) address.setText(entry.address);
        if (kind != null) kind.setText(entry.featured ? R.string.lc_server_featured : R.string.lc_server_entry);
        if (motd != null) motd.setText(R.string.lc_status_checking);
        if (players != null) players.setText("—");
        if (pingText != null) pingText.setText("…");
        setSignal(row, 0);

        View play = row.findViewById(R.id.lc_srv_play);
        if (play != null) play.setOnClickListener(v -> joinServer(entry));

        View refresh = row.findViewById(R.id.lc_srv_refresh);
        if (refresh != null) refresh.setOnClickListener(v -> pingRow(row, entry));

        View remove = row.findViewById(R.id.lc_srv_remove);
        if (remove != null) {
            remove.setOnClickListener(v -> {
                ServerStore.remove(requireContext(), entry);
                Toast.makeText(requireContext(),
                        getString(R.string.lc_server_removed, entry.name), Toast.LENGTH_SHORT).show();
                refreshAll(false);
            });
        }
    }

    private void pingFeatured(final ServerStore.Entry entry) {
        if (mFeatured == null) return;
        final Context appContext = requireContext().getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            final ServerPinger.Status status =
                    ServerPinger.ping(entry.host(), entry.port(), 2500);
            Tools.runOnUiThread(() -> {
                if (!isAdded() || mFeatured == null) return;
                TextView statusView = mFeatured.findViewById(R.id.lc_featured_status);
                TextView players = mFeatured.findViewById(R.id.lc_featured_players);
                View dot = mFeatured.findViewById(R.id.lc_featured_dot);
                if (statusView != null) {
                    statusView.setText(status.online
                            ? getString(R.string.lc_status_online, status.latencyMs)
                            : getString(R.string.lc_status_offline));
                }
                if (players != null) {
                    players.setText(status.online
                            ? getString(R.string.lc_server_players, status.players, status.maxPlayers)
                            : "");
                }
                if (dot != null) {
                    dot.setBackgroundResource(status.online
                            ? R.drawable.lc_dot_mint : R.drawable.lc_dot_red);
                }
                if (!status.motd.isEmpty()) {
                    TextView statusLine = mFeatured.findViewById(R.id.lc_featured_motd);
                    if (statusLine != null) statusLine.setText(status.motd);
                }
            });
        });
    }

    private void pingRow(final View row, final ServerStore.Entry entry) {
        TextView pingText = row.findViewById(R.id.lc_srv_ping_text);
        if (pingText != null) pingText.setText("…");
        PojavApplication.sExecutorService.execute(() -> {
            final ServerPinger.Status status = ServerPinger.ping(entry.host(), entry.port(), 2500);
            Tools.runOnUiThread(() -> {
                if (!isAdded() || row.getParent() == null) return;
                TextView motd = row.findViewById(R.id.lc_srv_motd);
                TextView players = row.findViewById(R.id.lc_srv_players);
                TextView ping = row.findViewById(R.id.lc_srv_ping_text);
                View dot = row.findViewById(R.id.lc_srv_dot);

                if (dot != null) {
                    dot.setBackgroundResource(!status.online ? R.drawable.lc_dot_red
                            : status.latencyMs > 300 ? R.drawable.lc_dot_amber
                            : R.drawable.lc_dot_mint);
                }
                if (ping != null) {
                    ping.setText(status.online ? (status.latencyMs + " ms") : getString(R.string.lc_status_offline));
                }
                if (players != null) {
                    players.setText(status.online
                            ? getString(R.string.lc_server_players, status.players, status.maxPlayers)
                            : "—");
                }
                if (motd != null) {
                    if (!status.online) {
                        motd.setText(getString(R.string.lc_maintenance));
                        motd.setTextColor(0xFFFF6B6B);
                    } else {
                        motd.setText(status.motd.isEmpty() ? status.version : status.motd);
                        motd.setTextColor(0xFF9AA6C4);
                    }
                }
                setSignal(row, signalFor(status.latencyMs, status.online));
            });
        });
        if (mPendingPings > 0) mPendingPings--;
    }

    private static final int[] SIGNAL_BARS = {
            R.id.lc_srv_ping_1, R.id.lc_srv_ping_2, R.id.lc_srv_ping_3,
            R.id.lc_srv_ping_4, R.id.lc_srv_ping_5
    };

    private void setSignal(View row, int bars) {
        for (int i = 0; i < SIGNAL_BARS.length; i++) {
            View bar = row.findViewById(SIGNAL_BARS[i]);
            if (bar == null) continue;
            bar.setBackgroundResource(i < bars ? R.drawable.lc_dot_mint : R.drawable.lc_dot_dim);
        }
    }

    private int signalFor(long latencyMs, boolean online) {
        if (!online) return 0;
        if (latencyMs < 0) return 3;
        if (latencyMs < 60) return 5;
        if (latencyMs < 120) return 4;
        if (latencyMs < 250) return 3;
        if (latencyMs < 500) return 2;
        return 1;
    }

    private String initialsOf(String name) {
        if (name == null || name.trim().isEmpty()) return "??";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(Locale.ROOT);
        }
        return name.substring(0, Math.min(2, name.length())).toUpperCase(Locale.ROOT);
    }

    // ── actions ─────────────────────────────────────────────────────────────

    private void showAddDialog() {
        Context context = getContext();
        if (context == null) return;
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (getResources().getDisplayMetrics().density * 18);
        box.setPadding(pad, pad / 2, pad, 0);

        final EditText nameField = new EditText(context);
        nameField.setHint(R.string.lc_server_name_hint);
        nameField.setTextColor(0xFFEAF0FF);
        box.addView(nameField);

        final EditText addrField = new EditText(context);
        addrField.setHint(R.string.lc_server_addr_hint);
        addrField.setTextColor(0xFFEAF0FF);
        box.addView(addrField);

        new AlertDialog.Builder(context)
                .setTitle(R.string.lc_server_dialog_title)
                .setView(box)
                .setPositiveButton(R.string.lc_add_server, (dialog, which) -> {
                    String address = addrField.getText().toString().trim();
                    String name = nameField.getText().toString().trim();
                    if (address.isEmpty() || !address.contains(".")) {
                        Toast.makeText(context, R.string.lc_server_invalid, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (name.isEmpty()) name = address;
                    ServerStore.add(context, name, address);
                    Toast.makeText(context,
                            getString(R.string.lc_server_added, name), Toast.LENGTH_SHORT).show();
                    refreshAll(false);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void joinServer(ServerStore.Entry entry) {
        if (entry == null) return;
        Context context = getContext();
        if (context == null) return;

        File gameDir = currentProfileDir();
        if (gameDir == null) {
            Toast.makeText(context, R.string.lc_no_profiles, Toast.LENGTH_LONG).show();
            return;
        }
        boolean ok = ServerStore.writeServersDat(gameDir, ServerStore.load(context));
        Toast.makeText(context,
                ok ? getString(R.string.lc_server_join, entry.name) : getString(R.string.lc_server_invalid),
                Toast.LENGTH_SHORT).show();
        if (ok) ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
    }

    @Nullable
    private File currentProfileDir() {
        try {
            String key = LauncherPreferences.DEFAULT_PREF
                    .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
            LauncherProfiles.load();
            if (LauncherProfiles.mainProfileJson == null
                    || LauncherProfiles.mainProfileJson.profiles == null) return null;
            MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(key);
            if (profile == null) return null;
            return Tools.getGameDirPath(profile);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Kept for parity with the desktop-style "view all" affordance. */
    @SuppressWarnings("unused")
    private void openDiscord() {
        if (getActivity() != null) {
            Tools.openURL(getActivity(), getString(R.string.discord_invite));
        }
    }
}
