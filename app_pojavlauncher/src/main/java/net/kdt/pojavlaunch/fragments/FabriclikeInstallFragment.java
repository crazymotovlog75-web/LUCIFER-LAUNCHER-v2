package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import android.view.animation.LayoutAnimationController;
import android.view.animation.AnimationUtils;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.modloaders.FabriclikeDownloadTask;
import net.kdt.pojavlaunch.modloaders.FabriclikeUtils;
import net.kdt.pojavlaunch.modloaders.FabricVersion;
import net.kdt.pojavlaunch.modloaders.FabricVersionAdapter;
import net.kdt.pojavlaunch.modloaders.ModloaderDownloadListener;
import net.kdt.pojavlaunch.modloaders.ModloaderListenerProxy;
import net.kdt.pojavlaunch.modloaders.modpacks.SelfReferencingFuture;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Future;

public abstract class FabriclikeInstallFragment extends Fragment implements ModloaderDownloadListener, CompoundButton.OnCheckedChangeListener {
    private final FabriclikeUtils mFabriclikeUtils;
    private final String mExtraTag;
    private Spinner mGameVersionSpinner;
    private FabricVersion[] mGameVersionArray;
    private Future<?> mGameVersionFuture;
    private String mSelectedGameVersion;
    private Spinner mLoaderVersionSpinner;
    private FabricVersion[] mLoaderVersionArray;
    private Future<?> mLoaderVersionFuture;
    private String mSelectedLoaderVersion;
    private ProgressBar mProgressBar;
    private Button mStartButton;
    private View mRetryView;
    private CheckBox mOnlyStableCheckbox;
    private int mSelectedLoaderPosition = -1;
    private int mSelectedGamePosition = -1;
    private int mGameRequestGeneration;
    private int mLoaderRequestGeneration;

    // UI references
    private android.widget.ViewFlipper mStepFlipper;
    private ListView mGameVerList;
    private ListView mLoaderVerList;
    private FrameLayout mInstallOverlay;
    private FrameLayout mSuccessOverlay;
    private TextView mInstallStatusTitle;
    private TextView mInstallStatusSubtitle;
    private ProgressBar mDeterminateProgress;
    private Button mSuccessDoneButton;

    protected FabriclikeInstallFragment(FabriclikeUtils mFabriclikeUtils, String mFragmentTag) {
        super(R.layout.fragment_fabric_install);
        this.mFabriclikeUtils = mFabriclikeUtils;
        this.mExtraTag = mFragmentTag + "_proxy";
    }

    private boolean isFragmentUiAvailable() {
        return isAdded() && getView() != null && mStartButton != null && getContext() != null &&
                getActivity() != null && !isRemoving() && !isDetached();
    }

    private void applyListAnimations(android.widget.ListView listView) {
        Context context = getContext();
        if (!isFragmentUiAvailable() || context == null || listView == null || listView.getAdapter() == null) return;
        LayoutAnimationController controller = AnimationUtils.loadLayoutAnimation(
            context, R.anim.list_item_enter);
        listView.setLayoutAnimation(controller);
        listView.scheduleLayoutAnimation();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Install button
        mStartButton = view.findViewById(R.id.fabric_installer_start_button);
        mStartButton.setOnClickListener(this::onClickStart);
        mStartButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80)
                        .setInterpolator(new FastOutSlowInInterpolator()).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120)
                        .setInterpolator(new FastOutSlowInInterpolator()).start();
                    break;
            }
            return false;
        });

        // Hidden spinners (preserve backend logic)
        mGameVersionSpinner = view.findViewById(R.id.fabric_installer_game_ver_spinner);
        mGameVersionSpinner.setOnItemSelectedListener(new GameVersionSelectedListener());
        mLoaderVersionSpinner = view.findViewById(R.id.fabric_installer_loader_ver_spinner);
        mLoaderVersionSpinner.setOnItemSelectedListener(new LoaderVersionSelectedListener());

        mProgressBar = view.findViewById(R.id.fabric_installer_progress_bar);
        mRetryView = view.findViewById(R.id.fabric_installer_retry_layout);
        mOnlyStableCheckbox = view.findViewById(R.id.fabric_installer_only_stable_checkbox);
        mOnlyStableCheckbox.setOnCheckedChangeListener(this);
        view.findViewById(R.id.fabric_installer_retry_button).setOnClickListener(this::onClickRetry);

        // ViewFlipper and lists
        mStepFlipper = view.findViewById(R.id.fabric_step_flipper);
        mGameVerList = view.findViewById(R.id.fabric_game_ver_list);
        mLoaderVerList = view.findViewById(R.id.fabric_loader_ver_list);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            mGameVerList.setNestedScrollingEnabled(true);
            mLoaderVerList.setNestedScrollingEnabled(true);
        }

        // Overlays
        mInstallOverlay = view.findViewById(R.id.fabric_install_overlay);
        mSuccessOverlay = view.findViewById(R.id.fabric_success_overlay);
        mInstallStatusTitle = view.findViewById(R.id.fabric_install_status_title);
        mInstallStatusSubtitle = view.findViewById(R.id.fabric_install_status_subtitle);
        mDeterminateProgress = view.findViewById(R.id.fabric_install_determinate_progress);
        mSuccessDoneButton = view.findViewById(R.id.fabric_success_done_button);
        mSuccessDoneButton.setOnClickListener(v -> onSuccessDone());

        // Game version list click
        mGameVerList.setOnItemClickListener((parent, v, position, id) -> {
            if (!isFragmentUiAvailable()) return;
            FabricVersion selected = (FabricVersion) parent.getItemAtPosition(position);
            if (selected == null) {
                Log.w("FabricInstall", "Game version click: selected is null at position " + position);
                return;
            }

            mSelectedGamePosition = position;
            mSelectedGameVersion = selected.version;
            Log.d("LuciferLauncher", "Game Version selected: " + mSelectedGameVersion);

            ArrayAdapter<FabricVersion> spinnerAdapter = (ArrayAdapter<FabricVersion>) mGameVersionSpinner.getAdapter();
            if (spinnerAdapter == null) {
                Log.w("FabricInstall", "Game version click: spinner adapter is null");
                return;
            }
            for (int i = 0; i < spinnerAdapter.getCount(); i++) {
                if (spinnerAdapter.getItem(i) == selected) {
                    mGameVersionSpinner.setSelection(i);
                    break;
                }
            }

            TextView badge = view.findViewById(R.id.fabric_installer_label_loader_ver);
            if (badge != null) badge.setText(selected.version);

            Context context = getContext();
            if (!isFragmentUiAvailable() || context == null) return;
            mStepFlipper.setInAnimation(context, R.anim.screen_slide_in);
            mStepFlipper.setOutAnimation(context, R.anim.screen_slide_out);
            mStepFlipper.setDisplayedChild(1);

            // Reset loader selection when game version changes
            mSelectedLoaderPosition = -1;
            mSelectedLoaderVersion = null;
            if (mStartButton != null) {
                mStartButton.setEnabled(true);
            }

            // CRITICAL: Programmatic setSelection() does NOT fire OnItemSelectedListener.
            // We must explicitly trigger loader version fetch here.
            Log.d("LuciferLauncher", "Triggering loader version fetch for: " + mSelectedGameVersion);
            cancelFutureChecked(mLoaderVersionFuture);
            updateLoaderVersions();
        });

        // Loader version list click
        mLoaderVerList.setOnItemClickListener((parent, v, position, id) -> {
            if (!isFragmentUiAvailable()) return;
            FabricVersion selected = (FabricVersion) parent.getItemAtPosition(position);
            if (selected == null) return;

            mSelectedLoaderPosition = position;
            mSelectedLoaderVersion = selected.version;

            ArrayAdapter<FabricVersion> spinnerAdapter = (ArrayAdapter<FabricVersion>) mLoaderVersionSpinner.getAdapter();
            if (spinnerAdapter != null) {
                for (int i = 0; i < spinnerAdapter.getCount(); i++) {
                    if (spinnerAdapter.getItem(i) == selected) {
                        mLoaderVersionSpinner.setSelection(i);
                        break;
                    }
                }
            }

            // Update list selection visuals
            FabricVersionAdapter adapter = (FabricVersionAdapter) parent.getAdapter();
            if (adapter != null) {
                adapter.setSelectedPosition(position);
            }

            mStartButton.setEnabled(true);
            mStartButton.animate().alpha(1f).setDuration(150).start();
        });

        // Back button
        view.findViewById(R.id.fabric_step2_back_btn).setOnClickListener(v -> {
            Context context = getContext();
            if (!isFragmentUiAvailable() || context == null) return;
            mStartButton.clearAnimation();
            mStepFlipper.setInAnimation(context, R.anim.screen_slide_in);
            mStepFlipper.setOutAnimation(context, R.anim.screen_slide_out);
            mStepFlipper.setDisplayedChild(0);
        });
        view.findViewById(R.id.fabric_step3_back_btn).setOnClickListener(v ->
                getParentFragmentManager().popBackStack());

        // Proxy restore
        ModloaderListenerProxy proxy = getListenerProxy();
        if (proxy != null) {
            mStartButton.setEnabled(false);
            showInstallOverlay();
            proxy.attachListener(this);
        } else {
            mStartButton.setEnabled(true);
            // Rebind dynamic selections to newly created spinner views after rotation/re-entry.
            mSelectedGameVersion = null;
            mSelectedLoaderVersion = null;
            mLoaderVersionArray = null;
        }
        updateGameVersions();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!isFragmentUiAvailable() || getListenerProxy() != null) return;
        if (mGameVersionFuture != null && mGameVersionFuture.isCancelled() && mGameVersionArray == null) {
            updateGameVersions();
        } else if (mLoaderVersionFuture != null && mLoaderVersionFuture.isCancelled() &&
                mSelectedGameVersion != null && mLoaderVersionArray == null) {
            updateLoaderVersions();
        }
    }

    @Override
    public void onStop() {
        cancelFutureChecked(mGameVersionFuture);
        cancelFutureChecked(mLoaderVersionFuture);
        if (mProgressBar != null) mProgressBar.setVisibility(View.GONE);
        ModloaderListenerProxy proxy = getListenerProxy();
        if (proxy != null) proxy.detachListener();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        mGameRequestGeneration++;
        mLoaderRequestGeneration++;
        cancelFutureChecked(mGameVersionFuture);
        cancelFutureChecked(mLoaderVersionFuture);
        ModloaderListenerProxy proxy = getListenerProxy();
        if (proxy != null) proxy.detachListener();
        mGameVersionSpinner = null;
        mLoaderVersionSpinner = null;
        mProgressBar = null;
        mStartButton = null;
        mRetryView = null;
        mOnlyStableCheckbox = null;
        mStepFlipper = null;
        mGameVerList = null;
        mLoaderVerList = null;
        mInstallOverlay = null;
        mSuccessOverlay = null;
        mInstallStatusTitle = null;
        mInstallStatusSubtitle = null;
        mDeterminateProgress = null;
        mSuccessDoneButton = null;
        super.onDestroyView();
    }

    private void onClickStart(View v) {
        Log.d("FabricInstall", "STEP 1: Install button clicked");
        Log.d("FabricInstall", "STEP 2: Selected MC version: " + mSelectedGameVersion);
        Log.d("FabricInstall", "STEP 3: Selected Loader version: " + mSelectedLoaderVersion);

        if (!isFragmentUiAvailable()) {
            Log.e("FabricInstall", "Fragment UI is not available");
            return;
        }

        if (mSelectedGameVersion == null) {
            Log.w("FabricInstall", "Minecraft version is null");
            Toast.makeText(v.getContext(), "Please select a Minecraft version first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mSelectedLoaderVersion == null) {
            Log.w("FabricInstall", "Fabric Loader version is null");
            if (mLoaderVersionFuture != null && !mLoaderVersionFuture.isDone()) {
                Toast.makeText(v.getContext(), "Loading loader versions, please wait...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(v.getContext(), "Please select a Fabric Loader version", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (ProgressKeeper.hasOngoingTasks()) {
            Log.w("FabricInstall", "Tasks are already ongoing");
            Toast.makeText(v.getContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return;
        }

        Log.d("FabricInstall", "STEP 4: Starting installation");

        // Show install overlay
        showInstallOverlay();

        ModloaderListenerProxy proxy = new ModloaderListenerProxy();
        FabriclikeDownloadTask fabricDownloadTask = new FabriclikeDownloadTask(proxy, mFabriclikeUtils,
                mSelectedGameVersion, mSelectedLoaderVersion, true);
        proxy.attachListener(this);
        setListenerProxy(proxy);
        mStartButton.setEnabled(false);
        Log.d("LuciferLauncher", "Installation Started");
        PojavApplication.sExecutorService.execute(fabricDownloadTask);
    }

    private void showInstallOverlay() {
        if (mInstallOverlay == null || !isFragmentUiAvailable()) return;
        mInstallOverlay.setAlpha(0f);
        mInstallOverlay.setVisibility(View.VISIBLE);
        mInstallOverlay.animate().alpha(1f).setDuration(200).start();
        updateInstallStatus("Installing " + mFabriclikeUtils.getName() + "...", "Downloading loader metadata");
    }

    private void hideInstallOverlay() {
        if (mInstallOverlay == null) return;
        mInstallOverlay.animate().alpha(0f).setDuration(200).withEndAction(() -> mInstallOverlay.setVisibility(View.GONE)).start();
    }

    private void updateInstallStatus(String title, String subtitle) {
        if (mInstallStatusTitle != null) mInstallStatusTitle.setText(title);
        if (mInstallStatusSubtitle != null) mInstallStatusSubtitle.setText(subtitle);
    }

    private void showSuccessOverlay() {
        if (mSuccessOverlay == null || !isFragmentUiAvailable()) return;
        hideInstallOverlay();
        mSuccessOverlay.setAlpha(0f);
        mSuccessOverlay.setVisibility(View.VISIBLE);
        mSuccessOverlay.animate().alpha(1f).setDuration(250).start();

        TextView msg = mSuccessOverlay.findViewById(R.id.fabric_success_message);
        if (msg != null && mSelectedGameVersion != null && mSelectedLoaderVersion != null) {
            msg.setText(mFabriclikeUtils.getName() + " " + mSelectedLoaderVersion + " for Minecraft " + mSelectedGameVersion + " installed successfully.");
        }
    }

    private void onSuccessDone() {
        if (!isFragmentUiAvailable()) return;
        getParentFragmentManager().popBackStackImmediate();
    }

    private void onClickRetry(View v) {
        if (!isFragmentUiAvailable()) return;
        mStartButton.setEnabled(false);
        mRetryView.setVisibility(View.GONE);
        mLoaderVersionSpinner.setAdapter(null);
        if(mGameVersionArray == null) {
            mGameVersionSpinner.setAdapter(null);
            updateGameVersions();
            return;
        }
        updateLoaderVersions();
    }

    private void restoreUiState() {
        if (!isFragmentUiAvailable()) return;
        hideInstallOverlay();
        mStartButton.setVisibility(View.VISIBLE);
        mStartButton.setEnabled(true);
        mStartButton.animate().alpha(1f).translationY(0f).setDuration(200).start();
        mProgressBar.setVisibility(View.GONE);
    }

    @Override
    public void onDownloadFinished(File downloadedFile) {
        Log.d("FabricInstall", "STEP 6: Installation finished");
        Tools.runOnUiThread(()->{
            if (!isFragmentUiAvailable()) return;
            Log.d("LuciferLauncher", "Installation Finished");
            ModloaderListenerProxy proxy = getListenerProxy();
            if (proxy != null) proxy.detachListener();
            setListenerProxy(null);
            showSuccessOverlay();
        });
    }

    @Override
    public void onDataNotAvailable() {
        Log.e("FabricInstall", "STEP 6: Installation finished (failed: data not available)");
        Tools.runOnUiThread(()->{
            Context context = getContext();
            if (!isFragmentUiAvailable() || context == null) return;
            ModloaderListenerProxy proxy = getListenerProxy();
            if (proxy != null) proxy.detachListener();
            setListenerProxy(null);
            restoreUiState();
            showInstallFailure(context, new IOException(
                    context.getString(R.string.fabric_dl_cant_read_meta, mFabriclikeUtils.getName())));
        });
    }

    @Override
    public void onDownloadError(Exception e) {
        Log.e("FabricInstall", "STEP 6: Installation finished (failed: " + e.getMessage() + ")", e);
        Tools.runOnUiThread(()-> {
            Context context = getContext();
            if (!isFragmentUiAvailable() || context == null) return;
            ModloaderListenerProxy proxy = getListenerProxy();
            if (proxy != null) proxy.detachListener();
            setListenerProxy(null);
            restoreUiState();
            showInstallFailure(context, e);
        });
    }

    private void showInstallFailure(Context context, Exception error) {
        String details = error == null || error.getMessage() == null
                ? "Check your connection and confirm the selected versions are still supported."
                : error.getMessage().trim();
        if (details.length() > 300) details = details.substring(0, 300) + "…";
        new AlertDialog.Builder(context)
                .setTitle(mFabriclikeUtils.getName() + " installation failed")
                .setMessage("The profile was not installed. You can retry after checking your connection and selected versions.\n\n" + details)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.global_retry, (dialog, which) -> {
                    if (isFragmentUiAvailable() && mStartButton != null) onClickStart(mStartButton);
                })
                .show();
    }

    private void cancelFutureChecked(Future<?> future) {
        if(future != null && !future.isCancelled()) future.cancel(true);
    }

    private void startLoading() {
        if (mProgressBar != null) mProgressBar.setVisibility(View.VISIBLE);
        if (mStartButton != null) mStartButton.setEnabled(false);
    }

    private void stopLoading() {
        if (mProgressBar != null) mProgressBar.setVisibility(View.GONE);
    }

    @Nullable
    private ArrayAdapter<FabricVersion> createAdapter(FabricVersion[] fabricVersions, boolean onlyStable, boolean showBadges) {
        Context context = getContext();
        if (!isFragmentUiAvailable() || context == null || fabricVersions == null) return null;
        ArrayList<FabricVersion> filteredVersions = new ArrayList<>(fabricVersions.length);
        for(FabricVersion fabricVersion : fabricVersions) {
            if(!onlyStable || fabricVersion.stable) filteredVersions.add(fabricVersion);
        }
        filteredVersions.trimToSize();
        int layoutRes = showBadges ? R.layout.list_item_fabric_version : R.layout.item_fabric_version;
        return new FabricVersionAdapter(context, layoutRes, filteredVersions, showBadges);
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if (!isFragmentUiAvailable()) return;
        updateGameSpinner();
        updateLoaderSpinner();
    }

    class LoaderVersionSelectedListener implements AdapterView.OnItemSelectedListener {

        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            if (!isFragmentUiAvailable() || adapterView.getAdapter() == null) return;
            FabricVersion selected = (FabricVersion) adapterView.getAdapter().getItem(i);
            mSelectedLoaderVersion = selected != null ? selected.version : null;
            Log.d("LuciferLauncher", "Selected Loader Version = " + mSelectedLoaderVersion);
            mStartButton.setEnabled(mSelectedGameVersion != null && mSelectedLoaderVersion != null);
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {
            if (!isFragmentUiAvailable()) return;
            mSelectedLoaderVersion = null;
            mStartButton.setEnabled(false);
        }
    }

    class LoadLoaderVersionsTask implements SelfReferencingFuture.FutureInterface {
        private final String gameVersion;
        private final int requestGeneration;

        LoadLoaderVersionsTask(String gameVersion, int requestGeneration) {
            this.gameVersion = gameVersion;
            this.requestGeneration = requestGeneration;
        }

        @Override
        public void run(Future<?> myFuture) {
            final FabricVersion[] cachedVersions = mFabriclikeUtils.getCachedLoaderVersions(gameVersion);
            final boolean hasCachedVersions = cachedVersions.length > 0;
            if (hasCachedVersions) {
                Tools.runOnUiThread(() -> {
                    if (myFuture.isCancelled() || requestGeneration != mLoaderRequestGeneration ||
                            !gameVersion.equals(mSelectedGameVersion) || !isFragmentUiAvailable()) return;
                    mLoaderVersionArray = cachedVersions;
                    updateLoaderSpinner();
                });
            }

            try {
                final FabricVersion[] versions = mFabriclikeUtils.downloadLoaderVersions(gameVersion);
                Tools.runOnUiThread(() -> {
                    if (myFuture.isCancelled() || requestGeneration != mLoaderRequestGeneration ||
                            !gameVersion.equals(mSelectedGameVersion) || !isFragmentUiAvailable()) return;
                    mLoaderVersionArray = versions;
                    stopLoading();
                    updateLoaderSpinner();
                    if (mRetryView != null) {
                        mRetryView.setVisibility(versions.length == 0 ? View.VISIBLE : View.GONE);
                    }
                    if (versions.length == 0 && isFragmentUiAvailable()) {
                        Toast.makeText(requireContext(),
                                "No compatible " + mFabriclikeUtils.getName() + " versions were returned for Minecraft " + gameVersion,
                                Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception error) {
                Log.e("FabricInstall", "Failed to load " + mFabriclikeUtils.getName() + " loader versions", error);
                Tools.runOnUiThread(() -> {
                    if (myFuture.isCancelled() || requestGeneration != mLoaderRequestGeneration ||
                            !gameVersion.equals(mSelectedGameVersion) || !isFragmentUiAvailable()) return;
                    stopLoading();
                    if (mRetryView != null) mRetryView.setVisibility(View.VISIBLE);
                    if (hasCachedVersions) {
                        Toast.makeText(requireContext(), R.string.modloader_using_cached_versions, Toast.LENGTH_SHORT).show();
                    } else {
                        Tools.showError(requireContext(), error);
                    }
                });
            }
        }
    }

    private void updateLoaderVersions() {
        if (!isFragmentUiAvailable()) return;
        String gameVersion = mSelectedGameVersion;
        int requestGeneration = ++mLoaderRequestGeneration;
        cancelFutureChecked(mLoaderVersionFuture);
        mLoaderVersionArray = null;
        mSelectedLoaderVersion = null;
        mSelectedLoaderPosition = -1;
        if (mRetryView != null) mRetryView.setVisibility(View.GONE);
        if (mLoaderVersionSpinner != null) mLoaderVersionSpinner.setAdapter(null);
        if (mLoaderVerList != null) mLoaderVerList.setAdapter(null);
        if (gameVersion == null || gameVersion.isEmpty()) {
            stopLoading();
            mStartButton.setEnabled(false);
            return;
        }
        startLoading();
        mLoaderVersionFuture = new SelfReferencingFuture(
                new LoadLoaderVersionsTask(gameVersion, requestGeneration))
                .startOnExecutor(PojavApplication.sExecutorService);
    }

    private void updateLoaderSpinner() {
        if(!isFragmentUiAvailable() || mLoaderVersionArray == null) {
            Log.w("LuciferLauncher", "updateLoaderSpinner: skipped (UI unavailable or no loader array)");
            return;
        }
        mLoaderVersionSpinner.setAlpha(0f);
        ArrayAdapter<FabricVersion> adapter = createAdapter(mLoaderVersionArray, false, true);
        if (adapter == null) {
            Log.w("LuciferLauncher", "updateLoaderSpinner: adapter is null");
            return;
        }
        Log.d("LuciferLauncher", "Game Version = " + mSelectedGameVersion);
        Log.d("LuciferLauncher", "updateLoaderSpinner: created adapter with " + adapter.getCount() + " items");
        mLoaderVersionSpinner.setAdapter(adapter);
        mLoaderVersionSpinner.animate().alpha(1f).setDuration(250).start();
        if (mLoaderVerList != null) {
            mLoaderVerList.setAdapter(adapter);
            applyListAnimations(mLoaderVerList);
            Log.d("LuciferLauncher", "updateLoaderSpinner: attached adapter to loader ListView");
        } else {
            Log.w("LuciferLauncher", "updateLoaderSpinner: mLoaderVerList is null!");
        }

        // Auto-select latest stable loader if available, else first item
        int selectedIndex = -1;
        if (adapter.getCount() > 0) {
            selectedIndex = 0;
            for (int i = 0; i < adapter.getCount(); i++) {
                FabricVersion ver = adapter.getItem(i);
                if (ver != null && ver.stable) {
                    selectedIndex = i;
                    break;
                }
            }
            mLoaderVersionSpinner.setSelection(selectedIndex);
            mSelectedLoaderVersion = adapter.getItem(selectedIndex).version;
            mSelectedLoaderPosition = selectedIndex;
            Log.d("LuciferLauncher", "updateLoaderSpinner: auto-selected loader " + mSelectedLoaderVersion);
        } else {
            mSelectedLoaderVersion = null;
            mSelectedLoaderPosition = -1;
            Log.w("LuciferLauncher", "updateLoaderSpinner: adapter is empty");
        }

        if (mLoaderVerList != null && adapter instanceof FabricVersionAdapter) {
            ((FabricVersionAdapter) adapter).setSelectedPosition(mSelectedLoaderPosition);
        }

        mStartButton.setEnabled(adapter.getCount() > 0 && mSelectedGameVersion != null &&
                mSelectedLoaderVersion != null);
    }

    class GameVersionSelectedListener implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            if (!isFragmentUiAvailable() || adapterView.getAdapter() == null) return;
            FabricVersion selected = (FabricVersion) adapterView.getAdapter().getItem(i);
            if (selected == null || selected.version == null) return;
            if (!selected.version.equals(mSelectedGameVersion)) {
                mSelectedGameVersion = selected.version;
                mSelectedLoaderVersion = null;
                Log.d("LuciferLauncher", "Selected MC Version = " + mSelectedGameVersion);
                updateLoaderVersions();
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {
            if (!isFragmentUiAvailable()) return;
            mSelectedGameVersion = null;
            mSelectedLoaderVersion = null;
            mLoaderRequestGeneration++;
            cancelFutureChecked(mLoaderVersionFuture);
            mLoaderVersionArray = null;
            if (mLoaderVersionSpinner != null) mLoaderVersionSpinner.setAdapter(null);
            if (mLoaderVerList != null) mLoaderVerList.setAdapter(null);
            mStartButton.setEnabled(false);
        }
    }

    class LoadGameVersionsTask implements SelfReferencingFuture.FutureInterface {
        private final int requestGeneration;

        LoadGameVersionsTask(int requestGeneration) {
            this.requestGeneration = requestGeneration;
        }

        @Override
        public void run(Future<?> myFuture) {
            final FabricVersion[] cachedVersions = mFabriclikeUtils.getCachedGameVersions();
            final boolean hasCachedVersions = cachedVersions.length > 0;
            if (hasCachedVersions) {
                Tools.runOnUiThread(() -> {
                    if (myFuture.isCancelled() || requestGeneration != mGameRequestGeneration ||
                            !isFragmentUiAvailable()) return;
                    mGameVersionArray = cachedVersions;
                    updateGameSpinner();
                });
            }

            try {
                final FabricVersion[] versions = mFabriclikeUtils.downloadGameVersions();
                Tools.runOnUiThread(() -> {
                    if (myFuture.isCancelled() || requestGeneration != mGameRequestGeneration ||
                            !isFragmentUiAvailable()) return;
                    mGameVersionArray = versions;
                    stopLoading();
                    updateGameSpinner();
                    if (mRetryView != null) mRetryView.setVisibility(versions.length == 0 ? View.VISIBLE : View.GONE);
                    if (versions.length == 0) {
                        Toast.makeText(requireContext(), "No Minecraft versions are available from the " +
                                mFabriclikeUtils.getName() + " API", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception error) {
                Log.e("FabricInstall", "Failed to load Minecraft versions for " + mFabriclikeUtils.getName(), error);
                Tools.runOnUiThread(() -> {
                    if (myFuture.isCancelled() || requestGeneration != mGameRequestGeneration ||
                            !isFragmentUiAvailable()) return;
                    stopLoading();
                    if (mRetryView != null) mRetryView.setVisibility(View.VISIBLE);
                    if (hasCachedVersions) {
                        Toast.makeText(requireContext(), R.string.modloader_using_cached_versions, Toast.LENGTH_SHORT).show();
                    } else {
                        Tools.showError(requireContext(), error);
                    }
                });
            }
        }
    }

    private void updateGameVersions() {
        if (!isFragmentUiAvailable()) return;
        int requestGeneration = ++mGameRequestGeneration;
        cancelFutureChecked(mGameVersionFuture);
        if (mRetryView != null) mRetryView.setVisibility(View.GONE);
        startLoading();
        mGameVersionFuture = new SelfReferencingFuture(new LoadGameVersionsTask(requestGeneration))
                .startOnExecutor(PojavApplication.sExecutorService);
    }

    private void updateGameSpinner() {
        if (!isFragmentUiAvailable() || mGameVersionArray == null) return;
        mGameVersionSpinner.setAlpha(0f);
        ArrayAdapter<FabricVersion> adapter = createAdapter(
                mGameVersionArray, mOnlyStableCheckbox.isChecked(), false);
        if (adapter == null) return;
        mGameVersionSpinner.setAdapter(adapter);
        if (adapter.getCount() == 0) {
            mSelectedGameVersion = null;
            mSelectedLoaderVersion = null;
            mStartButton.setEnabled(false);
            if (mRetryView != null) mRetryView.setVisibility(View.VISIBLE);
        }
        mGameVersionSpinner.animate().alpha(1f).setDuration(250).start();
        if (mGameVerList != null) {
            mGameVerList.setAdapter(adapter);
            applyListAnimations(mGameVerList);
        }
    }

    private ModloaderListenerProxy getListenerProxy() {
        return (ModloaderListenerProxy) ExtraCore.getValue(mExtraTag);
    }
    private void setListenerProxy(ModloaderListenerProxy listenerProxy) {
        ExtraCore.setValue(mExtraTag, listenerProxy);
    }
}
