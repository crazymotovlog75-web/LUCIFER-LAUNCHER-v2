package net.kdt.pojavlaunch.instances;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import net.kdt.pojavlaunch.utils.JSONUtils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class InstancePathResolverTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void twoInstancesKeepMutableDataSeparate() throws Exception {
        InstancePathResolver paths = new InstancePathResolver(temporaryFolder.newFolder(), true);
        InstanceManager manager = new InstanceManager(paths);
        File first = manager.initializeInstance("instance-one", "profile-one", "One", "1.20.1");
        File second = manager.initializeInstance("instance-two", "profile-two", "Two", "1.21.1");

        write(new File(first, "mods/example.jar"), "first-mod");
        write(new File(first, "config/example.toml"), "first-config");
        write(new File(first, "saves/World/level.dat"), "first-world");
        write(new File(second, "mods/example.jar"), "second-mod");
        write(new File(second, "config/example.toml"), "second-config");

        assertFalse(first.equals(second));
        assertEquals("first-mod", read(new File(first, "mods/example.jar")));
        assertEquals("second-mod", read(new File(second, "mods/example.jar")));
        assertFalse(new File(second, "saves/World/level.dat").exists());
        assertFalse(paths.getVersionDirectory("instance-one", "1.20.1")
                .equals(paths.getVersionDirectory("instance-two", "1.20.1")));
        assertEquals(new File(first, "versions"), paths.getVersionsRoot("instance-one"));
        assertFalse(paths.getVersionsRoot("instance-one").equals(paths.getLegacyVersionsRoot()));
        assertEquals(new File(paths.getInstanceRoot("instance-one"), "instance.json"),
                new File(first, "instance.json"));
    }

    @Test
    public void sharedCacheCanBeDisabledPerConfiguration() throws Exception {
        File storage = temporaryFolder.newFolder();
        InstancePathResolver shared = new InstancePathResolver(storage, true);
        InstancePathResolver local = new InstancePathResolver(storage, false);

        assertEquals(new File(storage, ".minecraft/libraries"), shared.getLibrariesRoot("one"));
        assertEquals(new File(storage, ".minecraft/assets"), shared.getAssetsRoot("two"));
        assertEquals(new File(local.getInstanceRoot("one"), "libraries"), local.getLibrariesRoot("one"));
        assertEquals(new File(local.getInstanceRoot("two"), "assets"), local.getAssetsRoot("two"));
        assertEquals(new File(local.getInstanceRoot("one"), "natives/1.20.1"),
                local.getNativesRoot("one", "1.20.1"));
    }

    @Test
    public void legacyImportCopiesWithoutDeletingSourceAndCanBeRepeated() throws Exception {
        File storage = temporaryFolder.newFolder();
        InstancePathResolver paths = new InstancePathResolver(storage, true);
        InstanceManager manager = new InstanceManager(paths);
        File legacy = paths.getLegacyMinecraftRoot();
        write(new File(legacy, "versions/1.20.1/1.20.1.json"), "legacy-json");
        write(new File(legacy, "versions/1.20.1/1.20.1.jar"), "legacy-jar");
        write(new File(legacy, "mods/old-mod.jar"), "legacy-mod");
        write(new File(legacy, "config/options.txt"), "legacy-options");
        write(new File(legacy, "options.txt"), "legacy-video-options");
        write(new File(legacy, "libraries/example/library.jar"), "shared-library");

        File imported = manager.importLegacyInstance(
                "imported-one", "profile-one", "Imported 1.20.1", "1.20.1", legacy);
        assertEquals("legacy-mod", read(new File(imported, "mods/old-mod.jar")));
        assertEquals("legacy-options", read(new File(imported, "config/options.txt")));
        assertEquals("legacy-json", read(paths.getVersionJson("imported-one", "1.20.1")));
        assertEquals("legacy-jar", read(paths.getVersionJar("imported-one", "1.20.1")));
        assertTrue(new File(legacy, "versions/1.20.1/1.20.1.jar").isFile());
        assertTrue(new File(legacy, "mods/old-mod.jar").isFile());
        assertFalse(new File(imported, "libraries/example/library.jar").exists());

        write(new File(imported, "config/options.txt"), "edited-instance-config");
        manager.importLegacyInstance("imported-one", "profile-one", "Imported 1.20.1", "1.20.1", legacy);
        assertEquals("edited-instance-config", read(new File(imported, "config/options.txt")));
    }

    @Test
    public void legacyVersionListIgnoresDirectoriesWithoutMetadata() throws Exception {
        InstancePathResolver paths = new InstancePathResolver(temporaryFolder.newFolder(), true);
        InstanceManager manager = new InstanceManager(paths);
        write(new File(paths.getLegacyVersionsRoot(), "1.21.1/1.21.1.json"), "{}\n");
        new File(paths.getLegacyVersionsRoot(), "broken").mkdirs();

        assertEquals(1, manager.listLegacyInstalledVersions().size());
        assertEquals("1.21.1", manager.listLegacyInstalledVersions().get(0));
    }

    @Test
    public void profileIdsAndGameDirectoryValuesAreStableAndSafe() {
        String id = InstancePathResolver.instanceIdForProfileKey("(Default)");
        assertEquals(id, InstancePathResolver.instanceIdForProfileKey("(Default)"));
        assertTrue(id.matches("[a-f0-9-]{36}"));
        assertEquals("./instances/instance-one", InstancePathResolver.gameDirectoryValue("instance-one"));
        try {
            InstancePathResolver.gameDirectoryValue("../outside");
            throw new AssertionError("Unsafe instance path accepted");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void vanillaAndFabricLaunchTokensResolveIntoTheRequestedInstance() throws Exception {
        File storage = temporaryFolder.newFolder();
        InstancePathResolver paths = new InstancePathResolver(storage, true);

        java.util.Map<String, String> vanilla = paths.getLaunchArgumentVariables("vanilla-one", "1.21.1");
        String[] vanillaTemplate = {"--gameDir", "${game_directory}", "--assetsDir", "${assets_root}",
                "-Djava.library.path=${natives_directory}", "-cp", "${client_jar}"};
        String[] vanillaArgs = JSONUtils.insertJSONValueList(vanillaTemplate, vanilla);
        assertEquals(new File(paths.getInstanceRoot("vanilla-one")).getAbsolutePath(), vanillaArgs[1]);
        assertEquals(new File(storage, ".minecraft/assets").getAbsolutePath(), vanillaArgs[3]);
        assertTrue(vanillaArgs[4].contains("/instances/vanilla-one/natives/1.21.1"));
        assertTrue(vanillaArgs[6].contains("/instances/vanilla-one/versions/1.21.1/1.21.1.jar"));
        assertFalse(vanillaArgs[6].contains("/.minecraft/versions/"));

        String fabricVersion = "fabric-loader-0.16.10-1.21.1";
        java.util.Map<String, String> fabric = paths.getLaunchArgumentVariables("fabric-one", fabricVersion);
        String[] fabricTemplate = {"--gameDir", "${game_directory}", "--version", "${version_name}",
                "-cp", "${client_jar}:${library_directory}/fabric-loader/example.jar"};
        String[] fabricArgs = JSONUtils.insertJSONValueList(fabricTemplate, fabric);
        assertEquals(new File(paths.getInstanceRoot("fabric-one")).getAbsolutePath(), fabricArgs[1]);
        assertEquals(fabricVersion, fabricArgs[3]);
        assertTrue(fabricArgs[5].contains("/instances/fabric-one/versions/" + fabricVersion + "/" + fabricVersion + ".jar"));
        assertTrue(fabricArgs[5].contains("/.minecraft/libraries/fabric-loader/example.jar"));
        assertFalse(fabricArgs[5].contains("/.minecraft/versions/"));
    }

    private static void write(File file, String value) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) assertTrue(parent.mkdirs());
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write(value);
        }
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
