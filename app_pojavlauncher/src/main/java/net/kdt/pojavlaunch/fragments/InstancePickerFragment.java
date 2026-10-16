package net.kdt.pojavlaunch.fragments;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.profiles.ProfileIconCache;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Glass-style instance directory. Profile I/O and icon lookups never block the UI thread. */
public class InstancePickerFragment extends Fragment {
    public static final String TAG = "InstancePickerFragment";

    private RecyclerView mRecyclerView;
    private EditText mSearchInput;
    private TextView mCountLabel;
    private View mEmptyState;
    private ProgressBar mProgress;
    private InstanceAdapter mAdapter;
    private List<Instance> mAllInstances = new ArrayList<>();

    public InstancePickerFragment() {
        super(R.layout.fragment_instance_picker);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.instance_picker_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        view.findViewById(R.id.instance_picker_add).setOnClickListener(v -> openCreateInstance());

        mRecyclerView = view.findViewById(R.id.instance_picker_recycler);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setItemAnimator(null);
        mAdapter = new InstanceAdapter();
        mRecyclerView.setAdapter(mAdapter);

        mSearchInput = view.findViewById(R.id.instance_picker_search);
        mCountLabel = view.findViewById(R.id.instance_picker_count);
        mEmptyState = view.findViewById(R.id.instance_picker_empty);
        mProgress = view.findViewById(R.id.instance_picker_progress);
        mProgress.setVisibility(View.VISIBLE);
        mRecyclerView.setVisibility(View.GONE);
        mSearchInput.setEnabled(false);

        mSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applySearch(s != null ? s.toString() : "");
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadInstances();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mRecyclerView = null;
        mSearchInput = null;
        mCountLabel = null;
        mEmptyState = null;
        mProgress = null;
        mAdapter = null;
    }

    private void loadInstances() {
        PojavApplication.sExecutorService.execute(() -> {
            final ArrayList<Instance> instances = new ArrayList<>();
            Exception failure = null;
            try {
                LauncherProfiles.load();
                // Opening the instance directory is an explicit request to discover already
                // installed versions that do not yet have their own profile entry.
                LauncherProfiles.rescanProfiles();
                Map<String, MinecraftProfile> profiles = LauncherProfiles.mainProfileJson != null
                        ? LauncherProfiles.mainProfileJson.profiles : null;
                if (profiles != null) {
                    for (Map.Entry<String, MinecraftProfile> entry :
                            LauncherProfiles.getProfilesInDisplayOrder()) {
                        String key = entry.getKey();
                        MinecraftProfile profile = entry.getValue();
                        if (key == null || key.trim().isEmpty() || profile == null ||
                                profile.name == null || profile.name.trim().isEmpty()) continue;
                        instances.add(new Instance(key, profile));
                    }
                }
            } catch (Exception error) {
                failure = error;
            }
            final Exception loadError = failure;
            Tools.runOnUiThread(() -> {
                if (!isAdded() || mRecyclerView == null) return;
                mAllInstances = instances;
                mAdapter.setInstances(instances);
                mProgress.setVisibility(View.GONE);
                mCountLabel.setText(getString(R.string.instance_count, instances.size()));
                mSearchInput.setEnabled(true);
                mRecyclerView.setVisibility(View.VISIBLE);
                applySearch(mSearchInput.getText().toString());
                if (loadError != null) {
                    Toast.makeText(requireContext(), R.string.instance_load_failed, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void applySearch(String query) {
        if (mAdapter == null || mRecyclerView == null) return;
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        ArrayList<Instance> filtered = new ArrayList<>();
        for (Instance instance : mAllInstances) {
            if (normalizedQuery.isEmpty() || instance.name.toLowerCase(Locale.ROOT).contains(normalizedQuery) ||
                    instance.version.toLowerCase(Locale.ROOT).contains(normalizedQuery) ||
                    instance.key.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                filtered.add(instance);
            }
        }
        mAdapter.setInstances(filtered);
        boolean noSearchResults = !normalizedQuery.isEmpty() && filtered.isEmpty();
        if (mEmptyState != null) mEmptyState.setVisibility(noSearchResults ? View.VISIBLE : View.GONE);
        mRecyclerView.setVisibility(noSearchResults ? View.GONE : View.VISIBLE);
    }

    private void openCreateInstance() {
        openPane(ProfileTypeSelectFragment.class, ProfileTypeSelectFragment.TAG);
    }

    private void openPane(Class<? extends Fragment> fragmentClass, String tag) {
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

    private void selectInstance(String profileKey) {
        Fragment parent = getParentFragment();
        while (parent != null && !(parent instanceof MainMenuFragment)) {
            parent = parent.getParentFragment();
        }
        if (parent instanceof MainMenuFragment) {
            ((MainMenuFragment) parent).selectInstance(profileKey);
            return;
        }
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, profileKey)
                .apply();
        ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, profileKey);
        Tools.backToMainMenu(requireActivity());
    }

    private static final class Instance {
        final String key;
        final MinecraftProfile profile;
        final String name;
        final String version;
        final String lastUsed;

        Instance(String key, MinecraftProfile profile) {
            this.key = key;
            this.profile = profile;
            this.name = profile.name != null ? profile.name : key;
            this.version = profile.lastVersionId != null && !profile.lastVersionId.isEmpty()
                    ? profile.lastVersionId : "Unknown version";
            this.lastUsed = profile.lastUsed != null ? profile.lastUsed : "";
        }
    }

    private final class InstanceAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_CREATE = 0;
        private static final int TYPE_INSTANCE = 1;
        private final ArrayList<Instance> instances = new ArrayList<>();
        private String selectedKey = "";

        InstanceAdapter() {
            setHasStableIds(true);
        }

        void setInstances(List<Instance> values) {
            instances.clear();
            instances.addAll(values);
            selectedKey = LauncherPreferences.DEFAULT_PREF.getString(
                    LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
            notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            return position == instances.size() ? Long.MIN_VALUE : instances.get(position).key.hashCode();
        }

        @Override
        public int getItemViewType(int position) {
            return position == instances.size() ? TYPE_CREATE : TYPE_INSTANCE;
        }

        @Override
        public int getItemCount() {
            return instances.size() + 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_CREATE) {
                return new CreateHolder(inflater.inflate(R.layout.item_instance_picker_create, parent, false));
            }
            return new InstanceHolder(inflater.inflate(R.layout.item_instance_picker_profile, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof CreateHolder) {
                holder.itemView.setOnClickListener(v -> openCreateInstance());
                return;
            }
            Instance item = instances.get(position);
            InstanceHolder profileHolder = (InstanceHolder) holder;
            profileHolder.name.setText(item.name);
            profileHolder.version.setText(item.version);
            profileHolder.lastUsed.setText(item.lastUsed.isEmpty()
                    ? "Not launched yet" : "Last played · " + formatDate(item.lastUsed));
            boolean selected = item.key.equals(selectedKey);
            profileHolder.active.setVisibility(selected ? View.VISIBLE : View.GONE);
            profileHolder.root.setBackgroundResource(selected
                    ? R.drawable.bg_lucifer_nav_item : R.drawable.bg_lucifer_glass_card);
            bindIcon(profileHolder.icon, item);
            profileHolder.root.setOnClickListener(v -> selectInstance(item.key));
        }

        private String formatDate(String timestamp) {
            return timestamp.length() >= 10 ? timestamp.substring(0, 10) : timestamp;
        }

        private void bindIcon(ImageView target, Instance instance) {
            String versionId = instance.version.toLowerCase(Locale.ROOT);
            int fallbackResource = versionId.contains("fabric") ? R.drawable.ic_fabric
                    : versionId.contains("quilt") ? R.drawable.ic_quilt
                    : R.drawable.lucifer_launcher_emblem;
            target.setImageResource(fallbackResource);
            target.setTag(instance.key);
            final android.content.res.Resources resources = target.getResources();
            PojavApplication.sExecutorService.execute(() -> {
                Drawable loaded;
                try {
                    loaded = ProfileIconCache.fetchIcon(resources, instance.key, instance.profile.icon);
                } catch (Exception error) {
                    loaded = null;
                }
                final Drawable result = loaded;
                if (result == null) return;
                Tools.runOnUiThread(() -> {
                    if (instance.key.equals(target.getTag())) target.setImageDrawable(result);
                });
            });
        }
    }

    private static final class CreateHolder extends RecyclerView.ViewHolder {
        CreateHolder(@NonNull View itemView) { super(itemView); }
    }

    private static final class InstanceHolder extends RecyclerView.ViewHolder {
        final View root;
        final ImageView icon;
        final TextView name;
        final TextView version;
        final TextView lastUsed;
        final TextView active;

        InstanceHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView;
            icon = itemView.findViewById(R.id.instance_picker_profile_icon);
            name = itemView.findViewById(R.id.instance_picker_profile_name);
            version = itemView.findViewById(R.id.instance_picker_profile_version);
            lastUsed = itemView.findViewById(R.id.instance_picker_profile_last_used);
            active = itemView.findViewById(R.id.instance_picker_profile_active);
        }
    }
}
