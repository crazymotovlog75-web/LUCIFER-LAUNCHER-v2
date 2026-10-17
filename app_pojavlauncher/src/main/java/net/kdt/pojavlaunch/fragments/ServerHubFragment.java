package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Saved Minecraft Java server hub with asynchronous status checks and one-shot Quick Play. */
public class ServerHubFragment extends Fragment {
    public static final String TAG = "ServerHubFragment";
    private static final String PREF_SERVERS = "lucifer_server_hub_list";
    private static final int MAX_SERVERS = 40;

    private RecyclerView mRecyclerView;
    private View mEmptyState;
    private ServerAdapter mAdapter;
    private final ArrayList<ServerEntry> mServers = new ArrayList<>();
    private final Map<String, ServerStatus> mStatuses = new HashMap<>();
    private final Map<String, Integer> mPingRequestIds = new HashMap<>();
    private int mNextPingRequestId = 1;

    public ServerHubFragment() {
        super(R.layout.fragment_server_hub);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mRecyclerView = view.findViewById(R.id.server_hub_list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setItemAnimator(null);
        mEmptyState = view.findViewById(R.id.server_hub_empty);
        mAdapter = new ServerAdapter();
        mRecyclerView.setAdapter(mAdapter);

        view.findViewById(R.id.server_hub_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        view.findViewById(R.id.btn_server_add).setOnClickListener(v -> showServerDialog(null));
        view.findViewById(R.id.btn_server_refresh_all).setOnClickListener(v -> refreshAll());

        loadServers();
        updateEmptyState();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAll();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mRecyclerView = null;
        mEmptyState = null;
        mAdapter = null;
    }

    private SharedPreferences preferences() {
        // Keep using the existing preference file name so saved settings and server data remain
        // available to users upgrading from the current launcher package.
        return requireContext().getSharedPreferences("cslauncher_settings", Context.MODE_PRIVATE);
    }

    private void loadServers() {
        mServers.clear();
        try {
            JSONArray array = new JSONArray(preferences().getString(PREF_SERVERS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String name = item.optString("name", "").trim();
                String address = item.optString("address", "").trim();
                if (name.isEmpty() || address.isEmpty()) continue;
                try {
                    address = MinecraftServerPing.parseEndpoint(address).toAddress();
                } catch (IllegalArgumentException invalid) {
                    continue;
                }
                mServers.add(new ServerEntry(item.optString("id", UUID.randomUUID().toString()), name, address));
            }
        } catch (Exception ignored) {
            // Corrupt optional server-hub data should not prevent the launcher from opening.
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
                array.put(item);
            } catch (Exception ignored) {}
        }
        preferences().edit().putString(PREF_SERVERS, array.toString()).apply();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (mEmptyState == null || mRecyclerView == null) return;
        boolean empty = mServers.isEmpty();
        mEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        mRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
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

        EditText addressInput = new EditText(context);
        addressInput.setSingleLine(true);
        addressInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addressInput.setHint("Address · for example play.example.net:25565");
        addressInput.setTextSize(14);
        if (editEntry != null) addressInput.setText(editEntry.address);
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addressParams.topMargin = dp(context, 10);
        form.addView(addressInput, addressParams);

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
            final String address;
            try {
                address = MinecraftServerPing.parseEndpoint(addressInput.getText().toString()).toAddress();
            } catch (IllegalArgumentException error) {
                addressInput.setError(error.getMessage());
                return;
            }

            if (editEntry == null && mServers.size() >= MAX_SERVERS) {
                Toast.makeText(context, "The server list is full", Toast.LENGTH_SHORT).show();
                return;
            }
            if (editEntry == null) {
                ServerEntry newEntry = new ServerEntry(UUID.randomUUID().toString(), name, address);
                mServers.add(newEntry);
                mStatuses.remove(newEntry.id);
            } else {
                editEntry.name = name;
                editEntry.address = address;
                mStatuses.remove(editEntry.id);
            }
            saveServers();
            if (mAdapter != null) mAdapter.notifyDataSetChanged();
            dialog.dismiss();
            if (editEntry == null) pingServer(mServers.get(mServers.size() - 1));
            else pingServer(editEntry);
        }));
        dialog.show();
    }

    private void refreshAll() {
        if (mServers.isEmpty()) {
            updateEmptyState();
            return;
        }
        for (ServerEntry entry : new ArrayList<>(mServers)) pingServer(entry);
    }

    private void pingServer(ServerEntry entry) {
        final String address = entry.address;
        final int requestId = mNextPingRequestId++;
        mPingRequestIds.put(entry.id, requestId);
        mStatuses.put(entry.id, ServerStatus.checking());
        notifyEntryChanged(entry.id);
        PojavApplication.sExecutorService.execute(() -> {
            ServerStatus result;
            try {
                MinecraftServerPing.PingResult ping = MinecraftServerPing.ping(address);
                result = ServerStatus.online(ping);
            } catch (Exception error) {
                result = ServerStatus.offline();
            }
            final ServerStatus finalResult = result;
            Tools.runOnUiThread(() -> {
                if (!isAdded() || !Integer.valueOf(requestId).equals(mPingRequestIds.get(entry.id))) return;
                boolean stillSaved = false;
                for (ServerEntry saved : mServers) {
                    if (saved.id.equals(entry.id)) {
                        stillSaved = true;
                        break;
                    }
                }
                if (!stillSaved) return;
                mStatuses.put(entry.id, finalResult);
                notifyEntryChanged(entry.id);
            });
        });
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
        boolean serverStillSaved = false;
        for (ServerEntry saved : mServers) {
            if (saved.id.equals(entry.id)) {
                serverStillSaved = true;
                break;
            }
        }
        if (!serverStillSaved) return;
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
            if (!QuickPlayManager.request(entry.address)) {
                Toast.makeText(requireContext(), "Could not prepare the server connection", Toast.LENGTH_LONG).show();
                return;
            }
            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        });
    }

    private void confirmRemove(ServerEntry entry) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Remove server?")
                .setMessage("Remove “" + entry.name + "” from Lucifer Launcher?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.global_remove, (dialog, which) -> {
                    mServers.remove(entry);
                    mStatuses.remove(entry.id);
                    mPingRequestIds.remove(entry.id);
                    saveServers();
                    if (mAdapter != null) mAdapter.notifyDataSetChanged();
                })
                .show();
    }

    private int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class ServerEntry {
        final String id;
        String name;
        String address;

        ServerEntry(String id, String name, String address) {
            this.id = id;
            this.name = name;
            this.address = address;
        }
    }

    private static final class ServerStatus {
        final boolean checking;
        final boolean online;
        final MinecraftServerPing.PingResult ping;

        private ServerStatus(boolean checking, boolean online, MinecraftServerPing.PingResult ping) {
            this.checking = checking;
            this.online = online;
            this.ping = ping;
        }

        static ServerStatus checking() { return new ServerStatus(true, false, null); }
        static ServerStatus offline() { return new ServerStatus(false, false, null); }
        static ServerStatus online(MinecraftServerPing.PingResult ping) {
            return new ServerStatus(false, true, ping);
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
            ServerStatus status = mStatuses.get(entry.id);
            if (status == null) {
                holder.status.setText("Not checked · tap refresh");
                holder.details.setText("Java server");
                holder.dot.setAlpha(0.35f);
            } else if (status.checking) {
                holder.status.setText("Checking live status…");
                holder.details.setText("Ping in progress");
                holder.dot.setAlpha(0.65f);
            } else if (!status.online || status.ping == null) {
                holder.status.setText("Offline or not responding");
                holder.details.setText("No response");
                holder.dot.setAlpha(0.25f);
            } else {
                holder.status.setText("Online · " + status.ping.latencyMs + " ms");
                holder.details.setText(status.ping.onlinePlayers >= 0
                        ? status.ping.onlinePlayers + "/" + status.ping.maximumPlayers + " players\n" +
                          (status.ping.versionName.isEmpty() ? "Java Edition" : status.ping.versionName)
                        : (status.ping.versionName.isEmpty() ? "Java server" : status.ping.versionName));
                holder.dot.setAlpha(1f);
            }

            holder.connect.setOnClickListener(v -> quickPlay(entry));
            holder.refresh.setOnClickListener(v -> pingServer(entry));
            holder.edit.setOnClickListener(v -> showServerDialog(entry));
            holder.remove.setOnClickListener(v -> confirmRemove(entry));
            holder.itemView.setOnClickListener(v -> quickPlay(entry));
        }

        @Override
        public int getItemCount() { return mServers.size(); }

        final class ServerHolder extends RecyclerView.ViewHolder {
            final View dot;
            final TextView name;
            final TextView address;
            final TextView status;
            final TextView details;
            final TextView connect;
            final TextView refresh;
            final TextView edit;
            final TextView remove;

            ServerHolder(@NonNull View itemView) {
                super(itemView);
                dot = itemView.findViewById(R.id.server_status_dot);
                name = itemView.findViewById(R.id.tv_server_name);
                address = itemView.findViewById(R.id.tv_server_address);
                status = itemView.findViewById(R.id.tv_server_status);
                details = itemView.findViewById(R.id.tv_server_details);
                connect = itemView.findViewById(R.id.btn_server_connect);
                refresh = itemView.findViewById(R.id.btn_server_ping);
                edit = itemView.findViewById(R.id.btn_server_edit);
                remove = itemView.findViewById(R.id.btn_server_delete);
            }
        }
    }
}
