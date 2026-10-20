package net.kdt.pojavlaunch.fragments;

import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.profiles.ProfileIconCache;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.util.Arrays;
import java.util.List;

public class HomeProfileAdapter extends RecyclerView.Adapter<HomeProfileAdapter.ViewHolder> {

    private static final String TAG = "HomeProfileAdapter";

    private static final int PAYLOAD_MOD_COUNT = 1;

    private final List<MinecraftProfile> mProfileList;
    private final List<String> mProfileKeys;
    private final OnProfileActionListener mListener;
    private String mSelectedProfileKey = "";
    // Pre-computed mod counts (0 = not loaded yet or no mods). Populated on background thread.
    private final int[] mModCountCache;
    private boolean mModCountsReady;

    public interface OnProfileActionListener {
        void onProfileSelect(String profileKey, MinecraftProfile profile);
        void onProfilePlay(String profileKey, MinecraftProfile profile);
        void onProfileEdit(String profileKey, MinecraftProfile profile);
        void onProfileAddShortcut(String profileKey, MinecraftProfile profile);
    }

    public HomeProfileAdapter(List<String> profileKeys, List<MinecraftProfile> profiles,
                              OnProfileActionListener listener) {
        mProfileKeys = profileKeys;
        mProfileList = profiles;
        mListener = listener;
        mModCountCache = new int[profiles.size()];
        setHasStableIds(true);
        // Kick off background loading of mod counts
        preloadModCounts();
    }

    public void setSelectedProfileKey(String profileKey) {
        String normalized = profileKey == null ? "" : profileKey;
        if (normalized.equals(mSelectedProfileKey)) return;
        mSelectedProfileKey = normalized;
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return mProfileKeys.get(position).hashCode();
    }

    /** Pre-compute mod counts on a background thread so onBindViewHolder never blocks on I/O. */
    private void preloadModCounts() {
        if (mProfileList.isEmpty()) return;
        PojavApplication.sExecutorService.execute(() -> {
            for (int i = 0; i < mProfileList.size(); i++) {
                MinecraftProfile profile = mProfileList.get(i);
                int count = 0;
                try {
                    java.io.File gameDir = profile.resolveGameDir();
                    if (gameDir != null) {
                        java.io.File modsDir = new java.io.File(gameDir, "mods");
                        if (modsDir.exists() && modsDir.isDirectory()) {
                            java.io.File[] files = modsDir.listFiles(f -> f.isFile() &&
                                    (f.getName().toLowerCase().endsWith(".jar") || f.getName().toLowerCase().endsWith(".jar.disabled")));
                            count = files != null ? files.length : 0;
                        }
                    }
                } catch (Throwable ignored) {}
                mModCountCache[i] = count;
            }
            mModCountsReady = true;
            // Use payload update so only mod count TextView rebinds, not the full card
            Tools.runOnUiThread(() -> notifyItemRangeChanged(0, mProfileList.size(), PAYLOAD_MOD_COUNT));
        });
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_home_profile_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        onBindViewHolder(holder, position, null);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        // Payload-based partial binding: skip expensive icon binding when only mod count changed
        if (!payloads.isEmpty()) {
            for (Object payload : payloads) {
                if (payload instanceof Integer && (Integer) payload == PAYLOAD_MOD_COUNT) {
                    int modCount = mModCountCache[position];
                    holder.tvModCount.setText("Installed Mods: " + modCount);
                    return;
                }
            }
        }

        MinecraftProfile profile = mProfileList.get(position);
        String profileKey = mProfileKeys.get(position);
        boolean selectedProfile = profileKey.equals(mSelectedProfileKey);
        holder.cardRoot.setSelected(selectedProfile);
        holder.cardRoot.setBackgroundResource(selectedProfile
                ? R.drawable.bg_home_profile_card_selected : R.drawable.bg_home_profile_card);

        holder.tvName.setText(profile.name != null ? profile.name : "");

        StringBuilder meta = new StringBuilder();
        if (profile.lastVersionId != null && !profile.lastVersionId.isEmpty()) {
            meta.append(profile.lastVersionId);
        }
        if (profile.lastUsed != null && !profile.lastUsed.isEmpty()) {
            if (meta.length() > 0) meta.append(" \u2022 ");
            String date = profile.lastUsed.length() >= 10
                    ? profile.lastUsed.substring(0, 10) : profile.lastUsed;
            meta.append(date);
        }
        holder.tvMeta.setText(meta.toString());

        // Read from pre-computed cache — never blocks on I/O
        int modCount = mModCountCache[position];
        holder.tvModCount.setText("Installed Mods: " + modCount);

        bindIcon(holder.imgIcon, profileKey, profile);

        holder.cardRoot.setOnClickListener(v -> {
            if (mListener != null) mListener.onProfileSelect(profileKey, profile);
        });
        holder.btnEdit.setOnClickListener(v -> {
            if (mListener != null) mListener.onProfileEdit(profileKey, profile);
        });

        holder.cardRoot.setOnLongClickListener(v -> {
            if (mListener != null) {
                mListener.onProfileAddShortcut(profileKey, profile);
            }
            return true;
        });

        holder.btnPlay.setOnClickListener(v -> {
            if (mListener != null) mListener.onProfilePlay(profileKey, profile);
        });
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        // Clear image and request identity so delayed bitmap decodes cannot land on a recycled row.
        holder.imgIcon.setTag(null);
        holder.imgIcon.setImageDrawable(null);
    }

    /**
     * Binds cached vector icons synchronously; base64 bitmap icons are decoded off the UI thread.
     * A typed fallback is shown immediately while large/custom profile icons load.
     */
    private void bindIcon(ImageView target, String profileKey, MinecraftProfile profile) {
        final String icon = profile.icon;
        if (icon != null && icon.startsWith("data:")) {
            Drawable fallback = resolveTypeFallback(target, profile.lastVersionId);
            if (fallback == null) fallback = ContextCompat.getDrawable(
                    target.getContext(), R.drawable.lucifer_launcher_emblem);
            target.setTag(profileKey);
            target.setImageDrawable(fallback);
            final android.content.res.Resources resources = target.getResources();
            PojavApplication.sExecutorService.execute(() -> {
                Drawable decoded = null;
                try {
                    decoded = ProfileIconCache.fetchIcon(resources, profileKey, icon);
                } catch (Exception error) {
                    Log.w(TAG, "Icon load failed for " + profileKey, error);
                }
                final Drawable result = decoded;
                if (result == null) return;
                Tools.runOnUiThread(() -> {
                    if (profileKey.equals(target.getTag())) target.setImageDrawable(result);
                });
            });
            return;
        }

        Drawable drawable = null;
        try {
            drawable = ProfileIconCache.fetchIcon(target.getResources(), profileKey, icon);
        } catch (Exception e) {
            Log.w(TAG, "Icon load failed for " + profileKey, e);
        }
        if (drawable == null) drawable = resolveTypeFallback(target, profile.lastVersionId);
        if (drawable == null) drawable = ContextCompat.getDrawable(
                target.getContext(), R.drawable.lucifer_launcher_emblem);
        target.setTag(profileKey);
        target.setImageDrawable(drawable);
    }

    /**
     * Picks a type-aware fallback icon based on the profile's MC version id
     * (e.g. "fabric-loader-1.20.1" → fabric icon). Avoids empty boxes when
     * the base64 icon payload is missing or corrupted.
     */
    private Drawable resolveTypeFallback(ImageView target, String lastVersionId) {
        if (lastVersionId == null) return null;
        String lower = lastVersionId.toLowerCase();
        int resId = -1;
        if (lower.contains("fabric")) resId = R.drawable.ic_fabric;
        else if (lower.contains("quilt")) resId = R.drawable.ic_quilt;
        else if (lower.contains("forge")) resId = R.drawable.lucifer_launcher_emblem;
        else if (lower.contains("neoforge")) resId = R.drawable.lucifer_launcher_emblem;
        if (resId == -1) return null;
        return ContextCompat.getDrawable(target.getContext(), resId);
    }

    @Override
    public int getItemCount() {
        return mProfileList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View cardRoot;
        final ImageView imgIcon;
        final TextView tvName;
        final TextView tvMeta;
        final TextView tvModCount;
        final ImageButton btnEdit;
        final FrameLayout btnPlay;

        ViewHolder(View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.card_profile_root);
            imgIcon = itemView.findViewById(R.id.img_profile_icon);
            tvName = itemView.findViewById(R.id.tv_profile_name);
            tvMeta = itemView.findViewById(R.id.tv_profile_meta);
            tvModCount = itemView.findViewById(R.id.tv_profile_mod_count);
            btnEdit = itemView.findViewById(R.id.btn_profile_edit);
            btnPlay = itemView.findViewById(R.id.btn_profile_play);
        }
    }
}
