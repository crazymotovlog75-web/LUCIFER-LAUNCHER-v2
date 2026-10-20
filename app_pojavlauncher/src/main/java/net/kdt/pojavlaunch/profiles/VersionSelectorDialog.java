package net.kdt.pojavlaunch.profiles;

import static net.kdt.pojavlaunch.extra.ExtraCore.getValue;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.ExpandableListView;

import androidx.appcompat.app.AlertDialog;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

public class VersionSelectorDialog {
    public static void open(Context context, boolean hideCustomVersions, VersionSelectorListener listener) {
        open(context, hideCustomVersions, Tools.getCurrentMinecraftProfile(), listener);
    }

    public static void open(Context context, boolean hideCustomVersions, MinecraftProfile instanceProfile,
                            VersionSelectorListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        ExpandableListView expandableListView = (ExpandableListView) LayoutInflater.from(context)
                .inflate(R.layout.dialog_expendable_list_view , null);
        JMinecraftVersionList jMinecraftVersionList = (JMinecraftVersionList) getValue(ExtraConstants.RELEASE_TABLE);
        JMinecraftVersionList.Version[] versionArray;
        if(jMinecraftVersionList == null || jMinecraftVersionList.versions == null) versionArray = new JMinecraftVersionList.Version[0];
        else versionArray = jMinecraftVersionList.versions;
        VersionListAdapter adapter = new VersionListAdapter(
                versionArray, hideCustomVersions, context, instanceProfile);

        expandableListView.setAdapter(adapter);
        builder.setView(expandableListView);
        AlertDialog dialog = builder.show();

        // Keep every version family reachable and start at the end of the expanded list, where
        // older/snapshot choices live. The selected instance version is tinted by the adapter.
        for (int group = 0; group < adapter.getGroupCount(); group++) {
            if (adapter.getChildrenCount(group) > 0) expandableListView.expandGroup(group);
        }
        expandableListView.post(() -> {
            int lastPosition = expandableListView.getCount() - 1;
            if (lastPosition >= 0) expandableListView.setSelection(lastPosition);
        });

        expandableListView.setOnChildClickListener((parent, v1, groupPosition, childPosition, id) -> {
            String version = adapter.getChild(groupPosition, childPosition);
            listener.onVersionSelected(version, adapter.isSnapshotSelected(groupPosition));
            dialog.dismiss();
            return true;
        });
    }
}
