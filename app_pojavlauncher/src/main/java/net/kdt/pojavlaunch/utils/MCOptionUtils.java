package net.kdt.pojavlaunch.utils;
import static org.lwjgl.glfw.CallbackBridge.windowHeight;
import static org.lwjgl.glfw.CallbackBridge.windowWidth;

import android.os.Build;
import android.os.FileObserver;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class MCOptionUtils {
    private static final HashMap<String,String> sParameterMap = new HashMap<>();
    private static final ArrayList<WeakReference<MCOptionListener>> sOptionListeners = new ArrayList<>();
    private static final String PERFORMANCE_BACKUP_FILE = ".lucifer-performance-options.json";
    private static FileObserver sFileObserver;
    private static String sOptionFolderPath = null;
    public interface MCOptionListener {
        /** Called when an option is changed. Don't know which one though */
        void onOptionChanged();
    }


    public static void load(){
        load(sOptionFolderPath == null
                ? Tools.getGameDirPath(net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles.getCurrentProfile()).getAbsolutePath()
                : sOptionFolderPath);
    }

    public static void load(@NonNull String folderPath) {
        File optionFile = new File(folderPath + "/options.txt");
        if(!optionFile.exists()) {
            try { // Needed for new instances I guess  :think:
                optionFile.createNewFile();
            } catch (IOException e) { e.printStackTrace(); }
        }

        if(sFileObserver == null || !Objects.equals(sOptionFolderPath, folderPath)){
            sOptionFolderPath = folderPath;
            setupFileObserver();
        }
        sOptionFolderPath = folderPath; // Yeah I know, it may be redundant

        sParameterMap.clear();

        try {
            BufferedReader reader = new BufferedReader(new FileReader(optionFile));
            String line;
            while ((line = reader.readLine()) != null) {
                int firstColonIndex = line.indexOf(':');
                if(firstColonIndex < 0) {
                    Log.w(Tools.APP_NAME, "No colon on line \""+line+"\", skipping");
                    continue;
                }
                sParameterMap.put(line.substring(0,firstColonIndex), line.substring(firstColonIndex+1));
            }
            reader.close();
        } catch (IOException e) {
            Log.w(Tools.APP_NAME, "Could not load options.txt", e);
        }
    }

    public static void set(String key, String value) {
        sParameterMap.put(key,value);
    }

    /** Set an array of String, instead of a simple value. Not supported on all options */
    public static void set(String key, List<String> values){
        sParameterMap.put(key, values.toString());
    }

    public static String get(String key){
        return sParameterMap.get(key);
    }

    /** @return A list of values from an array stored as a string */
    public static List<String> getAsList(String key){
        String value = get(key);

        // Fallback if the value doesn't exist
        if (value == null) return new ArrayList<>();

        // Remove the edges
        value = value.replace("[", "").replace("]", "");
        if (value.isEmpty()) return new ArrayList<>();

        return Arrays.asList(value.split(","));
    }

    public static boolean save() {
        if (sOptionFolderPath == null) {
            Log.w(Tools.APP_NAME, "Could not save options.txt before an instance was loaded");
            return false;
        }

        StringBuilder result = new StringBuilder();
        for(String key : sParameterMap.keySet())
            result.append(key)
                    .append(':')
                    .append(sParameterMap.get(key))
                    .append('\n');

        FileObserver observer = sFileObserver;
        try {
            if (observer != null) observer.stopWatching();
            Tools.write(sOptionFolderPath + "/options.txt", result.toString());
            return true;
        } catch (IOException | RuntimeException e) {
            Log.w(Tools.APP_NAME, "Could not save options.txt", e);
            return false;
        } finally {
            if (observer != null) {
                try {
                    observer.startWatching();
                } catch (RuntimeException e) {
                    Log.w(Tools.APP_NAME, "Could not restart options.txt observer", e);
                }
            }
        }
    }

    /** Apply the reversible high-FPS options to the currently loaded instance. */
    public static void applyPerformancePreset() {
        if (sOptionFolderPath == null) {
            Log.w(Tools.APP_NAME, "Cannot apply performance options before loading an instance");
            return;
        }

        File backupFile = new File(sOptionFolderPath, PERFORMANCE_BACKUP_FILE);
        if (!backupFile.exists()) {
            PerformanceOptionsPreset.Snapshot snapshot = PerformanceOptionsPreset.capture(sParameterMap);
            try {
                Tools.write(backupFile.getAbsolutePath(), Tools.GLOBAL_GSON.toJson(snapshot));
            } catch (IOException | RuntimeException e) {
                Log.w(Tools.APP_NAME, "Could not back up instance options; performance profile was not applied", e);
                return;
            }
        } else if (readPerformanceSnapshot(backupFile) == null) {
            // Do not overwrite an unreadable backup: doing so would make restoring the user's
            // original options impossible.
            return;
        }

        PerformanceOptionsPreset.apply(sParameterMap);
        save();
    }

    /** Restore the loaded instance's previous options after the high-FPS mode is disabled. */
    public static void restorePerformancePreset() {
        if (sOptionFolderPath == null) return;

        File backupFile = new File(sOptionFolderPath, PERFORMANCE_BACKUP_FILE);
        if (!backupFile.isFile()) return;

        PerformanceOptionsPreset.Snapshot snapshot = readPerformanceSnapshot(backupFile);
        if (snapshot == null) return;

        PerformanceOptionsPreset.restore(sParameterMap, snapshot);
        if (save() && !backupFile.delete()) {
            Log.w(Tools.APP_NAME, "Could not remove restored performance-options backup");
        }
    }

    private static PerformanceOptionsPreset.Snapshot readPerformanceSnapshot(File backupFile) {
        try {
            PerformanceOptionsPreset.Snapshot snapshot = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(backupFile), PerformanceOptionsPreset.Snapshot.class);
            if (PerformanceOptionsPreset.isValidSnapshot(snapshot)) return snapshot;
        } catch (IOException | RuntimeException e) {
            Log.w(Tools.APP_NAME, "Could not read performance-options backup", e);
            return null;
        }

        Log.w(Tools.APP_NAME, "Performance-options backup is invalid; leaving instance options unchanged");
        return null;
    }

    /** @return The stored Minecraft GUI scale, also auto-computed if on auto-mode or improper setting */
    public static int getMcScale() {
        String str = MCOptionUtils.get("guiScale");
        int guiScale = (str == null ? 0 :Integer.parseInt(str));

        int scale = Math.max(Math.min(windowWidth / 320, windowHeight / 240), 1);
        if(scale < guiScale || guiScale == 0){
            guiScale = scale;
        }

        return guiScale;
    }

    /** Add a file observer to reload options on file change
     * Listeners get notified of the change */
    private static void setupFileObserver(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            sFileObserver = new FileObserver(new File(sOptionFolderPath + "/options.txt"), FileObserver.MODIFY) {
                @Override
                public void onEvent(int i, @Nullable String s) {
                    MCOptionUtils.load();
                    notifyListeners();
                }
            };
        }else{
            sFileObserver = new FileObserver(sOptionFolderPath + "/options.txt", FileObserver.MODIFY) {
                @Override
                public void onEvent(int i, @Nullable String s) {
                    MCOptionUtils.load();
                    notifyListeners();
                }
            };
        }

        sFileObserver.startWatching();
    }

    /** Notify the option listeners */
    public static void notifyListeners(){
        for(WeakReference<MCOptionListener> weakReference : sOptionListeners){
            MCOptionListener optionListener = weakReference.get();
            if(optionListener == null) continue;

            optionListener.onOptionChanged();
        }
    }

    /** Add an option listener, notice how we don't have a reference to it */
    public static void addMCOptionListener(MCOptionListener listener){
        sOptionListeners.add(new WeakReference<>(listener));
    }

    /** Remove a listener from existence, or at least, its reference here */
    public static void removeMCOptionListener(MCOptionListener listener){
        for(WeakReference<MCOptionListener> weakReference : sOptionListeners){
            MCOptionListener optionListener = weakReference.get();
            if(optionListener == null) continue;
            if(optionListener == listener){
                sOptionListeners.remove(weakReference);
                return;
            }
        }
    }

}
