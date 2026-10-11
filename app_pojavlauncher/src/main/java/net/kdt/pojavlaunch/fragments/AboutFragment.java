package net.kdt.pojavlaunch.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.BuildConfig;
import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;

/** Feature directory and working shortcuts for Lucifer Launcher. */
public class AboutFragment extends Fragment {
    public static final String TAG = "AboutFragment";

    public AboutFragment() {
        super(R.layout.fragment_about);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        TextView version = view.findViewById(R.id.about_version);
        version.setText(getString(R.string.lucifer_version, BuildConfig.VERSION_NAME));

        view.findViewById(R.id.about_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        view.findViewById(R.id.about_instances).setOnClickListener(v -> openPane(
                InstancePickerFragment.class, InstancePickerFragment.TAG, null));
        view.findViewById(R.id.about_versions).setOnClickListener(v -> openPane(
                ProfileTypeSelectFragment.class, ProfileTypeSelectFragment.TAG, null));
        view.findViewById(R.id.about_mods).setOnClickListener(v -> openPane(
                ModsSearchFragment.class, ModsSearchFragment.TAG, null));
        view.findViewById(R.id.about_skins).setOnClickListener(v -> openPane(
                SkinManagerFragment.class, SkinManagerFragment.TAG, null));
        view.findViewById(R.id.about_performance).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), LauncherPreferenceFragment.class,
                        LauncherActivity.SETTING_FRAGMENT_TAG, null));
        view.findViewById(R.id.about_controls).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        view.findViewById(R.id.about_cursor).setOnClickListener(v -> openPane(
                CursorCustomizationFragment.class, CursorCustomizationFragment.TAG, null));
        view.findViewById(R.id.about_servers).setOnClickListener(v -> openPane(
                ServerHubFragment.class, ServerHubFragment.TAG, null));
        view.findViewById(R.id.about_pro).setOnClickListener(v -> openPane(
                ClientFeaturesFragment.class, ClientFeaturesFragment.TAG, null));
        view.findViewById(R.id.about_wiki).setOnClickListener(v ->
                Tools.openURL(requireActivity(), Tools.URL_HOME));
        view.findViewById(R.id.about_discord).setOnClickListener(v ->
                Tools.openURL(requireActivity(), getString(R.string.discord_invite)));
    }

    private void openPane(Class<? extends Fragment> fragmentClass, String tag, @Nullable Bundle args) {
        Fragment parent = getParentFragment();
        while (parent != null && !(parent instanceof MainMenuFragment)) {
            parent = parent.getParentFragment();
        }
        if (parent instanceof MainMenuFragment) {
            ((MainMenuFragment) parent).openChildPane(fragmentClass, tag, args);
        } else {
            Tools.swapFragment(requireActivity(), fragmentClass, tag, args);
        }
    }
}
