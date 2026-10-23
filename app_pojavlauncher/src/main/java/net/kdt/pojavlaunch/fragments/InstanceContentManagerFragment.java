package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.InstanceContentFiles;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.profiles.ProfileIconCache;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Per-instance browser and reversible enable/disable controls for game content. */
public class InstanceContentManagerFragment extends Fragment {
    public static final String TAG = "InstanceContentManagerFragment";
    public static final String ARG_PROFILE_KEY = "instance_profile_key";

    private static final String BACKGROUND_DIRECTORY = "instance_backgrounds";
    private static final int MAX_BACKGROUND_WIDTH = 1920;
    private static final int MAX_BACKGROUND_HEIGHT = 1080;
    private static final String LOG_TAG = "InstanceContent";

    private String mProfileKey;
    private MinecraftProfile mProfile;
    private File mGameDirectory;
    private InstanceContentFiles.Category mCategory = InstanceContentFiles.Category.MODS;
    private ContentFilter mFilter = ContentFilter.ALL;
    private int mLoadGeneration;

    private ImageView mBackgroundImage;
    private ImageView mProfileIcon;
    private TextView mProfileName;
    private TextView mProfileVersion;
    private TextView mClearBackground;
    private EditText mSearch;
    private TextView mFilterButton;
    private TextView mSectionTitle;
    private TextView mCount;
    private TextView mEmptyTitle;
    private RecyclerView mRecyclerView;
    private FrameLayout mLoadingContainer;
    private View mEmptyState;
    private ContentAdapter mAdapter;
    private final ArrayList<ProfileChoice> mProfiles = new ArrayList<>();
    private final ArrayList<InstanceContentFiles.Entry> mAllEntries = new ArrayList<>();

    private final ActivityResultLauncher<String> mBackgroundPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::onBackgroundPicked);

    public InstanceContentManagerFragment() {
        super(R.layout.fragment_instance_content_manager);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mProfileKey = getArguments() != null ? getArguments().getString(ARG_PROFILE_KEY) : null;
        if (mProfileKey == null || mProfileKey.trim().isEmpty()) {
            mProfileKey = LauncherPreferences.DEFAULT_PREF != null
                    ? LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "")
                    : "";
        }

        mBackgroundImage = view.findViewById(R.id.instance_content_background);
        mProfileIcon = view.findViewById(R.id.instance_content_profile_icon);
        mProfileName = view.findViewById(R.id.instance_content_profile_name);
        mProfileVersion = view.findViewById(R.id.instance_content_profile_version);
        mClearBackground = view.findViewById(R.id.instance_content_clear_background);
        mSearch = view.findViewById(R.id.instance_content_search);
        mFilterButton = view.findViewById(R.id.instance_content_filter);
        mSectionTitle = view.findViewById(R.id.instance_content_section_title);
        mCount = view.findViewById(R.id.instance_content_count);
        mEmptyTitle = view.findViewById(R.id.instance_content_empty_title);
        mRecyclerView = view.findViewById(R.id.instance_content_recycler);
        mLoadingContainer = view.findViewById(R.id.instance_content_loading);
        mEmptyState = view.findViewById(R.id.instance_content_empty);

        view.findViewById(R.id.instance_content_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        view.findViewById(R.id.instance_content_profile_selector).setOnClickListener(v -> showProfileChooser());
        view.findViewById(R.id.instance_content_set_background).setOnClickListener(v -> mBackgroundPicker.launch("image/*"));
        mClearBackground.setOnClickListener(v -> clearBackground());
        view.findViewById(R.id.instance_content_open_folder).setOnClickListener(v -> openCategoryFolder());
        view.findViewById(R.id.instance_content_refresh).setOnClickListener(v -> loadCategoryItems());
        mFilterButton.setOnClickListener(this::showFilterMenu);

        view.findViewById(R.id.instance_content_tab_mods).setOnClickListener(v -> setCategory(InstanceContentFiles.Category.MODS));
        view.findViewById(R.id.instance_content_tab_packs).setOnClickListener(v -> setCategory(InstanceContentFiles.Category.PACKS));
        view.findViewById(R.id.instance_content_tab_shaders).setOnClickListener(v -> setCategory(InstanceContentFiles.Category.SHADERS));
        view.findViewById(R.id.instance_content_tab_worlds).setOnClickListener(v -> setCategory(InstanceContentFiles.Category.WORLDS));

        mRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        mRecyclerView.setItemAnimator(null);
        mAdapter = new ContentAdapter((entry, enabled) -> toggleEntry(entry, enabled));
        mRecyclerView.setAdapter(mAdapter);
        mSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                applySearchAndFilter();
            }
            @Override public void afterTextChanged(android.text.Editable text) { }
        });

        setCategory(InstanceContentFiles.Category.MODS);
        loadProfiles();
    }

    @Override
    public void onDestroyView() {
        mLoadGeneration++;
        mBackgroundImage = null;
        mProfileIcon = null;
        mProfileName = null;
        mProfileVersion = null;
        mClearBackground = null;
        mSearch = null;
        mFilterButton = null;
        mSectionTitle = null;
        mCount = null;
        mEmptyTitle = null;
        mRecyclerView = null;
        mLoadingContainer = null;
        mEmptyState = null;
        mAdapter = null;
        super.onDestroyView();
    }

    private void loadProfiles() {
        PojavApplication.sExecutorService.execute(() -> {
            final ArrayList<ProfileChoice> choices = new ArrayList<>();
            try {
                LauncherProfiles.load();
                List<Map.Entry<String, MinecraftProfile>> entries = LauncherProfiles.getProfilesInDisplayOrder();
                if (entries != null) {
                    for (Map.Entry<String, MinecraftProfile> entry : entries) {
                        String key = entry.getKey();
                        MinecraftProfile profile = entry.getValue();
                        if (key == null || key.trim().isEmpty() || profile == null) continue;
                        String name = profile.name == null || profile.name.trim().isEmpty() ? key : profile.name;
                        choices.add(new ProfileChoice(key, name, profile, Tools.getGameDirPath(profile)));
                    }
                }
            } catch (Exception error) {
                Log.e(LOG_TAG, "Could not load profiles for the content manager", error);
            }

            String selectedKey = mProfileKey;
            if (choices.isEmpty()) {
                String preferredKey = LauncherPreferences.DEFAULT_PREF != null
                        ? LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "")
                        : "";
                MinecraftProfile current = LauncherProfiles.getCurrentProfile();
                if (current != null) {
                    String key = preferredKey == null || preferredKey.isEmpty() ? "default" : preferredKey;
                    choices.add(new ProfileChoice(key, current.name == null ? key : current.name,
                            current, Tools.getGameDirPath(current)));
                }
            }

            ProfileChoice selected = null;
            for (ProfileChoice choice : choices) {
                if (choice.key.equals(selectedKey)) {
                    selected = choice;
                    break;
                }
            }
            if (selected == null && !choices.isEmpty()) selected = choices.get(0);
            final ProfileChoice selectedChoice = selected;
            final ArrayList<ProfileChoice> loadedChoices = choices;
            Tools.runOnUiThread(() -> {
                if (!isAdded() || mRecyclerView == null) return;
                mProfiles.clear();
                mProfiles.addAll(loadedChoices);
                if (selectedChoice == null) {
                    mProfileName.setText(R.string.no_profile_selected);
                    mProfileVersion.setText("");
                    mAllEntries.clear();
                    applySearchAndFilter();
                } else {
                    selectProfile(selectedChoice);
                }
            });
        });
    }

    private void selectProfile(ProfileChoice choice) {
        mProfileKey = choice.key;
        mProfile = choice.profile;
        mGameDirectory = choice.gameDirectory;
        mProfileName.setText(choice.name + "  ▾");
        String version = mProfile.lastVersionId == null || mProfile.lastVersionId.trim().isEmpty()
                ? getString(R.string.unknown) : mProfile.lastVersionId;
        mProfileVersion.setText(version);
        bindProfileIcon(choice);
        loadBackgroundForProfile();
        loadCategoryItems();
    }

    private void bindProfileIcon(ProfileChoice choice) {
        if (mProfileIcon == null) return;
        mProfileIcon.setImageResource(R.drawable.lucifer_launcher_emblem);
        mProfileIcon.setTag(choice.key);
        final ImageView target = mProfileIcon;
        final Resources resources = target.getResources();
        PojavApplication.sExecutorService.execute(() -> {
            Drawable icon;
            try {
                icon = ProfileIconCache.fetchIcon(resources, choice.key, choice.profile.icon);
            } catch (Exception error) {
                icon = null;
            }
            if (icon == null) return;
            final Drawable result = icon;
            Tools.runOnUiThread(() -> {
                if (target == mProfileIcon && choice.key.equals(target.getTag())) {
                    target.setImageDrawable(result);
                }
            });
        });
    }

    private void showProfileChooser() {
        if (mProfiles.isEmpty()) return;
        String[] names = new String[mProfiles.size()];
        int selectedIndex = 0;
        for (int index = 0; index < mProfiles.size(); index++) {
            ProfileChoice choice = mProfiles.get(index);
            names[index] = choice.name + " — " + (choice.profile.lastVersionId == null
                    ? getString(R.string.unknown) : choice.profile.lastVersionId);
            if (choice.key.equals(mProfileKey)) selectedIndex = index;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.instance_content_profile_switch)
                .setSingleChoiceItems(names, selectedIndex, (dialog, which) -> {
                    dialog.dismiss();
                    if (which >= 0 && which < mProfiles.size()) selectProfile(mProfiles.get(which));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setCategory(InstanceContentFiles.Category category) {
        mCategory = category;
        mFilter = ContentFilter.ALL;
        mAllEntries.clear();
        if (mAdapter != null) mAdapter.setEntries(mAllEntries);
        if (mSearch != null) mSearch.setText("");
        updateCategoryTabs();
        if (mSectionTitle != null) mSectionTitle.setText(getCategoryTitle(category));
        updateFilterButton();
        if (mGameDirectory != null) loadCategoryItems();
    }

    private void updateCategoryTabs() {
        if (getView() == null) return;
        setTabState(getView().findViewById(R.id.instance_content_tab_mods),
                mCategory == InstanceContentFiles.Category.MODS);
        setTabState(getView().findViewById(R.id.instance_content_tab_packs),
                mCategory == InstanceContentFiles.Category.PACKS);
        setTabState(getView().findViewById(R.id.instance_content_tab_shaders),
                mCategory == InstanceContentFiles.Category.SHADERS);
        setTabState(getView().findViewById(R.id.instance_content_tab_worlds),
                mCategory == InstanceContentFiles.Category.WORLDS);
    }

    private void setTabState(View tab, boolean selected) {
        if (tab == null) return;
        tab.setSelected(selected);
        tab.setBackgroundResource(selected ? R.drawable.bg_lucifer_action : R.drawable.bg_lucifer_nav_item);
        if (tab instanceof TextView) {
            ((TextView) tab).setTextColor(resolveThemeColor(selected ? R.attr.luciferOnAccent : R.attr.luciferAccent));
        }
    }

    private int resolveThemeColor(int attribute) {
        TypedValue value = new TypedValue();
        if (requireContext().getTheme().resolveAttribute(attribute, value, true)) {
            if (value.resourceId != 0) return getResources().getColor(value.resourceId);
            return value.data;
        }
        return Color.WHITE;
    }

    private int getCategoryTitle(InstanceContentFiles.Category category) {
        switch (category) {
            case PACKS: return R.string.instance_content_section_packs;
            case SHADERS: return R.string.instance_content_section_shaders;
            case WORLDS: return R.string.instance_content_section_worlds;
            case MODS:
            default: return R.string.instance_content_section_mods;
        }
    }

    private void loadCategoryItems() {
        if (!isAdded() || mRecyclerView == null || mGameDirectory == null || mCategory == null) return;
        final File gameDirectory = mGameDirectory;
        final InstanceContentFiles.Category category = mCategory;
        final int requestGeneration = ++mLoadGeneration;
        showLoading();
        PojavApplication.sExecutorService.execute(() -> {
            final List<InstanceContentFiles.Entry> entries = InstanceContentFiles.list(gameDirectory, category);
            Tools.runOnUiThread(() -> {
                if (!isAdded() || requestGeneration != mLoadGeneration || mRecyclerView == null ||
                        gameDirectory != mGameDirectory || category != mCategory) return;
                mAllEntries.clear();
                mAllEntries.addAll(entries);
                hideLoading();
                applySearchAndFilter();
            });
        });
    }

    private void showLoading() {
        if (mLoadingContainer != null) mLoadingContainer.setVisibility(View.VISIBLE);
        if (mRecyclerView != null) mRecyclerView.setVisibility(View.GONE);
        if (mEmptyState != null) mEmptyState.setVisibility(View.GONE);
    }

    private void hideLoading() {
        if (mLoadingContainer != null) mLoadingContainer.setVisibility(View.GONE);
    }

    private void applySearchAndFilter() {
        if (mAdapter == null || mRecyclerView == null || mEmptyState == null) return;
        String query = mSearch == null || mSearch.getText() == null
                ? "" : mSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        ArrayList<InstanceContentFiles.Entry> visibleEntries = new ArrayList<>();
        for (InstanceContentFiles.Entry entry : mAllEntries) {
            if (mFilter == ContentFilter.ENABLED && !entry.enabled) continue;
            if (mFilter == ContentFilter.DISABLED && entry.enabled) continue;
            if (!query.isEmpty() && !entry.displayName().toLowerCase(Locale.ROOT).contains(query) &&
                    !entry.file.getName().toLowerCase(Locale.ROOT).contains(query)) continue;
            visibleEntries.add(entry);
        }
        mAdapter.setEntries(visibleEntries);
        mCount.setText(getString(R.string.instance_content_count, visibleEntries.size()));
        updateFilterButton();
        boolean empty = visibleEntries.isEmpty();
        mRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        mEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (mEmptyTitle != null) {
            mEmptyTitle.setText(query.isEmpty()
                    ? R.string.instance_content_empty_title : R.string.instance_content_empty_search);
        }
    }

    private void updateFilterButton() {
        if (mFilterButton == null) return;
        switch (mFilter) {
            case ENABLED:
                mFilterButton.setText(R.string.instance_content_filter_enabled);
                break;
            case DISABLED:
                mFilterButton.setText(R.string.instance_content_filter_disabled);
                break;
            case ALL:
            default:
                mFilterButton.setText(getString(R.string.instance_content_filter_all, mAllEntries.size()));
                break;
        }
    }

    private void showFilterMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 0, 0, getString(R.string.instance_content_filter_all, mAllEntries.size()));
        menu.getMenu().add(0, 1, 1, R.string.instance_content_filter_enabled);
        menu.getMenu().add(0, 2, 2, R.string.instance_content_filter_disabled);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0) mFilter = ContentFilter.ALL;
            else if (item.getItemId() == 1) mFilter = ContentFilter.ENABLED;
            else if (item.getItemId() == 2) mFilter = ContentFilter.DISABLED;
            else return false;
            applySearchAndFilter();
            return true;
        });
        menu.show();
    }

    private void toggleEntry(InstanceContentFiles.Entry entry, boolean enabled) {
        if (mGameDirectory == null || entry == null || entry.pending || entry.enabled == enabled) return;
        entry.pending = true;
        if (mAdapter != null) mAdapter.notifyEntryChanged(entry);
        final File gameDirectory = mGameDirectory;
        PojavApplication.sExecutorService.execute(() -> {
            Exception failure = null;
            try {
                InstanceContentFiles.setEnabled(gameDirectory, entry, enabled);
            } catch (Exception error) {
                failure = error;
                Log.w(LOG_TAG, "Could not toggle " + entry.file, error);
            }
            final Exception toggleFailure = failure;
            Tools.runOnUiThread(() -> {
                if (!isAdded() || mRecyclerView == null) return;
                entry.pending = false;
                if (toggleFailure != null) {
                    Toast.makeText(requireContext(), R.string.instance_content_enable_failed, Toast.LENGTH_LONG).show();
                }
                applySearchAndFilter();
            });
        });
    }

    private void openCategoryFolder() {
        if (mGameDirectory == null || mCategory == null) return;
        File folder = InstanceContentFiles.getCategoryDirectory(mGameDirectory, mCategory);
        if (!folder.isDirectory() && !folder.mkdirs() && !folder.isDirectory()) {
            Toast.makeText(requireContext(), R.string.instance_open_folder_failed, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Tools.openPath(requireContext(), folder, false);
        } catch (Exception error) {
            Toast.makeText(requireContext(), R.string.instance_open_folder_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void onBackgroundPicked(Uri uri) {
        if (uri == null || getContext() == null || mProfileKey == null) return;
        final String key = mProfileKey;
        final Context context = requireContext().getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            Exception failure = null;
            try {
                saveBackgroundImage(context, uri, key);
            } catch (Exception error) {
                failure = error;
                Log.e(LOG_TAG, "Could not save the selected instance background", error);
            }
            final Exception saveError = failure;
            Tools.runOnUiThread(() -> {
                if (!isAdded() || mBackgroundImage == null || !key.equals(mProfileKey)) return;
                if (saveError == null) {
                    loadBackgroundForProfile();
                    Toast.makeText(requireContext(), R.string.instance_content_background_set, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), R.string.instance_content_background_failed, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void saveBackgroundImage(Context context, Uri uri, String profileKey) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Image provider returned no stream");
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Image dimensions are invalid");

        int sampleSize = 1;
        while (bounds.outWidth / sampleSize > MAX_BACKGROUND_WIDTH * 2 ||
                bounds.outHeight / sampleSize > MAX_BACKGROUND_HEIGHT * 2) {
            sampleSize *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bitmap;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Image provider returned no stream");
            bitmap = BitmapFactory.decodeStream(input, null, options);
        }
        if (bitmap == null) throw new IOException("Could not decode selected image");

        File destination = getBackgroundFile(context, profileKey);
        File parent = destination.getParentFile();
        if (parent == null || !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            bitmap.recycle();
            throw new IOException("Could not create the background directory");
        }
        File temporary = new File(parent, destination.getName() + ".tmp");
        boolean moved = false;
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output)) {
                    throw new IOException("Could not encode selected image");
                }
                output.getFD().sync();
            }
            android.system.Os.rename(temporary.getAbsolutePath(), destination.getAbsolutePath());
            moved = true;
        } finally {
            bitmap.recycle();
            if (!moved && temporary.exists() && !temporary.delete()) {
                Log.w(LOG_TAG, "Could not remove temporary instance background");
            }
        }
    }

    private void loadBackgroundForProfile() {
        if (mBackgroundImage == null || getContext() == null || mProfileKey == null) return;
        final ImageView target = mBackgroundImage;
        final File background = getBackgroundFile(requireContext(), mProfileKey);
        final String expectedPath = background.getAbsolutePath();
        target.setTag(expectedPath);
        mClearBackground.setVisibility(background.isFile() ? View.VISIBLE : View.GONE);
        if (!background.isFile()) {
            target.setImageDrawable(null);
            return;
        }
        PojavApplication.sExecutorService.execute(() -> {
            Bitmap bitmap = decodeBackground(background);
            if (bitmap == null) return;
            Tools.runOnUiThread(() -> {
                if (target == mBackgroundImage && expectedPath.equals(target.getTag())) {
                    target.setImageBitmap(bitmap);
                } else {
                    bitmap.recycle();
                }
            });
        });
    }

    private static Bitmap decodeBackground(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sampleSize = 1;
        while (bounds.outWidth / sampleSize > MAX_BACKGROUND_WIDTH ||
                bounds.outHeight / sampleSize > MAX_BACKGROUND_HEIGHT) sampleSize *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private void clearBackground() {
        if (mProfileKey == null || getContext() == null) return;
        final String key = mProfileKey;
        Context context = requireContext().getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            File file = getBackgroundFile(context, key);
            boolean removed = !file.exists() || file.delete();
            Tools.runOnUiThread(() -> {
                if (!isAdded() || mBackgroundImage == null || !key.equals(mProfileKey)) return;
                if (removed) {
                    mBackgroundImage.setImageDrawable(null);
                    mClearBackground.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), R.string.instance_content_background_cleared, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), R.string.instance_content_background_failed, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private static File getBackgroundFile(Context context, String profileKey) {
        File directory = new File(context.getFilesDir(), BACKGROUND_DIRECTORY);
        return new File(directory, sha256(profileKey) + ".jpg");
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            return result.toString();
        } catch (Exception impossible) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private enum ContentFilter { ALL, ENABLED, DISABLED }

    private static final class ProfileChoice {
        final String key;
        final String name;
        final MinecraftProfile profile;
        final File gameDirectory;

        ProfileChoice(String key, String name, MinecraftProfile profile, File gameDirectory) {
            this.key = key;
            this.name = name;
            this.profile = profile;
            this.gameDirectory = gameDirectory;
        }
    }

    private interface ToggleListener {
        void onToggle(InstanceContentFiles.Entry entry, boolean enabled);
    }

    private static final class ContentAdapter extends RecyclerView.Adapter<ContentViewHolder> {
        private final ArrayList<InstanceContentFiles.Entry> entries = new ArrayList<>();
        private final ToggleListener listener;

        ContentAdapter(ToggleListener listener) {
            this.listener = listener;
        }

        void setEntries(List<InstanceContentFiles.Entry> values) {
            entries.clear();
            entries.addAll(values);
            notifyDataSetChanged();
        }

        void notifyEntryChanged(InstanceContentFiles.Entry entry) {
            int index = entries.indexOf(entry);
            if (index >= 0) notifyItemChanged(index);
        }

        @NonNull
        @Override
        public ContentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_instance_content, parent, false);
            return new ContentViewHolder(row);
        }

        @Override
        public void onBindViewHolder(@NonNull ContentViewHolder holder, int position) {
            InstanceContentFiles.Entry entry = entries.get(position);
            holder.name.setText(entry.displayName());
            String detail = entry.file.isDirectory() ? "Folder" : entry.file.getName();
            holder.state.setText(holder.itemView.getContext().getString(
                    entry.enabled ? R.string.instance_content_state_enabled : R.string.instance_content_state_disabled,
                    detail));
            int icon = entry.category == InstanceContentFiles.Category.MODS
                    ? R.drawable.ic_add_modded
                    : entry.category == InstanceContentFiles.Category.WORLDS
                    ? R.drawable.ic_folder : R.drawable.ic_folder_managed;
            holder.icon.setImageResource(icon);

            holder.toggle.setOnCheckedChangeListener(null);
            holder.toggle.setChecked(entry.enabled);
            holder.toggle.setEnabled(!entry.pending);
            holder.toggle.setOnCheckedChangeListener((button, checked) -> {
                if (!entry.pending && entry.enabled != checked) {
                    listener.onToggle(entry, checked);
                }
            });
            holder.itemView.setOnClickListener(v -> {
                if (!entry.pending) holder.toggle.setChecked(!entry.enabled);
            });
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }
    }

    private static final class ContentViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView state;
        final SwitchCompat toggle;

        ContentViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.instance_content_item_icon);
            name = itemView.findViewById(R.id.instance_content_item_name);
            state = itemView.findViewById(R.id.instance_content_item_state);
            toggle = itemView.findViewById(R.id.instance_content_item_toggle);
        }
    }
}
