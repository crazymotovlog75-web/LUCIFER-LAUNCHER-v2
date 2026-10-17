package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;

/** Displays the creator and friend credits supplied for Lucifer Launcher. */
public class CreatorTeamFragment extends Fragment {
    public static final String TAG = "CreatorTeamFragment";

    public CreatorTeamFragment() {
        super(R.layout.fragment_creator_team);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        View back = view.findViewById(R.id.btn_creator_team_back);
        if (back != null) {
            back.setOnClickListener(v -> getParentFragmentManager().popBackStack());
        }
    }
}
