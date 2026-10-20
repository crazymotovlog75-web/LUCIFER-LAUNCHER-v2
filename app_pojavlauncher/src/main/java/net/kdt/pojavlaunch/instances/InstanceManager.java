package net.kdt.pojavlaunch.instances;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** File operations for per-profile instance folders. Copy operations never remove their source. */
public final class InstanceManager {
    private static final String METADATA_FILE = "instance.json";
    private static final String MIGRATION_MARKER = ".legacy-import-complete";
    private static final Set<String> SHARED_OR_LAUNCHER_ENTRIES = new HashSet<>(Arrays.asList(
            "versions", "libraries", "assets", "launcher_profiles.json", "custom_instances",
            "modpacks", "instances", METADATA_FILE, MIGRATION_MARKER
    ));
    private static final String[] GAME_DIRECTORIES = {
            "mods", "saves", "config", "resourcepacks", "shaderpacks", "logs",
            "screenshots", "crash-reports", "server-resource-packs"
    };

    private final InstancePathResolver paths;

    public InstanceManager(InstancePathResolver paths) {
        if (paths == null) throw new IllegalArgumentException("paths must not be null");
        this.paths = paths;
    }

    public InstancePathResolver paths() { return paths; }

    public File initializeInstance(String instanceId, String profileKey, String name, String versionId)
            throws IOException {
        File root = paths.getInstanceRoot(instanceId);
        ensureDirectory(root);
        for (String directory : GAME_DIRECTORIES) ensureDirectory(new File(root, directory));
        ensureDirectory(paths.getVersionsRoot(instanceId));
        ensureDirectory(new File(root, "natives"));
        ensureDirectory(paths.getLibrariesRoot(instanceId));
        ensureDirectory(paths.getAssetsRoot(instanceId));
        if (!new File(root, METADATA_FILE).isFile()) {
            writeMetadata(instanceId, profileKey, name, versionId);
        }
        return root;
    }

    public void updateMetadata(String instanceId, String profileKey, String name, String versionId)
            throws IOException {
        ensureDirectory(paths.getInstanceRoot(instanceId));
        writeMetadata(instanceId, profileKey, name, versionId);
    }

    /**
     * Import the old game's mutable data and selected version into a new isolated instance.
     * Existing destinations are left untouched; the source is never deleted or modified.
     */
    public File importLegacyInstance(String instanceId, String profileKey, String name,
                                     String versionId, File sourceGameDirectory) throws IOException {
        File destination = initializeInstance(instanceId, profileKey, name, versionId);
        if (sourceGameDirectory != null && sourceGameDirectory.isDirectory() &&
                !sameCanonicalPath(sourceGameDirectory, destination)) {
            copyDirectoryMissing(sourceGameDirectory, destination, SHARED_OR_LAUNCHER_ENTRIES);
            copyVersionFrom(sourceGameDirectory, instanceId, versionId);
        }
        if (!new File(destination, MIGRATION_MARKER).exists()) {
            writeText(new File(destination, MIGRATION_MARKER), "copied\n");
        }
        return destination;
    }

    /** Copy a legacy shared-cache version into an instance, if the instance lacks its JSON. */
    public File ensureVersionImported(String instanceId, String versionId) throws IOException {
        File destination = paths.getVersionDirectory(instanceId, versionId);
        if (paths.getVersionJson(instanceId, versionId).isFile()) return destination;
        File source = new File(paths.getLegacyVersionsRoot(), safeVersionId(versionId));
        if (source.isDirectory()) copyDirectoryMissing(source, destination, Collections.emptySet());
        return destination;
    }

    public boolean copyVersionFromInstance(String sourceInstanceId, String destinationInstanceId,
                                           String versionId) throws IOException {
        File source = paths.getVersionDirectory(sourceInstanceId, safeVersionId(versionId));
        if (!source.isDirectory()) return false;
        copyDirectoryMissing(source, paths.getVersionDirectory(destinationInstanceId, versionId),
                Collections.emptySet());
        return true;
    }

    public File importLegacyVersion(String instanceId, String profileKey, String name, String versionId)
            throws IOException {
        File root = initializeInstance(instanceId, profileKey, name, versionId);
        ensureVersionImported(instanceId, versionId);
        File versionJson = paths.getVersionJson(instanceId, versionId);
        if (!versionJson.isFile()) {
            throw new IOException("Legacy version is missing its version JSON: " + versionId);
        }
        return root;
    }

    /** Deep-copy an instance for Duplicate; the original tree is not altered. */
    public File duplicateInstance(String sourceInstanceId, String destinationInstanceId,
                                  String profileKey, String name, String versionId) throws IOException {
        File source = paths.getInstanceRoot(sourceInstanceId);
        if (!source.isDirectory()) throw new IOException("Source instance does not exist");
        File destination = paths.getInstanceRoot(destinationInstanceId);
        if (sameCanonicalPath(source, destination)) throw new IOException("Cannot duplicate an instance onto itself");
        ensureDirectory(destination);
        copyDirectoryMissing(source, destination, new HashSet<>(Collections.singletonList(METADATA_FILE)));
        for (String directory : GAME_DIRECTORIES) ensureDirectory(new File(destination, directory));
        ensureDirectory(paths.getVersionsRoot(destinationInstanceId));
        ensureDirectory(new File(destination, "natives"));
        writeMetadata(destinationInstanceId, profileKey, name, versionId);
        return destination;
    }

    /** Deletes only a child of the managed instances root. */
    public void deleteInstance(String instanceId) throws IOException {
        File root = paths.getInstanceRoot(instanceId).getCanonicalFile();
        File instances = paths.getInstancesRoot().getCanonicalFile();
        if (!root.getParentFile().equals(instances)) {
            throw new IOException("Refusing to delete a path outside the instances directory");
        }
        deleteRecursively(root);
    }

    public List<String> listLegacyInstalledVersions() {
        File root = paths.getLegacyVersionsRoot();
        File[] directories = root.listFiles(File::isDirectory);
        if (directories == null) return Collections.emptyList();
        List<String> versions = new ArrayList<>();
        for (File directory : directories) {
            String id = directory.getName();
            try {
                safeVersionId(id);
                if (new File(directory, id + ".json").isFile()) versions.add(id);
            } catch (IllegalArgumentException ignored) {
                // Do not expose malformed folders as importable versions.
            }
        }
        Collections.sort(versions, String.CASE_INSENSITIVE_ORDER);
        return versions;
    }

    public List<File> listManagedInstances() {
        File[] directories = paths.getInstancesRoot().listFiles(File::isDirectory);
        if (directories == null) return Collections.emptyList();
        List<File> instances = new ArrayList<>();
        for (File directory : directories) {
            if (new File(directory, METADATA_FILE).isFile()) instances.add(directory);
        }
        Collections.sort(instances, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        return instances;
    }

    private void copyVersionFrom(File sourceGameDirectory, String instanceId, String versionId)
            throws IOException {
        if (versionId == null || versionId.trim().isEmpty()) return;
        String safeId = safeVersionId(versionId);
        File sourceVersion = new File(new File(sourceGameDirectory, "versions"), safeId);
        if (!sourceVersion.isDirectory()) sourceVersion = new File(paths.getLegacyVersionsRoot(), safeId);
        if (sourceVersion.isDirectory()) {
            copyDirectoryMissing(sourceVersion, paths.getVersionDirectory(instanceId, safeId),
                    Collections.emptySet());
        }
    }

    private void writeMetadata(String instanceId, String profileKey, String name, String versionId)
            throws IOException {
        File metadata = new File(paths.getInstanceRoot(instanceId), METADATA_FILE);
        String json = "{\n" +
                "  \"schemaVersion\": 1,\n" +
                "  \"instanceId\": \"" + escape(instanceId) + "\",\n" +
                "  \"profileKey\": \"" + escape(profileKey) + "\",\n" +
                "  \"name\": \"" + escape(name) + "\",\n" +
                "  \"versionId\": \"" + escape(versionId) + "\"\n" +
                "}\n";
        if (metadata.isFile() && json.equals(readText(metadata))) return;
        writeText(metadata, json);
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\b", "\\b").replace("\f", "\\f").replace("\t", "\\t")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String safeVersionId(String versionId) {
        if (versionId == null || !versionId.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,127}") ||
                versionId.equals(".") || versionId.equals("..")) {
            throw new IllegalArgumentException("Unsafe version id: " + versionId);
        }
        return versionId;
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (directory.isDirectory()) return;
        if (directory.exists() || !directory.mkdirs()) {
            throw new IOException("Could not create directory " + directory.getAbsolutePath());
        }
    }

    private static boolean sameCanonicalPath(File left, File right) throws IOException {
        return left.getCanonicalFile().equals(right.getCanonicalFile());
    }

    private static void copyDirectoryMissing(File source, File destination, Set<String> excludedRootNames)
            throws IOException {
        File canonicalSource = source.getCanonicalFile();
        File canonicalDestination = destination.getCanonicalFile();
        String sourcePath = canonicalSource.getPath();
        String destinationPath = canonicalDestination.getPath();
        if (destinationPath.equals(sourcePath) ||
                destinationPath.startsWith(sourcePath + File.separator)) {
            throw new IOException("Refusing to copy a directory into itself");
        }
        ensureDirectory(destination);
        File[] children = source.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (excludedRootNames.contains(child.getName())) continue;
            copyEntryMissing(child, new File(destination, child.getName()));
        }
    }

    private static void copyEntryMissing(File source, File destination) throws IOException {
        if (source.isDirectory()) {
            if (!destination.exists()) ensureDirectory(destination);
            File[] children = source.listFiles();
            if (children == null) return;
            for (File child : children) copyEntryMissing(child, new File(destination, child.getName()));
            return;
        }
        if (!source.isFile() || destination.exists()) return;
        File parent = destination.getParentFile();
        if (parent != null) ensureDirectory(parent);
        File temporary = File.createTempFile(destination.getName() + ".", ".copy", parent);
        boolean complete = false;
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.getFD().sync();
            if (!temporary.renameTo(destination)) {
                if (destination.exists()) return;
                throw new IOException("Could not publish copied file " + destination.getAbsolutePath());
            }
            complete = true;
        } finally {
            if (!complete && temporary.exists() && !temporary.delete()) {
                temporary.deleteOnExit();
            }
        }
    }

    private static String readText(File source) throws IOException {
        try (FileInputStream input = new FileInputStream(source);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void writeText(File destination, String content) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null) ensureDirectory(parent);
        File temporary = File.createTempFile(destination.getName() + ".", ".tmp", parent);
        boolean complete = false;
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
            if (!temporary.renameTo(destination)) {
                if (destination.exists()) destination.delete();
                if (!temporary.renameTo(destination)) throw new IOException("Could not save " + destination.getAbsolutePath());
            }
            complete = true;
        } finally {
            if (!complete && temporary.exists() && !temporary.delete()) temporary.deleteOnExit();
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        if (file.exists() && !file.delete()) throw new IOException("Could not delete " + file.getAbsolutePath());
    }
}
