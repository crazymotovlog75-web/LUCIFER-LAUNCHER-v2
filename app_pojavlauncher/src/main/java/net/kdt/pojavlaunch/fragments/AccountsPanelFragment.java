package net.kdt.pojavlaunch.fragments;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.liquid.Liquid;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Liquid Crystal — the in-app account switcher ("Who is playing?", mockup screen 02).
 *
 * This replaces the old drop-down account spinner. It is a normal fragment inside the
 * launcher window: no separate window, no dialog, no spinner popup. Accounts live as one
 * {@code <name>.json} per account inside {@link Tools#DIR_ACCOUNT_NEW}, which is also what
 * the launcher core reads, so switching here switches the account everywhere.
 */
public class AccountsPanelFragment extends Fragment {
    public static final String TAG = "LC_ACCOUNTS_PANEL";

    private LinearLayout mAccountList;
    private TextView mCount;

    public AccountsPanelFragment() {
        super(R.layout.fragment_accounts_panel);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mAccountList = view.findViewById(R.id.lc_account_list);
        mCount = view.findViewById(R.id.lc_accounts_count);

        View close = view.findViewById(R.id.lc_accounts_close);
        close.setOnClickListener(v -> {
            Liquid.pressFeedback(v);
            closePanel();
        });

        view.findViewById(R.id.lc_account_add).setOnClickListener(v -> {
            Liquid.pressFeedback(v);
            openAuth(SelectAuthFragment.class, SelectAuthFragment.TAG);
        });
        view.findViewById(R.id.lc_account_ms).setOnClickListener(v -> {
            Liquid.pressFeedback(v);
            openAuth(MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG);
        });
        view.findViewById(R.id.lc_account_elyby).setOnClickListener(v -> {
            Liquid.pressFeedback(v);
            openAuth(ElybyLoginFragment.class, ElybyLoginFragment.TAG);
        });
        view.findViewById(R.id.lc_account_local).setOnClickListener(v -> {
            Liquid.pressFeedback(v);
            Tools.hasNoOnlineProfileDialog(requireActivity(),
                    () -> openAuth(LocalLoginFragment.class, LocalLoginFragment.TAG));
        });

        Liquid.reveal(view.findViewById(R.id.lc_accounts_sheet), 40L);
        reloadAccounts();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Coming back from a login screen should already show the new account.
        if (mAccountList != null) reloadAccounts();
    }

    // ── account list ─────────────────────────────────────────────────────────

    private void reloadAccounts() {
        if (mAccountList == null) return;
        mAccountList.removeAllViews();

        List<String> names = accountNames();
        if (mCount != null) mCount.setText(String.valueOf(names.size()));

        if (names.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.lc_accounts_none);
            empty.setTextColor(getResources().getColor(R.color.lc_text_dim));
            empty.setTextSize(13f);
            empty.setGravity(android.view.Gravity.CENTER);
            int pad = (int) (getResources().getDisplayMetrics().density * 18f);
            empty.setPadding(pad, pad, pad, pad);
            mAccountList.addView(empty);
            return;
        }

        String current = PojavProfile.getCurrentProfileName(requireContext());
        for (String name : names) {
            View row = getLayoutInflater().inflate(R.layout.item_lc_account, mAccountList, false);
            bindRow(row, name, name.equals(current));
            mAccountList.addView(row);
            Liquid.reveal(row, 60L);
        }
    }

    private List<String> accountNames() {
        List<String> names = new ArrayList<>();
        File folder = new File(Tools.DIR_ACCOUNT_NEW);
        File[] files = folder.listFiles();
        if (files == null) return names;
        for (File file : files) {
            String fileName = file.getName();
            if (!fileName.endsWith(".json") || !file.isFile()) continue;
            names.add(fileName.substring(0, fileName.length() - 5));
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private void bindRow(View row, final String name, boolean active) {
        TextView nameView = row.findViewById(R.id.lc_account_name);
        TextView badge = row.findViewById(R.id.lc_account_badge);
        TextView subtitle = row.findViewById(R.id.lc_account_subtitle);
        ImageView avatar = row.findViewById(R.id.lc_account_avatar);
        View check = row.findViewById(R.id.lc_account_check);
        View delete = row.findViewById(R.id.lc_account_delete);

        MinecraftAccount account = MinecraftAccount.load(name);

        nameView.setText(name);
        check.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
        if (active) row.setBackgroundResource(R.drawable.lc_glass_card_active);

        boolean online = account != null && account.accessToken != null
                && !"0".equals(account.accessToken);
        if (account != null && account.isMicrosoft) {
            badge.setText(R.string.lc_account_badge_msa);
        } else if (online) {
            badge.setText(R.string.lc_account_badge_ely);
            badge.setBackgroundResource(R.drawable.lc_badge_mint);
            badge.setTextColor(getResources().getColor(R.color.lc_mint));
        } else {
            badge.setText(R.string.lc_account_badge_local);
        }
        subtitle.setText(online ? R.string.lc_account_sub_online : R.string.lc_account_sub_local);

        loadAvatar(avatar, account, name);

        row.setOnClickListener(v -> switchTo(name));
        check.setOnClickListener(v -> switchTo(name));
        delete.setOnClickListener(v -> confirmRemove(name));
    }

    private void loadAvatar(final ImageView avatar, final MinecraftAccount account, final String name) {
        PojavApplication.sExecutorService.execute(() -> {
            Bitmap head = null;
            try {
                if (account != null) head = account.getSkinFace();
                if (head == null) head = MinecraftAccount.getSkinFace(name);
            } catch (Exception ignored) {
                // any skin failure just falls back to the steve head already set on the view
            }
            if (head == null) return;
            final Bitmap bitmap = head;
            Tools.runOnUiThread(() -> {
                if (isAdded() && avatar != null) avatar.setImageBitmap(bitmap);
            });
        });
    }

    private void switchTo(String name) {
        PojavProfile.setCurrentProfile(requireContext(), name);
        Toast.makeText(requireContext(), getString(R.string.lc_account_switched, name),
                Toast.LENGTH_SHORT).show();
        reloadAccounts();
    }

    private void confirmRemove(final String name) {
        if (getActivity() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.lc_account_remove)
                .setMessage(R.string.warning_remove_account)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    File accountFile = new File(Tools.DIR_ACCOUNT_NEW, name + ".json");
                    if (accountFile.exists()) accountFile.delete();
                    Toast.makeText(requireContext(), R.string.lc_account_removed,
                            Toast.LENGTH_SHORT).show();
                    reloadAccounts();
                })
                .show();
    }

    // ── navigation ───────────────────────────────────────────────────────────

    /** Sign-in flows open inside the launcher too: right pane in landscape, full screen otherwise. */
    private void openAuth(Class<? extends Fragment> fragmentClass, String tag) {
        Fragment parent = getParentFragment();
        if (parent instanceof MainMenuFragment) {
            ((MainMenuFragment) parent).openChildPane(fragmentClass, tag, null);
        } else {
            Tools.swapFragment(requireActivity(), fragmentClass, tag, null);
        }
    }

    private void closePanel() {
        FragmentManager manager = getParentFragmentManager();
        if (manager.getBackStackEntryCount() > 0) {
            manager.popBackStack();
        } else {
            Tools.swapFragment(requireActivity(), MainMenuFragment.class, MainMenuFragment.TAG, null);
        }
    }
}
