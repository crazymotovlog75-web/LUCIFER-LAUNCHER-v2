package net.kdt.pojavlaunch.instances;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class InstanceContentFilesTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void modJarsToggleWithCompatibleDisabledSuffixPerInstance() throws Exception {
        File firstInstance = temporaryFolder.newFolder("first");
        File secondInstance = temporaryFolder.newFolder("second");
        File activeMod = write(new File(firstInstance, "mods/performance.jar"));
        write(new File(secondInstance, "mods/performance.jar"));

        List<InstanceContentFiles.Entry> entries = InstanceContentFiles.list(
                firstInstance, InstanceContentFiles.Category.MODS);
        assertEquals(1, entries.size());
        assertTrue(entries.get(0).enabled);
        assertEquals("performance", entries.get(0).displayName());

        File disabled = InstanceContentFiles.setEnabled(firstInstance, entries.get(0), false);
        assertEquals("performance.jar.disabled", disabled.getName());
        assertFalse(disabled.equals(activeMod));
        assertTrue(disabled.isFile());
        assertTrue(new File(secondInstance, "mods/performance.jar").isFile());

        List<InstanceContentFiles.Entry> disabledEntries = InstanceContentFiles.list(
                firstInstance, InstanceContentFiles.Category.MODS);
        assertEquals(1, disabledEntries.size());
        assertFalse(disabledEntries.get(0).enabled);
        assertEquals("performance", disabledEntries.get(0).displayName());
        InstanceContentFiles.setEnabled(firstInstance, disabledEntries.get(0), true);
        assertTrue(new File(firstInstance, "mods/performance.jar").isFile());
    }

    @Test
    public void resourcePackDirectoriesAreMovedOutsideTheScannedFolder() throws Exception {
        File gameDirectory = temporaryFolder.newFolder("instance");
        File pack = new File(gameDirectory, "resourcepacks/High Contrast Pack");
        assertTrue(pack.mkdirs());
        write(new File(pack, "pack.mcmeta"));

        List<InstanceContentFiles.Entry> entries = InstanceContentFiles.list(
                gameDirectory, InstanceContentFiles.Category.PACKS);
        assertEquals(1, entries.size());
        File disabled = InstanceContentFiles.setEnabled(gameDirectory, entries.get(0), false);
        assertTrue(disabled.getAbsolutePath().contains(".lucifer-disabled"));
        assertFalse(new File(gameDirectory, "resourcepacks/High Contrast Pack").exists());
        assertFalse(InstanceContentFiles.list(gameDirectory, InstanceContentFiles.Category.PACKS).get(0).enabled);

        List<InstanceContentFiles.Entry> disabledEntries = InstanceContentFiles.list(
                gameDirectory, InstanceContentFiles.Category.PACKS);
        InstanceContentFiles.setEnabled(gameDirectory, disabledEntries.get(0), true);
        assertTrue(new File(gameDirectory, "resourcepacks/High Contrast Pack/pack.mcmeta").isFile());
    }

    @Test
    public void resourcePackZipAndWorldDirectoryToggleWithoutDeletingData() throws Exception {
        File gameDirectory = temporaryFolder.newFolder("instance");
        write(new File(gameDirectory, "resourcepacks/texture.zip"));
        File level = write(new File(gameDirectory, "saves/Survival/level.dat"));

        List<InstanceContentFiles.Entry> packs = InstanceContentFiles.list(
                gameDirectory, InstanceContentFiles.Category.PACKS);
        assertEquals("texture", packs.get(0).displayName());
        InstanceContentFiles.setEnabled(gameDirectory, packs.get(0), false);
        assertTrue(new File(gameDirectory, "resourcepacks/texture.zip.disabled").isFile());

        List<InstanceContentFiles.Entry> worlds = InstanceContentFiles.list(
                gameDirectory, InstanceContentFiles.Category.WORLDS);
        assertEquals(1, worlds.size());
        File disabledWorld = InstanceContentFiles.setEnabled(gameDirectory, worlds.get(0), false);
        assertTrue(disabledWorld.getAbsolutePath().contains(".lucifer-disabled"));
        assertTrue(new File(disabledWorld, "level.dat").isFile());
        assertFalse(level.exists());

        List<InstanceContentFiles.Entry> disabledWorlds = InstanceContentFiles.list(
                gameDirectory, InstanceContentFiles.Category.WORLDS);
        assertEquals(1, disabledWorlds.size());
        InstanceContentFiles.setEnabled(gameDirectory, disabledWorlds.get(0), true);
        assertTrue(new File(gameDirectory, "saves/Survival/level.dat").isFile());
    }

    @Test(expected = IOException.class)
    public void enableRefusesToOverwriteAnExistingPack() throws Exception {
        File gameDirectory = temporaryFolder.newFolder("instance");
        write(new File(gameDirectory, "resourcepacks/texture.zip"));
        write(new File(gameDirectory, "resourcepacks/texture.zip.disabled"));
        List<InstanceContentFiles.Entry> entries = InstanceContentFiles.list(
                gameDirectory, InstanceContentFiles.Category.PACKS);
        InstanceContentFiles.Entry disabled = null;
        for (InstanceContentFiles.Entry entry : entries) {
            if (!entry.enabled) disabled = entry;
        }
        assertTrue(disabled != null);
        InstanceContentFiles.setEnabled(gameDirectory, disabled, true);
    }

    private static File write(File file) throws IOException {
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Could not create test directory");
        }
        if (!file.createNewFile()) throw new IOException("Could not create test file");
        return file;
    }
}
