package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.animation.LayoutTransition;
import android.view.animation.DecelerateInterpolator;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ImageButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.InstanceManager;
import net.kdt.pojavlaunch.instances.InstancePathResolver;
import net.kdt.pojavlaunch.modloaders.InstalledModAdapter;
import net.kdt.pojavlaunch.modloaders.LocalPackAdapter;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.RTSpinnerAdapter;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.profiles.ProfileIconCache;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.utils.CropperUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import android.net.Uri;
import android.widget.Toast;
import androidx.activity.result.contract.ActivityResultContracts;
import org.apache.commons.io.IOUtils;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.File;

public class ProfileEditorFragment extends Fragment implements CropperUtils.CropperListener{
    public static final String TAG = "ProfileEditorFragment";
    public static final String DELETED_PROFILE = "deleted_profile";

    private static final String TAG_ASYNC = "ProfileEditorAsync";

    private String mProfileKey;
    private MinecraftProfile mTempProfile = null;
    private boolean mCreatingProfile;
    private String mOriginalVersionId;
    private String mOriginalProfileName;
    private String mValueToConsume = "";
    private Button mSaveButton, mDeleteButton, mControlSelectButton, mGameDirButton, mVersionSelectButton;
    private ImageButton mModsAddButton, mModsImport, mResourcePacksFolder, mShaderPacksFolder, mResourcePacksImport, mShaderPacksImport;
    private RecyclerView mModsRecycler, mResourcePacksRecycler, mShaderPacksRecycler;
    private TextView mModsHeader, mModsEmpty, mResourcePacksEmpty, mShaderPacksEmpty;
    private Spinner mDefaultRuntime, mDefaultRenderer;
    private EditText mDefaultName, mDefaultJvmArgument;
    private TextView mDefaultPath, mDefaultVersion, mDefaultControl;
    private ImageView mProfileIcon;
    private final ActivityResultLauncher<?> mCropperLauncher = CropperUtils.registerCropper(this, this);

    private final ActivityResultLauncher<String> mModPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> handleImport(uri, "mods")
    );

    private final ActivityResultLauncher<String> mResourcePackPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> handleImport(uri, "resourcepacks")
    );

    private final ActivityResultLauncher<String> mShaderPackPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> handleImport(uri, "shaderpacks")
    );

    private List<String> mRenderNames;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mBgExecutor = PojavApplication.sExecutorService;
    private boolean mAsyncLoadComplete = false;

    public ProfileEditorFragment(){
        super(R.layout.fragment_profile_editor);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Paths, which can be changed
        String value = (String) ExtraCore.consumeValue(ExtraConstants.FILE_SELECTOR);
        if(value != null){
            if(mValueToConsume.equals(FileSelectorFragment.BUNDLE_SELECT_FOLDER)){
                mTempProfile.gameDir = value;
            }else{
                mTempProfile.controlFile = value;
            }
        }
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bindViews(view);

        // Smooth 60/90 FPS layout animations
        View rootLayout = view.findViewById(R.id.fragment_profile_editor_root);
        if (rootLayout instanceof ViewGroup) {
            LayoutTransition transition = new LayoutTransition();
            transition.enableTransitionType(LayoutTransition.CHANGING);
            transition.setDuration(250);
            transition.setInterpolator(LayoutTransition.CHANGE_APPEARING,
                    new DecelerateInterpolator());
            ((ViewGroup) rootLayout).setLayoutTransition(transition);
        }

        // Hardware acceleration
        if (getActivity() != null) {
            getActivity().getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        }

        // Hardware layer for the entire content block → smooth 60fps animations
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Apply 200ms scale-up reveal with DecelerateInterpolator
        view.setAlpha(0f);
        view.setScaleX(0.96f);
        view.setScaleY(0.96f);
        view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (getView() != null) {
                        getView().setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                })
                .start();

        // Renderer spinner setup (synchronous, fast — just string list)
        Tools.RenderersList renderersList = Tools.getCompatibleRenderers(view.getContext());
        mRenderNames = renderersList.rendererIds;
        List<String> renderList = new ArrayList<>(renderersList.rendererDisplayNames.length + 1);
        renderList.addAll(Arrays.asList(renderersList.rendererDisplayNames));
        renderList.add(view.getContext().getString(R.string.global_default));
        mDefaultRenderer.setAdapter(new ArrayAdapter<>(getContext(), R.layout.item_simple_list_1, renderList));

        // Set up behaviors
        mSaveButton.setOnClickListener(v -> {
            if (mTempProfile == null) return;
            // 1) Read inputs on the main thread (touching UI state)
            readInputsFromUi();
            String sourceInstanceIdForFork = null;
            if (!mCreatingProfile && !sameValue(mOriginalVersionId, mTempProfile.lastVersionId)) {
                // Changing a profile's Minecraft version now forks a new isolated copy.
                sourceInstanceIdForFork = mTempProfile.instanceId;
                MinecraftProfile fork = new MinecraftProfile(mTempProfile);
                fork.isDefault = false;
                fork.created = MinecraftProfile.currentTimestamp();
                fork.lastUsed = null;
                mTempProfile = fork;
                mProfileKey = LauncherProfiles.getFreeProfileKey();
                fork.instanceId = InstancePathResolver.instanceIdForProfileKey(mProfileKey);
                fork.gameDir = InstancePathResolver.gameDirectoryValue(fork.instanceId);
                mCreatingProfile = true;
                if (sameValue(mOriginalProfileName, fork.name)) {
                    fork.name = makeUniqueProfileName(suggestProfileName(fork.lastVersionId), mProfileKey);
                }
            }
            if (mTempProfile.instanceId == null || mTempProfile.instanceId.trim().isEmpty()) {
                mTempProfile.instanceId = InstancePathResolver.instanceIdForProfileKey(mProfileKey);
            }
            mTempProfile.gameDir = InstancePathResolver.gameDirectoryValue(mTempProfile.instanceId);
            if (mTempProfile.created == null || mTempProfile.created.isEmpty()) {
                mTempProfile.created = MinecraftProfile.currentTimestamp();
            }
            if (mTempProfile.name == null || mTempProfile.name.trim().isEmpty()) {
                mTempProfile.name = makeUniqueProfileName(
                        suggestProfileName(mTempProfile.lastVersionId), mProfileKey);
            }
            ProfileIconCache.dropIcon(mProfileKey);
            final String saveKey = mProfileKey;
            final MinecraftProfile profileToSave = mTempProfile;
            final String forkSourceInstanceId = sourceInstanceIdForFork;
            // 2) Disable button immediately to prevent double-tap
            mSaveButton.setEnabled(false);
            // 3) Initialize/copy the instance and persist its profile off the UI thread.
            mBgExecutor.execute(() -> {
                Exception failure = null;
                try {
                    InstanceManager manager = Tools.getInstanceManager();
                    if (forkSourceInstanceId != null) {
                        manager.duplicateInstance(forkSourceInstanceId, profileToSave.instanceId,
                                saveKey, profileToSave.name, profileToSave.lastVersionId);
                    } else {
                        manager.initializeInstance(profileToSave.instanceId, saveKey,
                                profileToSave.name, profileToSave.lastVersionId);
                        manager.updateMetadata(profileToSave.instanceId, saveKey,
                                profileToSave.name, profileToSave.lastVersionId);
                    }
                    LauncherProfiles.mainProfileJson.profiles.put(saveKey, profileToSave);
                    LauncherProfiles.write();
                    ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, saveKey);
                } catch (Exception error) {
                    failure = error;
                }
                final Exception saveFailure = failure;
                mMainHandler.post(() -> {
                    if (!isAdded()) return;
                    if (saveFailure != null) {
                        mSaveButton.setEnabled(true);
                        Toast.makeText(requireContext(), "Could not save instance: " +
                                saveFailure.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    Fragment parentFrag = getParentFragment();
                    if (parentFrag instanceof MainMenuFragment) {
                        MainMenuFragment mmf = (MainMenuFragment) parentFrag;
                        mmf.clearRightPane();
                        mmf.reloadSpinner();
                    } else {
                        Tools.backToMainMenu(requireActivity());
                    }
                });
            });
        });

        mDeleteButton.setOnClickListener(v -> {
            if(LauncherProfiles.mainProfileJson.profiles.size() > 1){
                ProfileIconCache.dropIcon(mProfileKey);
                mDeleteButton.setEnabled(false);
                final String deleteKey = mProfileKey;
                final String deleteInstanceId = mTempProfile != null ? mTempProfile.instanceId : null;
                // Remove the profile and its isolated folder off the UI thread. Legacy source folders
                // are not under instances/ and are deliberately never deleted here.
                mBgExecutor.execute(() -> {
                    Exception failure = null;
                    String replacementSelection = null;
                    try {
                        if (deleteInstanceId != null) {
                            Tools.getInstanceManager().deleteInstance(deleteInstanceId);
                        }
                        LauncherProfiles.mainProfileJson.profiles.remove(deleteKey);
                        String selectedKey = LauncherPreferences.DEFAULT_PREF.getString(
                                LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
                        if (deleteKey.equals(selectedKey)) {
                            java.util.List<java.util.Map.Entry<String, MinecraftProfile>> remaining =
                                    LauncherProfiles.getProfilesInDisplayOrder();
                            if (!remaining.isEmpty()) replacementSelection = remaining.get(0).getKey();
                            if (replacementSelection != null) {
                                LauncherPreferences.DEFAULT_PREF.edit()
                                        .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE,
                                                replacementSelection).apply();
                            }
                        }
                        LauncherProfiles.write();
                        ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER,
                                replacementSelection != null ? replacementSelection
                                        : ProfileEditorFragment.DELETED_PROFILE);
                    } catch (Exception error) {
                        failure = error;
                    }
                    final Exception deleteFailure = failure;
                    mMainHandler.post(() -> {
                        if (!isAdded()) return;
                        if (deleteFailure != null) {
                            mDeleteButton.setEnabled(true);
                            Toast.makeText(requireContext(), "Could not delete instance: " +
                                    deleteFailure.getMessage(), Toast.LENGTH_LONG).show();
                            return;
                        }
                        Fragment parentFrag = getParentFragment();
                        if (parentFrag instanceof MainMenuFragment) {
                            MainMenuFragment mmf = (MainMenuFragment) parentFrag;
                            mmf.clearRightPane();
                            mmf.reloadSpinner();
                        } else {
                            Tools.removeCurrentFragment(requireActivity());
                        }
                    });
                });
            } else {
                Fragment parentFrag = getParentFragment();
                if (parentFrag instanceof MainMenuFragment) {
                    ((MainMenuFragment) parentFrag).clearRightPane();
                } else {
                    Tools.removeCurrentFragment(requireActivity());
                }
            }
        });


        View.OnClickListener gameDirListener = getGameDirListener();
        mGameDirButton.setOnClickListener(gameDirListener);
        mDefaultPath.setOnClickListener(gameDirListener);

        View.OnClickListener controlSelectListener = getControlSelectListener();
        mControlSelectButton.setOnClickListener(controlSelectListener);
        mDefaultControl.setOnClickListener(controlSelectListener);

        // Setup the expendable list behavior
        View.OnClickListener versionSelectListener = getVersionSelectListener();
        mVersionSelectButton.setOnClickListener(versionSelectListener);
        mDefaultVersion.setOnClickListener(versionSelectListener);

        // Set up the icon change click listener
        mProfileIcon.setOnClickListener(v -> CropperUtils.startCropper(mCropperLauncher));

        mModsAddButton.setOnClickListener(v -> {
            File gameDir = Tools.getGameDirPath(mTempProfile);
            Tools.openPath(v.getContext(), new File(gameDir, "mods"), false);
        });

        mResourcePacksFolder.setOnClickListener(v -> {
            File gameDir = Tools.getGameDirPath(mTempProfile);
            Tools.openPath(v.getContext(), new File(gameDir, "resourcepacks"), false);
        });

        mShaderPacksFolder.setOnClickListener(v -> {
            File gameDir = Tools.getGameDirPath(mTempProfile);
            Tools.openPath(v.getContext(), new File(gameDir, "shaderpacks"), false);
        });

        mModsImport = view.findViewById(R.id.vprof_editor_mods_import);
        mResourcePacksImport = view.findViewById(R.id.vprof_editor_resource_packs_import);
        mShaderPacksImport = view.findViewById(R.id.vprof_editor_shader_packs_import);

        mModsImport.setOnClickListener(v -> mModPicker.launch("*/*"));
        mResourcePacksImport.setOnClickListener(v -> mResourcePackPicker.launch("*/*"));
        mShaderPacksImport.setOnClickListener(v -> mShaderPackPicker.launch("*/*"));

        loadValuesAsync(LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, ""), view.getContext());
    }

    private void handleImport(Uri uri, String subDir) {
        if (uri == null) return;
        mBgExecutor.execute(() -> {
            try (InputStream is = requireContext().getContentResolver().openInputStream(uri)) {
                File gameDir = Tools.getGameDirPath(mTempProfile);
                File destDir = new File(gameDir, subDir);
                if (!destDir.exists()) destDir.mkdirs();

                String fileName = Tools.getFileName(requireContext(), uri);
                if (fileName == null) fileName = "imported_" + System.currentTimeMillis() + ".zip";

                File destFile = new File(destDir, fileName);
                try (FileOutputStream os = new FileOutputStream(destFile)) {
                    IOUtils.copy(is, os);
                }
                mMainHandler.post(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Imported successfully!", Toast.LENGTH_SHORT).show();
                    setupPacksListsAsync();
                });
            } catch (Exception e) {
                mMainHandler.post(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /** Navigate to a fragment — stays inside the right pane when running as a child fragment. */
    private void navigateToFragment(Class<? extends Fragment> fragmentClass, String tag, Bundle args) {
        Fragment parent = getParentFragment();
        if (parent instanceof MainMenuFragment) {
            ((MainMenuFragment) parent).openChildPane(fragmentClass, tag, args);
        } else {
            Tools.swapFragment(requireActivity(), fragmentClass, tag, args);
        }
    }

    private View.OnClickListener getGameDirListener() {
        return v -> {
            if (mTempProfile == null) return;
            try {
                File instanceRoot = Tools.getGameDirPath(mTempProfile);
                if (!instanceRoot.isDirectory() && !instanceRoot.mkdirs() && !instanceRoot.isDirectory()) {
                    throw new IOException("Could not create instance folder");
                }
                Tools.openPath(v.getContext(), instanceRoot, false);
            } catch (Exception error) {
                Toast.makeText(v.getContext(), "Could not open instance folder", Toast.LENGTH_LONG).show();
            }
        };
    }

    private View.OnClickListener getControlSelectListener() {
        return v -> {
            Bundle bundle = new Bundle(3);
            bundle.putBoolean(FileSelectorFragment.BUNDLE_SELECT_FOLDER, false);
            bundle.putString(FileSelectorFragment.BUNDLE_ROOT_PATH, Tools.CTRLMAP_PATH);
            mValueToConsume = FileSelectorFragment.BUNDLE_SELECT_FILE;

            navigateToFragment(FileSelectorFragment.class, FileSelectorFragment.TAG, bundle);
        };
    }

    private View.OnClickListener getVersionSelectListener() {
        return v -> VersionSelectorDialog.open(v.getContext(), false, mTempProfile, (id, snapshot)-> {
            mTempProfile.lastVersionId = id;
            mDefaultVersion.setText(id);
        });
    }

    /**
     * Loads profile values on a background thread to prevent main-thread jank
     * (file I/O, JSON parsing, runtime enumeration).
     */
    private void loadValuesAsync(@NonNull String profile, @NonNull Context context) {
        if (mTempProfile == null) {
            mTempProfile = getProfile(profile);
        }

        // Static UI population (cheap, on UI thread) — only string setters
        mProfileIcon.setImageDrawable(
                ProfileIconCache.fetchIcon(getResources(), mProfileKey, mTempProfile.icon)
        );
        mDefaultVersion.setText(mTempProfile.lastVersionId);
        mDefaultJvmArgument.setText(mTempProfile.javaArgs == null ? "" : mTempProfile.javaArgs);
        mDefaultName.setText(mTempProfile.name);
        mDefaultPath.setText(mTempProfile.gameDir == null ? "" : mTempProfile.gameDir);
        mDefaultControl.setText(mTempProfile.controlFile == null ? "" : mTempProfile.controlFile);

        // TODO: Remove this jank when it's not relevant anymore
        if ("vulkan_zink".equals(mTempProfile.pojavRendererName)) {
            mTempProfile.pojavRendererName = "opengles3_desktopgl_zink_kopper";
        }

        // All expensive work goes to background
        mBgExecutor.execute(() -> {
            // Runtime enumeration (file I/O over runtime dir)
            List<Runtime> runtimes = MultiRTUtils.getInstalledRuntimes();
            int jvmIdx = runtimes.indexOf(new Runtime("<Default>"));
            if (mTempProfile.javaDir != null) {
                String selectedRuntime = mTempProfile.javaDir.substring(Tools.LAUNCHERPROFILES_RTPREFIX.length());
                int nindex = runtimes.indexOf(new Runtime(selectedRuntime));
                if (nindex != -1) jvmIdx = nindex;
            }
            if (jvmIdx == -1) jvmIdx = runtimes.size() - 1;
            final int finalJvmIndex = jvmIdx;

            // Directory listings for mods / resourcepacks / shaderpacks
            File gameDir = Tools.getGameDirPath(mTempProfile);
            final File modsDir = new File(gameDir, "mods");
            final File resourcePacksDir = new File(gameDir, "resourcepacks");
            final File shaderPacksDir = new File(gameDir, "shaderpacks");

            mMainHandler.post(() -> {
                if (!isAdded()) return;
                mDefaultRuntime.setAdapter(new RTSpinnerAdapter(context, runtimes));
                mDefaultRuntime.setSelection(Math.max(0, finalJvmIndex));

                int rendererIdx = mDefaultRenderer.getAdapter().getCount() - 1;
                if (mTempProfile.pojavRendererName != null) {
                    int nindex = mRenderNames.indexOf(mTempProfile.pojavRendererName);
                    if (nindex != -1) rendererIdx = nindex;
                }
                final int finalRendererIndex = rendererIdx;

                mDefaultRenderer.setSelection(finalRendererIndex);

                bindPacksAdapters(modsDir, resourcePacksDir, shaderPacksDir);

                // Hide Mods section for Vanilla & OptiFine profiles only if no mod files exist.
                if (mTempProfile != null) {
                    boolean hideMods = (mTempProfile.isOptiFine() || mTempProfile.isVanilla()) && mTempProfile.getInstalledModsCount() == 0;
                    int visibility = hideMods ? View.GONE : View.VISIBLE;
                    if (mModsHeader != null) mModsHeader.setVisibility(visibility);
                    if (mModsImport != null) mModsImport.setVisibility(visibility);
                    if (mModsAddButton != null) mModsAddButton.setVisibility(visibility);
                    if (mModsRecycler != null) mModsRecycler.setVisibility(visibility);
                    if (mModsEmpty != null) mModsEmpty.setVisibility(visibility);
                }
                mAsyncLoadComplete = true;
            });
        });
    }

    /**
     * Adapter binding only (directory listings were already collected on the
     * background thread — this avoids touching the filesystem from the UI thread).
     */
    private void bindPacksAdapters(File modsDir, File resourcePacksDir, File shaderPacksDir) {
        mModsRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        mModsRecycler.setItemAnimator(null);
        mModsRecycler.setAdapter(new InstalledModAdapter(modsDir, isEmpty ->
                mModsEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE)));

        mResourcePacksRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        mResourcePacksRecycler.setItemAnimator(null);
        mResourcePacksRecycler.setAdapter(new LocalPackAdapter(resourcePacksDir, isEmpty ->
                mResourcePacksEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE)));

        mShaderPacksRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        mShaderPacksRecycler.setItemAnimator(null);
        mShaderPacksRecycler.setAdapter(new LocalPackAdapter(shaderPacksDir, isEmpty ->
                mShaderPacksEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE)));
    }

    private void setupPacksListsAsync() {
        if (!mAsyncLoadComplete) return;
        mBgExecutor.execute(() -> {
            if (mTempProfile == null) return;
            File gameDir = Tools.getGameDirPath(mTempProfile);
            File modsDir = new File(gameDir, "mods");
            File resourcePacksDir = new File(gameDir, "resourcepacks");
            File shaderPacksDir = new File(gameDir, "shaderpacks");

            mMainHandler.post(() -> {
                if (!isAdded()) return;
                bindPacksAdapters(modsDir, resourcePacksDir, shaderPacksDir);
            });
        });
    }

    private boolean sameValue(@Nullable String left, @Nullable String right) {
        return left == null ? right == null : left.equals(right);
    }

    private String suggestProfileName(@Nullable String versionId) {
        if (versionId == null || versionId.trim().isEmpty()) return "Minecraft Instance";
        String mcVersion = net.kdt.pojavlaunch.utils.ProfileDetection.extractMcFromVersionId(versionId);
        if (mcVersion != null && !mcVersion.isEmpty()) return "Minecraft " + mcVersion;
        if (MinecraftProfile.LATEST_RELEASE.equals(versionId)) return "Minecraft Latest Release";
        if (MinecraftProfile.LATEST_SNAPSHOT.equals(versionId)) return "Minecraft Latest Snapshot";
        return "Minecraft " + versionId;
    }

    private String makeUniqueProfileName(@NonNull String baseName, @Nullable String excludedKey) {
        String candidate = baseName;
        int suffix = 2;
        Map<String, MinecraftProfile> profiles = LauncherProfiles.mainProfileJson != null
                ? LauncherProfiles.mainProfileJson.profiles : null;
        while (profiles != null && hasProfileName(profiles, candidate, excludedKey)) {
            candidate = baseName + " (" + suffix++ + ")";
        }
        return candidate;
    }

    private boolean hasProfileName(@NonNull Map<String, MinecraftProfile> profiles,
                                   @NonNull String candidate, @Nullable String excludedKey) {
        for (Map.Entry<String, MinecraftProfile> entry : profiles.entrySet()) {
            if (sameValue(excludedKey, entry.getKey())) continue;
            MinecraftProfile profile = entry.getValue();
            if (profile != null && profile.name != null &&
                    candidate.equalsIgnoreCase(profile.name.trim())) return true;
        }
        return false;
    }

    private MinecraftProfile getProfile(@NonNull String profile){
        LauncherProfiles.load();
        if (getArguments() == null) {
            MinecraftProfile originalProfile = LauncherProfiles.mainProfileJson.profiles.get(profile);
            if (originalProfile != null) {
                mCreatingProfile = false;
                mOriginalVersionId = originalProfile.lastVersionId;
                mOriginalProfileName = originalProfile.name;
                mProfileKey = profile;
                return new MinecraftProfile(originalProfile);
            }
        }

        mCreatingProfile = true;
        mOriginalVersionId = null;
        mOriginalProfileName = null;
        mProfileKey = LauncherProfiles.getFreeProfileKey();
        MinecraftProfile profile = MinecraftProfile.createTemplate();
        profile.instanceId = InstancePathResolver.instanceIdForProfileKey(mProfileKey);
        profile.gameDir = InstancePathResolver.gameDirectoryValue(profile.instanceId);
        return profile;
    }


    private void bindViews(@NonNull View view){
        mDefaultControl = view.findViewById(R.id.vprof_editor_ctrl_spinner);
        mDefaultRuntime = view.findViewById(R.id.vprof_editor_spinner_runtime);
        mDefaultRenderer = view.findViewById(R.id.vprof_editor_profile_renderer);
        mDefaultVersion = view.findViewById(R.id.vprof_editor_version_spinner);

        mDefaultPath = view.findViewById(R.id.vprof_editor_path);
        mDefaultName = view.findViewById(R.id.vprof_editor_profile_name);
        mDefaultJvmArgument = view.findViewById(R.id.vprof_editor_jre_args);

        mSaveButton = view.findViewById(R.id.vprof_editor_save_button);
        mDeleteButton = view.findViewById(R.id.vprof_editor_delete_button);
        mControlSelectButton = view.findViewById(R.id.vprof_editor_ctrl_button);
        mVersionSelectButton = view.findViewById(R.id.vprof_editor_version_button);
        mGameDirButton = view.findViewById(R.id.vprof_editor_path_button);
        mProfileIcon = view.findViewById(R.id.vprof_editor_profile_icon);

        mModsHeader = view.findViewById(R.id.vprof_editor_mods_header);
        mModsAddButton = view.findViewById(R.id.vprof_editor_mods_add);
        mResourcePacksFolder = view.findViewById(R.id.vprof_editor_resource_packs_folder);
        mShaderPacksFolder = view.findViewById(R.id.vprof_editor_shader_packs_folder);
        mModsRecycler = view.findViewById(R.id.vprof_editor_mods_recycler);
        mResourcePacksRecycler = view.findViewById(R.id.vprof_editor_resource_packs_recycler);
        mShaderPacksRecycler = view.findViewById(R.id.vprof_editor_shader_packs_recycler);
        mModsEmpty = view.findViewById(R.id.vprof_editor_mods_empty);
        mResourcePacksEmpty = view.findViewById(R.id.vprof_editor_resource_packs_empty);
        mShaderPacksEmpty = view.findViewById(R.id.vprof_editor_shader_packs_empty);
        mResourcePacksImport = view.findViewById(R.id.vprof_editor_resource_packs_import);
        mShaderPacksImport = view.findViewById(R.id.vprof_editor_shader_packs_import);
    }

    private void readInputsFromUi() {
        if (mTempProfile == null) return;
        mTempProfile.lastVersionId = mDefaultVersion.getText().toString();
        mTempProfile.controlFile = mDefaultControl.getText().toString();
        mTempProfile.name = mDefaultName.getText().toString();
        mTempProfile.javaArgs = mDefaultJvmArgument.getText().toString()
                .replaceAll("[\r\n]+", " ")
                .trim();
        mTempProfile.gameDir = mTempProfile.instanceId != null
                ? InstancePathResolver.gameDirectoryValue(mTempProfile.instanceId) : null;

        if(mTempProfile.controlFile != null && mTempProfile.controlFile.isEmpty()) mTempProfile.controlFile = null;
        if(mTempProfile.javaArgs != null && mTempProfile.javaArgs.isEmpty()) mTempProfile.javaArgs = null;

        if (mDefaultRuntime.getSelectedItem() instanceof Runtime) {
            Runtime selectedRuntime = (Runtime) mDefaultRuntime.getSelectedItem();
            mTempProfile.javaDir = (selectedRuntime.name.equals("<Default>") || selectedRuntime.versionString == null)
                    ? null : Tools.LAUNCHERPROFILES_RTPREFIX + selectedRuntime.name;
        }

        if(mDefaultRenderer.getSelectedItemPosition() == mRenderNames.size()) mTempProfile.pojavRendererName = null;
        else mTempProfile.pojavRendererName = mRenderNames.get(mDefaultRenderer.getSelectedItemPosition());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mProfileKey != null) {
            ProfileIconCache.dropIcon(mProfileKey);
            Fragment parent = getParentFragment();
            if (parent instanceof MainMenuFragment) {
                ((MainMenuFragment) parent).reloadSpinner();
            }
        }
    }

    @Override
    public void onCropped(Bitmap contentBitmap) {
        mProfileIcon.setImageBitmap(contentBitmap);
        Log.i(TAG_ASYNC, "w="+contentBitmap.getWidth() +" h="+contentBitmap.getHeight());
        mBgExecutor.execute(() -> {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try (Base64OutputStream base64OutputStream = new Base64OutputStream(byteArrayOutputStream, Base64.NO_WRAP)) {
                contentBitmap.compress(
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.R ?
                        Bitmap.CompressFormat.WEBP:
                        Bitmap.CompressFormat.WEBP_LOSSY,
                    60,
                    base64OutputStream
                );
                base64OutputStream.flush();
                byteArrayOutputStream.flush();
            }catch (IOException e) {
                mMainHandler.post(() -> Tools.showErrorRemote(e));
                return;
            }
            String iconLine = new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);
            String dataUri = "data:image/webp;base64," + iconLine;
            mMainHandler.post(() -> {
                if (mTempProfile != null) mTempProfile.icon = dataUri;
            });
        });
    }

    @Override
    public void onFailed(Exception exception) {
        Tools.showErrorRemote(exception);
    }
}