package net.kdt.pojavlaunch.fragments;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.theme.ThemeManager;
import net.kdt.pojavlaunch.theme.WallpaperCatalog;

/** Full-width wallpaper picker with palettes applied as a real Activity theme. */
public final class WallpaperStudioFragment extends Fragment {
    public static final String TAG = "WALLPAPER_STUDIO";

    public WallpaperStudioFragment() {
        super(R.layout.fragment_wallpaper_studio);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView paletteInfo = view.findViewById(R.id.tv_wallpaper_palette_info);
        View accentSwatch = view.findViewById(R.id.wallpaper_swatch_accent);
        View secondarySwatch = view.findViewById(R.id.wallpaper_swatch_secondary);
        View surfaceSwatch = view.findViewById(R.id.wallpaper_swatch_surface);
        int selectedId = ThemeManager.getSelectedWallpaperId();
        WallpaperCatalog.Wallpaper selected = WallpaperCatalog.get(selectedId);

        if (selected != null) {
            String[] names = getResources().getStringArray(R.array.lucifer_wallpaper_names);
            paletteInfo.setText(names[selected.id]);
            setSwatchColor(accentSwatch, ContextCompat.getColor(requireContext(), selected.accentColorRes));
            setSwatchColor(secondarySwatch, ContextCompat.getColor(requireContext(), selected.secondaryColorRes));
            setSwatchColor(surfaceSwatch, ContextCompat.getColor(requireContext(), selected.surfaceColorRes));
        } else {
            paletteInfo.setText(R.string.wallpaper_custom_preset);
            setSwatchColor(accentSwatch, ThemeManager.getAccentColor(requireContext()));
            setSwatchColor(secondarySwatch, ThemeManager.getSecondaryColor(requireContext()));
            setSwatchColor(surfaceSwatch, ThemeManager.resolveColor(
                    requireContext(), R.attr.luciferSurface, 0xFF101820));
        }

        RecyclerView grid = view.findViewById(R.id.rv_wallpapers);
        GridLayoutManager gridLayout = new GridLayoutManager(requireContext(), 4);
        grid.setLayoutManager(gridLayout);
        grid.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int availableWidth = right - left - grid.getPaddingLeft() - grid.getPaddingRight();
            int targetCellWidth = Math.round(160 * getResources().getDisplayMetrics().density);
            int columns = Math.max(2, Math.min(4, availableWidth / Math.max(1, targetCellWidth)));
            if (gridLayout.getSpanCount() != columns) gridLayout.setSpanCount(columns);
        });
        grid.setHasFixedSize(true);
        grid.setAdapter(new WallpaperAdapter(
                getResources().getStringArray(R.array.lucifer_wallpaper_names),
                selectedId,
                wallpaperId -> {
                    if (wallpaperId == ThemeManager.getSelectedWallpaperId()) return;
                    ThemeManager.selectWallpaper(wallpaperId);
                    Toast.makeText(requireContext(), R.string.wallpaper_applied_toast, Toast.LENGTH_SHORT).show();
                    requireActivity().recreate();
                }));

        view.findViewById(R.id.btn_wallpaper_default).setOnClickListener(v -> {
            if (ThemeManager.getSelectedWallpaperId() == WallpaperCatalog.DEFAULT_ID) return;
            ThemeManager.resetToDefault();
            Toast.makeText(requireContext(), R.string.wallpaper_applied_toast, Toast.LENGTH_SHORT).show();
            requireActivity().recreate();
        });
    }

    private static void setSwatchColor(@NonNull View swatch, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        drawable.setStroke(2, 0xB3FFFFFF);
        swatch.setBackground(drawable);
        swatch.setContentDescription(String.format("#%06X", 0xFFFFFF & color));
    }

    private static final class WallpaperAdapter extends RecyclerView.Adapter<WallpaperViewHolder> {
        private final String[] names;
        private final int selectedId;
        private final OnWallpaperSelected listener;

        WallpaperAdapter(String[] names, int selectedId, OnWallpaperSelected listener) {
            this.names = names;
            this.selectedId = selectedId;
            this.listener = listener;
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            return WallpaperCatalog.ALL[position].id;
        }

        @NonNull
        @Override
        public WallpaperViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_wallpaper, parent, false);
            return new WallpaperViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull WallpaperViewHolder holder, int position) {
            WallpaperCatalog.Wallpaper wallpaper = WallpaperCatalog.ALL[position];
            boolean selected = wallpaper.id == selectedId;
            holder.itemView.setSelected(selected);
            holder.preview.setImageResource(wallpaper.previewRes);
            holder.name.setText(names[wallpaper.id]);
            holder.selectedBadge.setVisibility(selected ? View.VISIBLE : View.GONE);
            holder.itemView.setContentDescription(holder.itemView.getResources().getString(
                    selected ? R.string.wallpaper_card_selected_description
                            : R.string.wallpaper_card_description,
                    names[wallpaper.id]));
            holder.itemView.setOnClickListener(v -> listener.onSelected(wallpaper.id));
        }

        @Override
        public int getItemCount() {
            return WallpaperCatalog.ALL.length;
        }
    }

    private static final class WallpaperViewHolder extends RecyclerView.ViewHolder {
        final ImageView preview;
        final TextView name;
        final TextView selectedBadge;

        WallpaperViewHolder(@NonNull View itemView) {
            super(itemView);
            preview = itemView.findViewById(R.id.iv_wallpaper_preview);
            name = itemView.findViewById(R.id.tv_wallpaper_name);
            selectedBadge = itemView.findViewById(R.id.tv_wallpaper_selected);
        }
    }

    private interface OnWallpaperSelected {
        void onSelected(int wallpaperId);
    }
}
