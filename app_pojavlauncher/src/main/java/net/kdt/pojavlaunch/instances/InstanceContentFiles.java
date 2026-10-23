package net.kdt.pojavlaunch.instances;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** File-backed, per-instance content listing and reversible enable/disable operations. */
public final class InstanceContentFiles {
    private static final String DISABLED_SUFFIX = ".disabled";
    private static final String DISABLED_ROOT = ".lucifer-disabled";

    public enum Category {
        MODS("mods"),
        PACKS("resourcepacks"),
        SHADERS("shaderpacks"),
        WORLDS("saves");

        public final String directoryName;

        Category(String directoryName) {
            this.directoryName = directoryName;
        }
    }

    public static final class Entry {
        public volatile File file;
        public volatile boolean enabled;
        public volatile boolean pending;
        public final Category category;

        private Entry(File file, boolean enabled, Category category) {
            this.file = file;
            this.enabled = enabled;
            this.category = category;
        }

        public String displayName() {
            String name = file.getName();
            if (!file.isFile()) return name;
            if (name.toLowerCase(Locale.ROOT).endsWith(DISABLED_SUFFIX)) {
                name = name.substring(0, name.length() - DISABLED_SUFFIX.length());
            }
            if (category == Category.MODS && name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                return name.substring(0, name.length() - 4);
            }
            if ((category == Category.PACKS || category == Category.SHADERS) &&
                    name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                return name.substring(0, name.length() - 4);
            }
            return name;
        }
    }

    private InstanceContentFiles() { }

    /** Lists enabled content in the game's normal folder and disabled folders outside it. */
    public static List<Entry> list(File gameDirectory, Category category) {
        if (gameDirectory == null || category == null) return Collections.emptyList();
        ArrayList<Entry> entries = new ArrayList<>();
        File categoryDirectory = getCategoryDirectory(gameDirectory, category);
        File[] enabledItems = categoryDirectory.listFiles();
        if (enabledItems != null) {
            for (File file : enabledItems) {
                if (!file.getName().startsWith(".") && isSupported(category, file, true)) {
                    boolean enabled = !isDisabledFile(file);
                    entries.add(new Entry(file, enabled, category));
                }
            }
        }

        if (category != Category.MODS) {
            File disabledDirectory = getDisabledDirectory(gameDirectory, category);
            File[] disabledItems = disabledDirectory.listFiles();
            if (disabledItems != null) {
                for (File file : disabledItems) {
                    if (!file.getName().startsWith(".") && isSupported(category, file, false)) {
                        entries.add(new Entry(file, false, category));
                    }
                }
            }
        }

        entries.sort(Comparator.comparing((Entry entry) -> entry.displayName().toLowerCase(Locale.ROOT))
                .thenComparing(entry -> entry.enabled ? 0 : 1));
        return entries;
    }

    /**
     * Renames files with a .disabled suffix (compatible with the existing mod list) and moves
     * disabled pack/world directories outside the game's scanned content folders.
     */
    public static File setEnabled(File gameDirectory, Entry entry, boolean enabled) throws IOException {
        if (gameDirectory == null || entry == null || entry.category == null) {
            throw new IOException("Missing instance content entry");
        }
        if (entry.enabled == enabled) return entry.file;
        File source = entry.file;
        if (source == null || !source.exists()) throw new IOException("Content no longer exists");

        File target;
        if (source.isDirectory()) {
            target = enabled
                    ? new File(getCategoryDirectory(gameDirectory, entry.category), source.getName())
                    : new File(getDisabledDirectory(gameDirectory, entry.category), source.getName());
        } else {
            String name = source.getName();
            if (enabled) {
                if (!endsWithIgnoreCase(name, DISABLED_SUFFIX)) {
                    throw new IOException("Disabled content filename is invalid");
                }
                target = new File(source.getParentFile(), name.substring(0, name.length() - DISABLED_SUFFIX.length()));
            } else {
                if (endsWithIgnoreCase(name, DISABLED_SUFFIX)) {
                    throw new IOException("Content is already disabled");
                }
                target = new File(source.getParentFile(), name + DISABLED_SUFFIX);
            }
        }

        if (target.exists()) throw new IOException("A file with that name already exists");
        File targetParent = target.getParentFile();
        if (targetParent != null && !targetParent.isDirectory() && !targetParent.mkdirs() && !targetParent.isDirectory()) {
            throw new IOException("Could not create the content directory");
        }
        if (!source.renameTo(target)) throw new IOException("Could not change content state");

        entry.file = target;
        entry.enabled = enabled;
        return target;
    }

    public static File getCategoryDirectory(File gameDirectory, Category category) {
        return new File(gameDirectory, category.directoryName);
    }

    public static File getDisabledDirectory(File gameDirectory, Category category) {
        return new File(new File(gameDirectory, DISABLED_ROOT), category.directoryName);
    }

    private static boolean isSupported(Category category, File file, boolean inGameDirectory) {
        if (category == Category.MODS) {
            if (!file.isFile()) return false;
            String name = file.getName().toLowerCase(Locale.ROOT);
            return name.endsWith(".jar") || inGameDirectory && name.endsWith(".jar.disabled");
        }
        if (category == Category.WORLDS) return file.isDirectory();
        if (file.isDirectory()) return true;
        if (!file.isFile()) return false;
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".zip") || inGameDirectory && name.endsWith(".zip.disabled");
    }

    private static boolean isDisabledFile(File file) {
        return file.isFile() && endsWithIgnoreCase(file.getName(), DISABLED_SUFFIX);
    }

    private static boolean endsWithIgnoreCase(String value, String suffix) {
        return value != null && value.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT));
    }
}
