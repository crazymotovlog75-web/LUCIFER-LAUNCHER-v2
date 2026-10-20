package net.kdt.pojavlaunch.utils;

import android.system.ErrnoException;
import android.system.Os;
import android.util.Base64;
import android.util.Log;

import net.kdt.pojavlaunch.MinecraftServerPing;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Safe, merge-preserving read/write for Minecraft Java Edition's gzip-compressed servers.dat. */
public final class MinecraftServerListStore {
    private static final String TAG = "ServerListStore";
    private static final byte TAG_END = 0;
    private static final byte TAG_BYTE = 1;
    private static final byte TAG_SHORT = 2;
    private static final byte TAG_INT = 3;
    private static final byte TAG_LONG = 4;
    private static final byte TAG_FLOAT = 5;
    private static final byte TAG_DOUBLE = 6;
    private static final byte TAG_BYTE_ARRAY = 7;
    private static final byte TAG_STRING = 8;
    private static final byte TAG_LIST = 9;
    private static final byte TAG_COMPOUND = 10;
    private static final byte TAG_INT_ARRAY = 11;
    private static final byte TAG_LONG_ARRAY = 12;

    private static final int MAX_COMPRESSED_FILE_BYTES = 16 * 1024 * 1024;
    private static final long MAX_UNCOMPRESSED_FILE_BYTES = 32L * 1024 * 1024;
    private static final int MAX_COLLECTION_LENGTH = 1_000_000;
    private static final int MAX_NBT_DEPTH = 64;
    private static final Object FILE_LOCK = new Object();

    private MinecraftServerListStore() {}

    public static final class ServerEntry {
        public final String name;
        public final String address;

        public ServerEntry(String name, String address) {
            this.name = name;
            this.address = address;
        }
    }

    public static final class SyncResult {
        public final Set<String> managedKeys;
        public final int added;
        public final int removed;
        public final boolean fileChanged;

        private SyncResult(Set<String> managedKeys, int added, int removed, boolean fileChanged) {
            this.managedKeys = new HashSet<>(managedKeys);
            this.added = added;
            this.removed = removed;
            this.fileChanged = fileChanged;
        }
    }

    /** Stable opaque key for tracking only entries inserted by Server Hub, per game directory. */
    public static String keyForEntry(String name, String address) {
        String canonicalAddress = canonicalAddress(address);
        String value = (name == null ? "" : name.trim()) + '\n' + canonicalAddress;
        return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    /**
     * Adds missing Hub entries and removes only previously managed entries no longer desired.
     * Unknown NBT tags and unowned multiplayer entries are preserved. A malformed existing file
     * is left untouched and reported as an IOException rather than being silently replaced.
     */
    public static SyncResult sync(File gameDirectory, List<ServerEntry> desiredEntries,
                                  Set<String> previouslyManagedKeys) throws IOException {
        if (gameDirectory == null) throw new IOException("Selected profile has no game directory");
        List<ServerEntry> desired = desiredEntries == null
                ? new ArrayList<>() : new ArrayList<>(desiredEntries);
        Set<String> previous = previouslyManagedKeys == null
                ? new HashSet<>() : new HashSet<>(previouslyManagedKeys);

        Set<String> desiredKeys = new LinkedHashSet<>();
        for (ServerEntry entry : desired) {
            if (entry == null || entry.name == null || entry.name.trim().isEmpty() ||
                    entry.name.trim().length() > 64) {
                throw new IOException("A saved server has an invalid name");
            }
            try {
                MinecraftServerPing.parseEndpoint(entry.address);
            } catch (IllegalArgumentException invalid) {
                throw new IOException("A saved server has an invalid address", invalid);
            }
            desiredKeys.add(keyForEntry(entry.name, entry.address));
        }

        File directory = gameDirectory.getCanonicalFile();
        File destination = new File(directory, "servers.dat");
        if (!destination.exists() && desired.isEmpty() && previous.isEmpty()) {
            return new SyncResult(new HashSet<>(), 0, 0, false);
        }

        synchronized (FILE_LOCK) {
            FileUtils.ensureDirectory(directory);
            if (destination.exists() && (!destination.isFile() || !destination.canRead() || !destination.canWrite())) {
                throw new IOException("Minecraft servers.dat is not a readable and writable file");
            }

            CompoundTag root = destination.exists() ? readRoot(destination) : new CompoundTag();
            Tag serversTag = root.values.get("servers");
            if (serversTag == null) {
                serversTag = Tag.list(TAG_COMPOUND, new ArrayList<Tag>());
                root.values.put("servers", serversTag);
            }
            if (serversTag.type != TAG_LIST) throw new IOException("Minecraft servers.dat has an invalid servers tag");
            ListTag servers = (ListTag) serversTag.value;
            if (servers.elementType != TAG_COMPOUND && !(servers.elementType == TAG_END && servers.values.isEmpty())) {
                throw new IOException("Minecraft servers.dat contains an unsupported servers list");
            }
            if (servers.elementType == TAG_END) servers.elementType = TAG_COMPOUND;

            boolean changed = false;
            int removed = 0;
            Set<String> removedOnce = new HashSet<>();
            Iterator<Tag> iterator = servers.values.iterator();
            while (iterator.hasNext()) {
                Tag tag = iterator.next();
                String existingKey = keyForTag(tag);
                if (existingKey != null && previous.contains(existingKey) &&
                        !desiredKeys.contains(existingKey) && removedOnce.add(existingKey)) {
                    iterator.remove();
                    removed++;
                    changed = true;
                }
            }

            Set<String> existingKeys = new HashSet<>();
            Set<String> existingAddressKeys = new HashSet<>();
            for (Tag tag : servers.values) {
                String key = keyForTag(tag);
                if (key != null) existingKeys.add(key);
                String addressKey = addressKeyForTag(tag);
                if (addressKey != null) existingAddressKeys.add(addressKey);
            }

            Set<String> managedNow = new HashSet<>();
            int added = 0;
            for (ServerEntry entry : desired) {
                String key = keyForEntry(entry.name, entry.address);
                if (existingKeys.contains(key)) {
                    if (previous.contains(key)) managedNow.add(key);
                    continue;
                }
                String addressKey = canonicalAddressKey(entry.address);
                if (existingAddressKeys.contains(addressKey)) {
                    // A manual server-list entry already targets this endpoint under another
                    // display name; preserve it rather than creating an address duplicate.
                    continue;
                }
                servers.values.add(newServerTag(entry));
                existingKeys.add(key);
                existingAddressKeys.add(addressKey);
                managedNow.add(key);
                added++;
                changed = true;
            }

            if (changed) writeAtomically(destination, root);
            return new SyncResult(managedNow, added, removed, changed);
        }
    }

    private static String canonicalAddress(String address) {
        if (address == null) return "";
        try {
            return MinecraftServerPing.parseEndpoint(address).toAddress().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) {
            return address.trim().toLowerCase(Locale.ROOT);
        }
    }

    private static String keyForTag(Tag tag) {
        if (tag == null || tag.type != TAG_COMPOUND) return null;
        CompoundTag compound = (CompoundTag) tag.value;
        Tag name = compound.values.get("name");
        Tag address = compound.values.get("ip");
        if (name == null || name.type != TAG_STRING || address == null || address.type != TAG_STRING) return null;
        return keyForEntry((String) name.value, (String) address.value);
    }

    private static String addressKeyForTag(Tag tag) {
        if (tag == null || tag.type != TAG_COMPOUND) return null;
        Tag address = ((CompoundTag) tag.value).values.get("ip");
        if (address == null || address.type != TAG_STRING) return null;
        return canonicalAddressKey((String) address.value);
    }

    private static String canonicalAddressKey(String address) {
        try {
            return MinecraftServerPing.parseEndpoint(address).normalizedKey();
        } catch (IllegalArgumentException ignored) {
            return address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
        }
    }

    private static Tag newServerTag(ServerEntry entry) {
        CompoundTag server = new CompoundTag();
        server.values.put("name", Tag.string(entry.name.trim()));
        server.values.put("ip", Tag.string(MinecraftServerPing.parseEndpoint(entry.address).toAddress()));
        server.values.put("acceptTextures", Tag.byteValue((byte) 1));
        return Tag.compound(server);
    }

    private static CompoundTag readRoot(File file) throws IOException {
        if (file.length() <= 0 || file.length() > MAX_COMPRESSED_FILE_BYTES) {
            throw new IOException("Minecraft servers.dat is empty or too large to read safely");
        }
        try (InputStream fileInput = new FileInputStream(file);
             GZIPInputStream gzip = new GZIPInputStream(new BufferedInputStream(fileInput));
             DataInputStream input = new DataInputStream(new LimitedInputStream(gzip, MAX_UNCOMPRESSED_FILE_BYTES))) {
            byte rootType = input.readByte();
            if (rootType != TAG_COMPOUND) throw new IOException("Minecraft servers.dat root is not a compound tag");
            input.readUTF(); // Root compound name is normally empty; retain all values, not its name.
            Tag root = readPayload(input, rootType, 0);
            if (input.read() != -1) throw new IOException("Unexpected trailing bytes after the NBT root");
            return (CompoundTag) root.value;
        } catch (IOException e) {
            throw new IOException("Could not safely read the existing Minecraft servers.dat", e);
        }
    }

    private static Tag readPayload(DataInputStream input, byte type, int depth) throws IOException {
        if (depth > MAX_NBT_DEPTH) throw new IOException("NBT nesting is too deep");
        switch (type) {
            case TAG_BYTE: return Tag.byteValue(input.readByte());
            case TAG_SHORT: return new Tag(TAG_SHORT, input.readShort());
            case TAG_INT: return new Tag(TAG_INT, input.readInt());
            case TAG_LONG: return new Tag(TAG_LONG, input.readLong());
            case TAG_FLOAT: return new Tag(TAG_FLOAT, input.readFloat());
            case TAG_DOUBLE: return new Tag(TAG_DOUBLE, input.readDouble());
            case TAG_BYTE_ARRAY: {
                int length = readLength(input);
                byte[] bytes = new byte[length];
                input.readFully(bytes);
                return new Tag(TAG_BYTE_ARRAY, bytes);
            }
            case TAG_STRING: return Tag.string(input.readUTF());
            case TAG_LIST: {
                byte elementType = input.readByte();
                int length = readLength(input);
                if (elementType == TAG_END && length > 0) throw new IOException("Invalid NBT list element type");
                List<Tag> values = new ArrayList<>(Math.min(length, 4096));
                for (int i = 0; i < length; i++) values.add(readPayload(input, elementType, depth + 1));
                return Tag.list(elementType, values);
            }
            case TAG_COMPOUND: {
                CompoundTag compound = new CompoundTag();
                int entries = 0;
                while (true) {
                    byte childType = input.readByte();
                    if (childType == TAG_END) break;
                    if (++entries > MAX_COLLECTION_LENGTH) throw new IOException("NBT compound has too many entries");
                    String name = input.readUTF();
                    compound.values.put(name, readPayload(input, childType, depth + 1));
                }
                return Tag.compound(compound);
            }
            case TAG_INT_ARRAY: {
                int length = readLength(input);
                int[] values = new int[length];
                for (int i = 0; i < length; i++) values[i] = input.readInt();
                return new Tag(TAG_INT_ARRAY, values);
            }
            case TAG_LONG_ARRAY: {
                int length = readLength(input);
                long[] values = new long[length];
                for (int i = 0; i < length; i++) values[i] = input.readLong();
                return new Tag(TAG_LONG_ARRAY, values);
            }
            default: throw new IOException("Unsupported NBT tag type " + type);
        }
    }

    private static int readLength(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_COLLECTION_LENGTH) throw new IOException("Invalid NBT collection length");
        return length;
    }

    private static void writeAtomically(File destination, CompoundTag root) throws IOException {
        File parent = destination.getParentFile();
        if (parent == null || !parent.isDirectory()) throw new IOException("Minecraft game directory is unavailable");
        File temporary = new File(parent, ".servers.dat." + UUID.randomUUID() + ".tmp");
        boolean renamed = false;
        try {
            try (FileOutputStream fileOutput = new FileOutputStream(temporary);
                 BufferedOutputStream buffered = new BufferedOutputStream(fileOutput);
                 GZIPOutputStream gzip = new GZIPOutputStream(buffered);
                 DataOutputStream output = new DataOutputStream(gzip)) {
                output.writeByte(TAG_COMPOUND);
                output.writeUTF("");
                writePayload(output, Tag.compound(root), 0);
                output.flush();
                gzip.finish();
                buffered.flush();
                fileOutput.getFD().sync();
            }

            // Verify the complete compressed file before replacing the user's existing server list.
            readRoot(temporary);
            try {
                Os.rename(temporary.getAbsolutePath(), destination.getAbsolutePath());
                renamed = true;
            } catch (ErrnoException e) {
                throw new IOException("Could not atomically replace Minecraft servers.dat", e);
            }
        } finally {
            if (!renamed && temporary.exists() && !temporary.delete()) {
                Log.w(TAG, "Unable to remove temporary NBT file " + temporary.getAbsolutePath());
            }
        }
    }

    private static void writePayload(DataOutputStream output, Tag tag, int depth) throws IOException {
        if (depth > MAX_NBT_DEPTH) throw new IOException("NBT nesting is too deep to write");
        switch (tag.type) {
            case TAG_BYTE: output.writeByte((Byte) tag.value); break;
            case TAG_SHORT: output.writeShort((Short) tag.value); break;
            case TAG_INT: output.writeInt((Integer) tag.value); break;
            case TAG_LONG: output.writeLong((Long) tag.value); break;
            case TAG_FLOAT: output.writeFloat((Float) tag.value); break;
            case TAG_DOUBLE: output.writeDouble((Double) tag.value); break;
            case TAG_BYTE_ARRAY: {
                byte[] values = (byte[]) tag.value;
                output.writeInt(values.length);
                output.write(values);
                break;
            }
            case TAG_STRING: output.writeUTF((String) tag.value); break;
            case TAG_LIST: {
                ListTag list = (ListTag) tag.value;
                output.writeByte(list.elementType);
                output.writeInt(list.values.size());
                for (Tag element : list.values) {
                    if (element.type != list.elementType) throw new IOException("Mixed tag types in NBT list");
                    writePayload(output, element, depth + 1);
                }
                break;
            }
            case TAG_COMPOUND: {
                CompoundTag compound = (CompoundTag) tag.value;
                for (java.util.Map.Entry<String, Tag> entry : compound.values.entrySet()) {
                    if (entry.getValue().type == TAG_END) continue;
                    output.writeByte(entry.getValue().type);
                    output.writeUTF(entry.getKey());
                    writePayload(output, entry.getValue(), depth + 1);
                }
                output.writeByte(TAG_END);
                break;
            }
            case TAG_INT_ARRAY: {
                int[] values = (int[]) tag.value;
                output.writeInt(values.length);
                for (int value : values) output.writeInt(value);
                break;
            }
            case TAG_LONG_ARRAY: {
                long[] values = (long[]) tag.value;
                output.writeInt(values.length);
                for (long value : values) output.writeLong(value);
                break;
            }
            default: throw new IOException("Unsupported NBT tag type " + tag.type);
        }
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private final long maximumBytes;
        private long bytesRead;

        LimitedInputStream(InputStream input, long maximumBytes) {
            super(input);
            this.maximumBytes = maximumBytes;
        }

        @Override public int read() throws IOException {
            int value = in.read();
            if (value != -1) count(1);
            return value;
        }

        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = in.read(buffer, offset, length);
            if (count > 0) count(count);
            return count;
        }

        private void count(int count) throws IOException {
            bytesRead += count;
            if (bytesRead > maximumBytes) throw new IOException("Decompressed NBT file exceeds the safety limit");
        }
    }

    private static final class CompoundTag {
        final LinkedHashMap<String, Tag> values = new LinkedHashMap<>();
    }

    private static final class ListTag {
        byte elementType;
        final List<Tag> values;

        ListTag(byte elementType, List<Tag> values) {
            this.elementType = elementType;
            this.values = values;
        }
    }

    private static final class Tag {
        final byte type;
        final Object value;

        Tag(byte type, Object value) {
            this.type = type;
            this.value = value;
        }

        static Tag byteValue(byte value) { return new Tag(TAG_BYTE, value); }
        static Tag string(String value) { return new Tag(TAG_STRING, value); }
        static Tag list(byte type, List<Tag> values) { return new Tag(TAG_LIST, new ListTag(type, values)); }
        static Tag compound(CompoundTag value) { return new Tag(TAG_COMPOUND, value); }
    }
}
