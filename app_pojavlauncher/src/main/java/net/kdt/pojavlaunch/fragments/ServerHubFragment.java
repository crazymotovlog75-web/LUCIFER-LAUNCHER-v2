package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.MinecraftServerPing;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.QuickPlayManager;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.MinecraftServerListStore;
import net.kdt.pojavlaunch.utils.MinecraftTextFormatter;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/** Saved Java server hub with real status pings and profile-aware Minecraft server-list sync. */
public class ServerHubFragment extends Fragment {
    public static final String TAG = "ServerHubFragment";
    private static final String PREF_SERVERS = "lucifer_server_hub_list";
    private static final String PREF_MANAGED_SERVERDAT_PREFIX = "lucifer_managed_serverdat_";
    private static final String FEATURED_SERVER_ADDRESS = "play.lucifermc.fun";
    private static final int MAX_SERVERS = 40;
    private static final AtomicInteger SERVERDAT_GENERATION = new AtomicInteger();
    private static final Object SERVERDAT_SYNC_LOCK = new Object();

    private RecyclerView mRecyclerView;
    private View mEmptyState;
    private TextView mFeaturedStatusView;
    private TextView mFeaturedPlayersView;
    private TextView mFeaturedPingView;
    private TextView mFeaturedMotdView;
    private TextView mServerCountView;
    private TextView mOtherServersHeader;
    private ServerAdapter mAdapter;
    private ExecutorService mPingExecutor;
    private ServerStatus mFeaturedStatus = ServerStatus.checking();
    private int mFeaturedPingRequestId;
    private boolean mFeaturedPingInFlight;
    private final ArrayList<ServerEntry> mServers = new ArrayList<>();
    private final Map<String, ServerStatus> mStatuses = new HashMap<>();
    private final Map<String, Integer> mPingRequestIds = new HashMap<>();
    private final Map<String, Integer> mPingInFlight = new HashMap<>();
    private int mNextPingRequestId = 1;

    public ServerHubFragment() {
        super(R.layout.fragment_server_hub);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mPingExecutor = Executors.newFixedThreadPool(3, runnable -> {
            Thread thread = new Thread(runnable, "ServerHub-Ping");
            thread.setDaemon(true);
            return thread;
        });
        mRecyclerView = view.findViewById(R.id.server_hub_list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setItemAnimator(null);
        mEmptyState = view.findViewById(R.id.server_hub_empty);
        mFeaturedStatusView = view.findViewById(R.id.featured_server_status);
        mFeaturedPlayersView = view.findViewById(R.id.featured_server_players);
        mFeaturedPingView = view.findViewById(R.id.featured_server_ping);
        mFeaturedMotdView = view.findViewById(R.id.featured_server_motd);
        mServerCountView = view.findViewById(R.id.server_hub_count);
        mOtherServersHeader = view.findViewById(R.id.server_hub_other_servers_title);
        mAdapter = new ServerAdapter();
        mRecyclerView.setAdapter(mAdapter);

        view.findViewById(R.id.server_hub_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        view.findViewById(R.id.btn_server_add).setOnClickListener(v -> showServerDialog(null));
        view.findViewById(R.id.btn_server_refresh_all).setOnClickListener(v -> refreshAll());
        view.findViewById(R.id.btn_featured_server_play).setOnClickListener(v ->
                quickPlay(FEATURED_SERVER_ADDRESS));
        view.findViewById(R.id.btn_featured_server_discord).setOnClickListener(v ->
                openWebOrDiscord(getString(R.string.discord_invite), true));
        view.findViewById(R.id.featured_server_community).setOnClickListener(v ->
                openWebOrDiscord(getString(R.string.discord_invite), true));

        loadServers();
        mFeaturedStatus = ServerStatus.checking();
        mStatuses.clear();
        renderFeaturedStatus(mFeaturedStatus);
        updateEmptyState();
        updateServerCount();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAll();
        syncServersDatAsync(false);
    }

    @Override
    public void onDestroyView() {
        mFeaturedPingRequestId++;
        mFeaturedPingInFlight = false;
        mPingRequestIds.clear();
        mPingInFlight.clear();
        mStatuses.clear();
        mFeaturedStatus = ServerStatus.checking();
        if (mPingExecutor != null) {
            mPingExecutor.shutdownNow();
            mPingExecutor = null;
        }
        if (mRecyclerView != null) mRecyclerView.setAdapter(null);
        mRecyclerView = null;
        mEmptyState = null;
        mFeaturedStatusView = null;
        mFeaturedPlayersView = null;
        mFeaturedPingView = null;
        mFeaturedMotdView = null;
        mServerCountView = null;
        mOtherServersHeader = null;
        mAdapter = null;
        super.onDestroyView();
    }

    private SharedPreferences preferences() {
        // Preserve the existing preference file and list key for launcher upgrades.
        return requireContext().getSharedPreferences("cslauncher_settings", Context.MODE_PRIVATE);
    }

    private void loadServers() {
        mServers.clear();
        Set<String> ids = new HashSet<>();
        Set<String> endpointKeys = new HashSet<>();
        try {
            JSONArray array = new JSONArray(preferences().getString(PREF_SERVERS, "[]"));
            for (int i = 0; i < array.length() && mServers.size() < MAX_SERVERS; i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String name = item.optString("name", "").trim();
                String rawAddress = item.optString("address", "").trim();
                if (name.isEmpty() || name.length() > 64 || rawAddress.isEmpty()) continue;
                final MinecraftServerPing.Endpoint endpoint;
                try {
                    endpoint = MinecraftServerPing.parseEndpoint(rawAddress);
                } catch (IllegalArgumentException invalid) {
                    continue;
                }
                if (!endpointKeys.add(endpoint.normalizedKey()) ||
                        endpoint.normalizedKey().equals(MinecraftServerPing.parseEndpoint(FEATURED_SERVER_ADDRESS).normalizedKey())) {
                    continue;
                }
                String id = item.optString("id", "").trim();
                if (id.isEmpty() || !ids.add(id)) {
                    do { id = UUID.randomUUID().toString(); } while (!ids.add(id));
                }
                String discordUrl = "";
                try {
                    discordUrl = normalizeServerLink(item.optString("discordUrl", ""));
                } catch (IllegalArgumentException ignored) {
                    // Ignore stale or invalid optional links without losing the saved server.
                }
                mServers.add(new ServerEntry(id, name, endpoint.toAddress(), discordUrl));
            }
        } catch (Exception error) {
            Log.w(TAG, "Saved server-hub data could not be read; showing an empty custom list", error);
            mServers.clear();
        }
        if (mAdapter != null) mAdapter.notifyDataSetChanged();
    }

    private void saveServers() {
        JSONArray array = new JSONArray();
        for (ServerEntry entry : mServers) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", entry.id);
                item.put("name", entry.name);
                item.put("address", entry.address);
                item.put("discordUrl", entry.discordUrl);
                array.put(item);
            } catch (JSONException error) {
                Log.w(TAG, "Could not serialize a saved server", error);
            }
        }
        preferences().edit().putString(PREF_SERVERS, array.toString()).apply();
        updateEmptyState();
        updateServerCount();
    }

    private void updateEmptyState() {
        if (mEmptyState == null || mRecyclerView == null) return;
        mEmptyState.setVisibility(mServers.isEmpty() ? View.VISIBLE : View.GONE);
        mRecyclerView.setVisibility(View.VISIBLE);
        if (mOtherServersHeader != null) {
            mOtherServersHeader.setText(mServers.isEmpty()
                    ? "OTHER SERVERS"
                    : "OTHER SERVERS · " + mServers.size());
        }
    }

    private void updateServerCount() {
        if (mServerCountView == null) return;
        int total = mServers.size() + 1; // LuciferMC is permanently pinned.
        int online = mFeaturedStatus.state == ServerState.ONLINE ? 1 : 0;
        int checking = mFeaturedStatus.state == ServerState.CHECKING ? 1 : 0;
        for (ServerEntry entry : mServers) {
            ServerStatus status = mStatuses.get(entry.id);
            if (status == null || status.state == ServerState.CHECKING) checking++;
            else if (status.state == ServerState.ONLINE) online++;
        }
        String count = total == 1 ? "1 server" : total + " servers";
        String summary = checking > 0
                ? online + " online · checking " + checking
                : online + "/" + total + " online";
        mServerCountView.setText(count + " · " + summary);
    }

    private void renderFeaturedStatus(ServerStatus status) {
        if (mFeaturedStatusView != null) {
            switch (status.state) {
                case ONLINE:
                    setBadge(mFeaturedStatusView, "ONLINE", "#82F59A", R.drawable.bg_server_badge_online,
                            "LuciferMC responded online to a live ping");
                    break;
                case OFFLINE:
                    setBadge(mFeaturedStatusView, "OFFLINE", "#FF8790", R.drawable.bg_server_badge_offline,
                            "LuciferMC did not respond to a live ping");
                    break;
                case UNKNOWN:
                    setBadge(mFeaturedStatusView, "UNKNOWN", "#C7CED8", R.drawable.bg_server_badge_unknown,
                            "LuciferMC status could not be determined");
                    break;
                default:
                    setBadge(mFeaturedStatusView, "CHECKING", "#FFE59A", R.drawable.bg_server_badge_checking,
                            "Checking LuciferMC server status");
            }
        }
        if (mFeaturedPlayersView == null || mFeaturedPingView == null || mFeaturedMotdView == null) return;
        if (status.state == ServerState.CHECKING) {
            mFeaturedPlayersView.setText("Players: checking…");
            mFeaturedPingView.setText("Ping: checking…");
            mFeaturedMotdView.setText("Loading MOTD…");
        } else if (status.ping != null) {
            mFeaturedPlayersView.setText(formatPlayers(status.ping));
            mFeaturedPingView.setText(formatPing(status.ping));
            mFeaturedMotdView.setText(MinecraftTextFormatter.format(status.ping.motd));
        } else {
            mFeaturedPlayersView.setText("Players unavailable");
            mFeaturedPingView.setText("Ping unavailable");
            mFeaturedMotdView.setText("MOTD unavailable");
        }
    }

    private static void setBadge(TextView view, String label, String color, int background, String description) {
        view.setText(label);
        view.setTextColor(android.graphics.Color.parseColor(color));
        view.setBackgroundResource(background);
        view.setContentDescription(description);
    }

    private static String formatPlayers(MinecraftServerPing.PingResult ping) {
        if (ping.onlinePlayers >= 0 && ping.maximumPlayers >= 0) {
            return "Players: " + ping.onlinePlayers + "/" + ping.maximumPlayers;
        }
        if (ping.onlinePlayers >= 0) return "Players online: " + ping.onlinePlayers;
        return "Player count unavailable";
    }

    private static String formatPing(MinecraftServerPing.PingResult ping) {
        return ping.latencyMs >= 0 ? "Ping: " + ping.latencyMs + " ms" : "Ping unavailable";
    }

    private void executePing(Runnable task) {
        ExecutorService executor = mPingExecutor;
        if (executor == null || executor.isShutdown()) return;
        try {
            executor.execute(task);
        } catch (RejectedExecutionException rejected) {
            Log.d(TAG, "Ping task was rejected because the view is closing");
        }
    }

    private void pingFeaturedServer() {
        if (mFeaturedPingInFlight || mFeaturedStatusView == null) return;
        final int requestId = ++mFeaturedPingRequestId;
        mFeaturedPingInFlight = true;
        mFeaturedStatus = ServerStatus.checking();
        renderFeaturedStatus(mFeaturedStatus);
        updateServerCount();
        executePing(() -> {
            ServerStatus result;
            try {
                result = ServerStatus.online(MinecraftServerPing.ping(FEATURED_SERVER_ADDRESS), null);
            } catch (Exception error) {
                result = ServerStatus.fromFailure(error);
            }
            final ServerStatus completedStatus = result;
            Tools.runOnUiThread(() -> {
                if (requestId == mFeaturedPingRequestId) mFeaturedPingInFlight = false;
                if (!isAdded() || requestId != mFeaturedPingRequestId || mFeaturedStatusView == null) return;
                mFeaturedStatus = completedStatus;
                renderFeaturedStatus(completedStatus);
                updateServerCount();
            });
        });
    }

    private void showServerDialog(@Nullable ServerEntry editEntry) {
        Context context = requireContext();
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(context, 22);
        int vertical = dp(context, 8);
        form.setPadding(horizontal, vertical, horizontal, vertical);

        EditText nameInput = new EditText(context);
        nameInput.setSingleLine(true);
        nameInput.setHint("Server name");
        nameInput.setTextSize(16);
        if (editEntry != null) nameInput.setText(editEntry.name);
        form.addView(nameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        MinecraftServerPing.Endpoint existingEndpoint = null;
        if (editEntry != null) {
            try {
                existingEndpoint = MinecraftServerPing.parseEndpoint(editEntry.address);
            } catch (IllegalArgumentException ignored) {
                // Saved entries are validated on load; leave blank if an old entry is malformed.
            }
        }

        EditText addressInput = new EditText(context);
        addressInput.setSingleLine(true);
        addressInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addressInput.setHint("Server IP or hostname (IPv6 supported)");
        addressInput.setTextSize(14);
        if (existingEndpoint != null) addressInput.setText(existingEndpoint.host);
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addressParams.topMargin = dp(context, 8);
        form.addView(addressInput, addressParams);

        EditText portInput = new EditText(context);
        portInput.setSingleLine(true);
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setHint("Port (default 25565)");
        portInput.setTextSize(14);
        portInput.setText(existingEndpoint == null ? "25565" : Integer.toString(existingEndpoint.port));
        LinearLayout.LayoutParams portParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        portParams.topMargin = dp(context, 4);
        form.addView(portInput, portParams);

        EditText linkInput = new EditText(context);
        linkInput.setSingleLine(true);
        linkInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        linkInput.setHint("Server Discord or website HTTPS link (optional)");
        linkInput.setTextSize(14);
        if (editEntry != null) linkInput.setText(editEntry.discordUrl);
        LinearLayout.LayoutParams linkParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        linkParams.topMargin = dp(context, 4);
        form.addView(linkInput, linkParams);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(editEntry == null ? "Add server" : "Edit server")
                .setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(editEntry == null ? "Add" : "Save", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty() || name.length() > 64) {
                nameInput.setError("Enter a name (1–64 characters)");
                return;
            }
            final MinecraftServerPing.Endpoint endpoint;
            try {
                endpoint = parseEndpointFields(addressInput.getText().toString(), portInput.getText().toString());
            } catch (IllegalArgumentException error) {
                if (error.getMessage() != null && error.getMessage().toLowerCase(Locale.ROOT).contains("port")) {
                    portInput.setError(error.getMessage());
                } else {
                    addressInput.setError(error.getMessage());
                }
                return;
            }
            String address = endpoint.toAddress();
            if (endpoint.normalizedKey().equals(
                    MinecraftServerPing.parseEndpoint(FEATURED_SERVER_ADDRESS).normalizedKey())) {
                addressInput.setError("LuciferMC is already pinned in Server Hub");
                return;
            }
            for (ServerEntry saved : mServers) {
                if ((editEntry == null || !saved.id.equals(editEntry.id)) &&
                        endpoint.normalizedKey().equals(MinecraftServerPing.parseEndpoint(saved.address).normalizedKey())) {
                    addressInput.setError("This server address is already saved");
                    return;
                }
            }
            if (editEntry == null && mServers.size() >= MAX_SERVERS) {
                Toast.makeText(context, "The server list is full", Toast.LENGTH_SHORT).show();
                return;
            }
            final String link;
            try {
                link = normalizeServerLink(linkInput.getText().toString());
            } catch (IllegalArgumentException invalidLink) {
                linkInput.setError(invalidLink.getMessage());
                return;
            }

            final ServerEntry changedEntry;
            if (editEntry == null) {
                changedEntry = new ServerEntry(UUID.randomUUID().toString(), name, address, link);
                mServers.add(changedEntry);
            } else {
                changedEntry = editEntry;
                changedEntry.name = name;
                changedEntry.address = address;
                changedEntry.discordUrl = link;
                mStatuses.remove(changedEntry.id);
                mPingRequestIds.remove(changedEntry.id);
                mPingInFlight.remove(changedEntry.id);
            }
            saveServers();
            if (mAdapter != null) mAdapter.notifyDataSetChanged();
            dialog.dismiss();
            syncServersDatAsync(true);
            pingServer(changedEntry);
        }));
        dialog.show();
    }

    private static MinecraftServerPing.Endpoint parseEndpointFields(String rawHost, String rawPort) {
        String host = rawHost == null ? "" : rawHost.trim();
        if (host.isEmpty()) throw new IllegalArgumentException("Enter a server IP or hostname");
        final int port;
        try {
            port = Integer.parseInt(rawPort == null ? "" : rawPort.trim());
        } catch (NumberFormatException invalidPort) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        if (host.indexOf(':') >= 0 && !host.startsWith("[")) host = "[" + host + "]";
        return MinecraftServerPing.parseEndpoint(host + ":" + port);
    }

    private static String normalizeServerLink(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return "";
        if (value.length() > 2048) throw new IllegalArgumentException("Link is too long");
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) || uri.getHost() == null || uri.getHost().isEmpty() ||
                uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Enter a valid HTTPS URL");
        }
        return uri.normalizeScheme().toString();
    }

    private void openWebOrDiscord(String url, boolean preferDiscord) {
        if (!isAdded()) return;
        final Uri uri;
        try {
            String normalized = normalizeServerLink(url);
            uri = Uri.parse(normalized);
        } catch (IllegalArgumentException invalid) {
            Toast.makeText(requireContext(), "No valid link is configured", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (preferDiscord) {
                Intent discord = new Intent(Intent.ACTION_VIEW, uri).setPackage("com.discord");
                startActivity(discord);
                return;
            }
        } catch (android.content.ActivityNotFoundException ignored) {
            // Fall through to the browser for an invite when the Discord app is not installed.
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (android.content.ActivityNotFoundException noHandler) {
            Toast.makeText(requireContext(), "No app is available to open this link", Toast.LENGTH_LONG).show();
        }
    }

    private void refreshAll() {
        if (mRecyclerView == null) return;
        pingFeaturedServer();
        for (ServerEntry entry : new ArrayList<>(mServers)) pingServer(entry);
    }

    private void pingServer(ServerEntry entry) {
        if (entry == null || mRecyclerView == null || mPingInFlight.containsKey(entry.id)) return;
        final String address = entry.address;
        final int requestId = mNextPingRequestId++;
        mPingRequestIds.put(entry.id, requestId);
        mPingInFlight.put(entry.id, requestId);
        mStatuses.put(entry.id, ServerStatus.checking());
        notifyEntryChanged(entry.id);
        updateServerCount();
        executePing(() -> {
            ServerStatus result;
            try {
                MinecraftServerPing.PingResult ping = MinecraftServerPing.ping(address);
                result = ServerStatus.online(ping, decodeServerFavicon(ping.faviconPng));
            } catch (Exception error) {
                result = ServerStatus.fromFailure(error);
                Log.d(TAG, "Ping did not complete for " + address + ": " + error.getClass().getSimpleName());
            }
            final ServerStatus completedStatus = result;
            Tools.runOnUiThread(() -> {
                if (Integer.valueOf(requestId).equals(mPingInFlight.get(entry.id))) {
                    mPingInFlight.remove(entry.id);
                }
                if (!isAdded() || mRecyclerView == null ||
                        !Integer.valueOf(requestId).equals(mPingRequestIds.get(entry.id))) return;
                boolean stillSaved = false;
                for (ServerEntry saved : mServers) {
                    if (saved.id.equals(entry.id)) {
                        stillSaved = true;
                        break;
                    }
                }
                if (!stillSaved) return;
                mStatuses.put(entry.id, completedStatus);
                notifyEntryChanged(entry.id);
                updateServerCount();
            });
        });
    }

    private static Bitmap decodeServerFavicon(@Nullable byte[] png) {
        if (png == null || png.length == 0) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(png, 0, png.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 512 || bounds.outHeight > 512) {
                return null;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inSampleSize = Math.max(1, Math.max(bounds.outWidth, bounds.outHeight) / 96);
            return BitmapFactory.decodeByteArray(png, 0, png.length, options);
        } catch (RuntimeException invalidImage) {
            Log.w(TAG, "Server sent an invalid favicon", invalidImage);
            return null;
        }
    }

    private void notifyEntryChanged(String id) {
        if (mAdapter == null) return;
        for (int i = 0; i < mServers.size(); i++) {
            if (mServers.get(i).id.equals(id)) {
                mAdapter.notifyItemChanged(i);
                return;
            }
        }
    }

    private void quickPlay(ServerEntry entry) {
        if (entry == null || !containsServer(entry.id)) return;
        quickPlay(entry.address);
    }

    private boolean containsServer(String id) {
        for (ServerEntry saved : mServers) if (saved.id.equals(id)) return true;
        return false;
    }

    private void quickPlay(String address) {
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
            if (!QuickPlayManager.request(address)) {
                Toast.makeText(requireContext(), "Could not prepare the server connection", Toast.LENGTH_LONG).show();
                return;
            }
            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        });
    }

    private void confirmRemove(ServerEntry entry) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Remove server?")
                .setMessage("Remove “" + entry.name + "” from Lucifer Launcher? Its managed servers.dat entry will also be removed from the active profile.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.global_remove, (dialog, which) -> {
                    mServers.remove(entry);
                    mStatuses.remove(entry.id);
                    mPingRequestIds.remove(entry.id);
                    mPingInFlight.remove(entry.id);
                    saveServers();
                    if (mAdapter != null) mAdapter.notifyDataSetChanged();
                    syncServersDatAsync(true);
                })
                .show();
    }

    /** Mirrors this Hub snapshot into the currently selected profile's game directory. */
    private void syncServersDatAsync(boolean notifyUser) {
        final int generation = SERVERDAT_GENERATION.incrementAndGet();
        final SharedPreferences prefs = preferences();
        final ArrayList<MinecraftServerListStore.ServerEntry> desired = new ArrayList<>(mServers.size() + 1);
        desired.add(new MinecraftServerListStore.ServerEntry("LuciferMC", FEATURED_SERVER_ADDRESS));
        for (ServerEntry entry : mServers) {
            desired.add(new MinecraftServerListStore.ServerEntry(entry.name, entry.address));
        }

        LauncherProfiles.loadAsync(() -> {
            if (generation != SERVERDAT_GENERATION.get()) return;
            String profileKey = LauncherPreferences.DEFAULT_PREF.getString(
                    LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
            MinecraftProfile profile = LauncherProfiles.mainProfileJson != null &&
                    LauncherProfiles.mainProfileJson.profiles != null
                    ? LauncherProfiles.mainProfileJson.profiles.get(profileKey) : null;
            if (profile == null) {
                reportSyncResult(generation, notifyUser, null,
                        new IOException("The selected Minecraft profile could not be found"));
                return;
            }
            final String selectedProfileKey = profileKey;
            File gameDirectory = Tools.getGameDirPath(profile);
            PojavApplication.sExecutorService.execute(() -> {
                synchronized (SERVERDAT_SYNC_LOCK) {
                    if (generation != SERVERDAT_GENERATION.get()) return;
                    String stillSelected = LauncherPreferences.DEFAULT_PREF.getString(
                            LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
                    if (!selectedProfileKey.equals(stillSelected)) {
                        Tools.runOnUiThread(() -> {
                            if (generation == SERVERDAT_GENERATION.get()) syncServersDatAsync(notifyUser);
                        });
                        return;
                    }
                    try {
                        String managedKey = managedPreferenceKey(gameDirectory);
                        Set<String> previous = new HashSet<>(prefs.getStringSet(managedKey, Collections.emptySet()));
                        MinecraftServerListStore.SyncResult result = MinecraftServerListStore.sync(
                                gameDirectory, desired, previous);
                        boolean savedOwnership = prefs.edit()
                                .putStringSet(managedKey, new HashSet<>(result.managedKeys))
                                .commit();
                        if (!savedOwnership) {
                            Log.w(TAG, "servers.dat updated, but its managed-entry marker was not persisted");
                        }
                        reportSyncResult(generation, notifyUser, result, null);
                    } catch (Exception error) {
                        Log.e(TAG, "Could not synchronize servers.dat for the active profile", error);
                        reportSyncResult(generation, notifyUser, null, error);
                    }
                }
            });
        });
    }

    private static String managedPreferenceKey(File gameDirectory) throws IOException {
        String canonicalPath = gameDirectory.getCanonicalPath();
        String encoded = Base64.encodeToString(canonicalPath.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        return PREF_MANAGED_SERVERDAT_PREFIX + encoded;
    }

    private void reportSyncResult(int generation, boolean notifyUser,
                                  @Nullable MinecraftServerListStore.SyncResult result,
                                  @Nullable Exception error) {
        if (!notifyUser) return;
        Tools.runOnUiThread(() -> {
            if (!isAdded() || getView() == null || generation != SERVERDAT_GENERATION.get()) return;
            if (error != null) {
                Toast.makeText(requireContext(),
                        "Saved in Server Hub, but the active profile's servers.dat could not be updated: " +
                                safeErrorMessage(error), Toast.LENGTH_LONG).show();
            } else if (result != null) {
                String message = result.added > 0 || result.removed > 0
                        ? "Server Hub synced with the selected profile's Minecraft server list"
                        : "Saved server list is already synchronized for the selected profile";
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static String safeErrorMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return "check profile storage access";
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        return message.length() > 110 ? message.substring(0, 110) + "…" : message;
    }

    private int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class ServerEntry {
        final String id;
        String name;
        String address;
        String discordUrl;

        ServerEntry(String id, String name, String address, String discordUrl) {
            this.id = id;
            this.name = name;
            this.address = address;
            this.discordUrl = discordUrl == null ? "" : discordUrl;
        }
    }

    private enum ServerState { CHECKING, ONLINE, OFFLINE, UNKNOWN }

    private static final class ServerStatus {
        final ServerState state;
        final MinecraftServerPing.PingResult ping;
        final Bitmap favicon;

        private ServerStatus(ServerState state, MinecraftServerPing.PingResult ping, Bitmap favicon) {
            this.state = state;
            this.ping = ping;
            this.favicon = favicon;
        }

        static ServerStatus checking() { return new ServerStatus(ServerState.CHECKING, null, null); }
        static ServerStatus online(MinecraftServerPing.PingResult ping, Bitmap favicon) {
            return new ServerStatus(ServerState.ONLINE, ping, favicon);
        }
        static ServerStatus fromFailure(Exception error) {
            if (error instanceof SocketTimeoutException || error instanceof ConnectException) {
                return new ServerStatus(ServerState.OFFLINE, null, null);
            }
            return new ServerStatus(ServerState.UNKNOWN, null, null);
        }
    }

    private final class ServerAdapter extends RecyclerView.Adapter<ServerAdapter.ServerHolder> {
        @NonNull
        @Override
        public ServerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_server_hub, parent, false);
            return new ServerHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ServerHolder holder, int position) {
            ServerEntry entry = mServers.get(position);
            holder.name.setText(entry.name);
            holder.address.setText(entry.address);
            holder.logo.setImageResource(R.drawable.ic_server_placeholder);
            holder.logo.setContentDescription("Generic server logo; no favicon is currently available");
            holder.bannerPlaceholder.setVisibility(View.VISIBLE);
            holder.discord.setVisibility(entry.discordUrl.isEmpty() ? View.GONE : View.VISIBLE);
            ServerStatus status = mStatuses.get(entry.id);

            if (status == null || status.state == ServerState.CHECKING) {
                holder.status.setText(status == null ? "Not checked · tap refresh" : "Checking live status…");
                holder.players.setText(status == null ? "Players unavailable" : "Players: checking…");
                holder.details.setText("Version unavailable");
                holder.ping.setText(status == null ? "Ping unavailable" : "Ping: checking…");
                holder.motd.setText(status == null ? "MOTD unavailable" : "Loading MOTD…");
                holder.dot.setBackgroundResource(R.drawable.bg_dot_checking);
            } else if (status.state == ServerState.OFFLINE) {
                holder.status.setText("Offline · no response");
                holder.players.setText("Players unavailable");
                holder.details.setText("Version unavailable");
                holder.ping.setText("Ping unavailable");
                holder.motd.setText("MOTD unavailable");
                holder.dot.setBackgroundResource(R.drawable.bg_dot_offline);
            } else if (status.state == ServerState.UNKNOWN) {
                holder.status.setText("Status unknown");
                holder.players.setText("Players unavailable");
                holder.details.setText("Version unavailable");
                holder.ping.setText("Ping unavailable");
                holder.motd.setText("MOTD unavailable");
                holder.dot.setBackgroundResource(R.drawable.bg_dot_unknown);
            } else {
                holder.status.setText("Online");
                holder.players.setText(formatPlayers(status.ping));
                holder.details.setText(status.ping.versionName == null || status.ping.versionName.isEmpty()
                        ? "Java server" : status.ping.versionName);
                holder.ping.setText(formatPing(status.ping));
                holder.motd.setText(MinecraftTextFormatter.format(status.ping.motd));
                holder.dot.setBackgroundResource(R.drawable.bg_dot_online);
                if (status.favicon != null) {
                    holder.logo.setImageBitmap(status.favicon);
                    holder.logo.setContentDescription(entry.name + " server favicon");
                }
            }

            holder.connect.setOnClickListener(v -> quickPlay(entry));
            holder.discord.setOnClickListener(v -> openWebOrDiscord(entry.discordUrl,
                    entry.discordUrl.toLowerCase(Locale.ROOT).contains("discord")));
            holder.refresh.setOnClickListener(v -> pingServer(entry));
            holder.edit.setOnClickListener(v -> showServerDialog(entry));
            holder.remove.setOnClickListener(v -> confirmRemove(entry));
            holder.itemView.setOnClickListener(v -> quickPlay(entry));
        }

        @Override
        public int getItemCount() { return mServers.size(); }

        final class ServerHolder extends RecyclerView.ViewHolder {
            final View dot;
            final ImageView logo;
            final TextView bannerPlaceholder;
            final TextView name;
            final TextView address;
            final TextView status;
            final TextView players;
            final TextView details;
            final TextView ping;
            final TextView motd;
            final TextView connect;
            final TextView discord;
            final TextView refresh;
            final TextView edit;
            final TextView remove;

            ServerHolder(@NonNull View itemView) {
                super(itemView);
                dot = itemView.findViewById(R.id.server_status_dot);
                logo = itemView.findViewById(R.id.server_logo);
                bannerPlaceholder = itemView.findViewById(R.id.server_banner_placeholder_label);
                name = itemView.findViewById(R.id.tv_server_name);
                address = itemView.findViewById(R.id.tv_server_address);
                status = itemView.findViewById(R.id.tv_server_status);
                players = itemView.findViewById(R.id.tv_server_players);
                details = itemView.findViewById(R.id.tv_server_details);
                ping = itemView.findViewById(R.id.tv_server_ping);
                motd = itemView.findViewById(R.id.tv_server_motd);
                connect = itemView.findViewById(R.id.btn_server_connect);
                discord = itemView.findViewById(R.id.btn_server_discord);
                refresh = itemView.findViewById(R.id.btn_server_ping);
                edit = itemView.findViewById(R.id.btn_server_edit);
                remove = itemView.findViewById(R.id.btn_server_delete);
            }
        }
    }
}
