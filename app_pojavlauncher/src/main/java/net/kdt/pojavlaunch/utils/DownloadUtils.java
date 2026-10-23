package net.kdt.pojavlaunch.utils;

import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Tools;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Callable;

/** HTTP and file-download helpers. Call these methods from a worker thread, never from the UI. */
@SuppressWarnings("IOStreamConstructor")
public class DownloadUtils {
    public static final String USER_AGENT = Tools.APP_NAME;
    private static final int TIME_OUT = 10000;
    private static final int MAX_STRING_RESPONSE_BYTES = 8 * 1024 * 1024;

    public static void download(String url, OutputStream os) throws IOException {
        download(new URL(url), os);
    }

    public static void download(URL url, OutputStream os) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(url, "GET");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new HttpStatusException(status, connection.getResponseMessage());
            }
            try (InputStream input = connection.getInputStream()) {
                IOUtils.copy(input, os);
            }
        } catch (HttpStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new IOException("Unable to download from " + url, e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static HttpURLConnection openConnection(URL url, String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setConnectTimeout(TIME_OUT);
        connection.setReadTimeout(TIME_OUT);
        connection.setInstanceFollowRedirects(true);
        connection.setDoInput(true);
        return connection;
    }

    private static void renameOver(File source, File destination) throws IOException {
        try {
            Os.rename(source.getAbsolutePath(), destination.getAbsolutePath());
        } catch (ErrnoException e) {
            throw new IOException("Could not atomically replace " + destination.getName(), e);
        }
    }

    public static String downloadString(String url) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        download(new URL(url), new BoundedOutputStream(bytes, MAX_STRING_RESPONSE_BYTES));
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Retries transient network errors, HTTP 408/429 and 5xx responses at most twice. */
    public static String downloadStringWithRetries(String url) throws IOException {
        IOException lastError = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return downloadString(url);
            } catch (HttpStatusException e) {
                if (!e.isRetryable() || attempt == 2) throw e;
                lastError = e;
            } catch (IOException e) {
                if (attempt == 2) throw e;
                lastError = e;
            }

            try {
                Thread.sleep(250L << attempt);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                java.io.InterruptedIOException canceled = new java.io.InterruptedIOException(
                        "Interrupted while retrying a network request");
                canceled.initCause(interrupted);
                throw canceled;
            }
        }
        throw lastError != null ? lastError : new IOException("Download failed");
    }

    public static void downloadFile(String url, File out) throws IOException {
        FileUtils.ensureParentDirectory(out);
        File parent = out.getAbsoluteFile().getParentFile();
        File temporary = new File(parent, ".download-" + UUID.randomUUID() + ".tmp");
        boolean moved = false;
        try {
            try (FileOutputStream fileOutputStream = new FileOutputStream(temporary)) {
                download(url, fileOutputStream);
                fileOutputStream.getFD().sync();
            }
            renameOver(temporary, out);
            moved = true;
        } finally {
            if (!moved && temporary.exists() && !temporary.delete()) {
                Log.w("DownloadUtils", "Could not remove temporary download: " + temporary.getAbsolutePath());
            }
        }
    }

    public static void downloadFileMonitored(String urlInput, File outputFile, @Nullable byte[] buffer,
                                             Tools.DownloaderFeedback monitor) throws IOException {
        FileUtils.ensureParentDirectory(outputFile);
        File temporary = new File(outputFile.getAbsoluteFile().getParentFile(),
                ".download-" + UUID.randomUUID() + ".tmp");
        HttpURLConnection connection = null;
        boolean moved = false;
        try {
            connection = openConnection(new URL(urlInput), "GET");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new HttpStatusException(status, connection.getResponseMessage());
            }
            int length = connection.getContentLength();
            if (buffer == null || buffer.length == 0) buffer = new byte[32 * 1024];
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(temporary)) {
                int current;
                int overall = 0;
                while ((current = input.read(buffer)) != -1) {
                    output.write(buffer, 0, current);
                    overall += current;
                    if (monitor != null) monitor.updateProgress(overall, length);
                }
                output.getFD().sync();
            }
            renameOver(temporary, outputFile);
            moved = true;
        } catch (IOException e) {
            if (e instanceof HttpStatusException) throw e;
            throw new IOException("Unable to download from " + urlInput, e);
        } finally {
            if (connection != null) connection.disconnect();
            if (!moved && temporary.exists() && !temporary.delete()) {
                Log.w("DownloadUtils", "Could not remove temporary download: " + temporary.getAbsolutePath());
            }
        }
    }

    /** Returns a cached response even when stale, for UI-first display while a refresh runs. */
    @Nullable
    public static String readCachedString(String cacheName) {
        if (cacheName == null || !cacheName.matches("[A-Za-z0-9._%-]{1,256}")) return null;
        return readCachedStringFile(getStringCacheFile(cacheName));
    }

    private static String readCachedStringFile(File cacheDestination) {
        if (!cacheDestination.isFile() || !cacheDestination.canRead() ||
                cacheDestination.length() > MAX_STRING_RESPONSE_BYTES) return null;
        try (InputStream input = new java.io.FileInputStream(cacheDestination)) {
            return IOUtils.toString(input, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.i("DownloadUtils", "Failed to read the cached file", e);
            return null;
        }
    }

    private static File getStringCacheFile(String cacheName) {
        // Preserve the legacy cache naming used by other metadata fetchers, some of which include
        // the source URL. readCachedString applies a stricter validator for stale-while-revalidate use.
        return new File(Tools.DIR_CACHE, "string_cache/" + cacheName);
    }

    public static <T> T downloadStringCached(String url, String cacheName, ParseCallback<T> parseCallback)
            throws IOException, ParseException {
        File cacheDestination = getStringCacheFile(cacheName);
        if (cacheDestination.isFile() && cacheDestination.canRead() &&
                cacheDestination.length() <= MAX_STRING_RESPONSE_BYTES &&
                System.currentTimeMillis() < cacheDestination.lastModified() + 86400000L) {
            String cachedString = readCachedStringFile(cacheDestination);
            if (cachedString != null) {
                try {
                    return parseCallback.process(cachedString);
                } catch (ParseException e) {
                    Log.i("DownloadUtils", "Failed to parse the cached file", e);
                }
            }
        }

        String urlContent = downloadStringWithRetries(url);
        // Do not cache an unparseable response; callers get the parsing error unchanged.
        T parseResult = parseCallback.process(urlContent);
        boolean tryWriteCache = cacheDestination.exists()
                ? cacheDestination.canWrite()
                : FileUtils.ensureParentDirectorySilently(cacheDestination);
        if (tryWriteCache) {
            try {
                Tools.write(cacheDestination.getAbsolutePath(), urlContent);
            } catch (IOException e) {
                Log.i("DownloadUtils", "Failed to cache the string", e);
            }
        }
        return parseResult;
    }

    private static <T> T downloadFile(Callable<T> downloadFunction) throws IOException {
        try {
            return downloadFunction.call();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean verifyFile(File file, String sha1) {
        return file.exists() && Tools.compareSHA1(file, sha1);
    }

    public static <T> T ensureSha1(File outputFile, @Nullable String sha1, Callable<T> downloadFunction)
            throws IOException {
        if (sha1 == null) {
            if (outputFile.exists()) return null;
            return downloadFile(downloadFunction);
        }
        int attempts = 0;
        boolean fileOkay = verifyFile(outputFile, sha1);
        T result = null;
        while (attempts < 5 && !fileOkay) {
            attempts++;
            downloadFile(downloadFunction);
            fileOkay = verifyFile(outputFile, sha1);
        }
        if (!fileOkay) throw new SHA1VerificationException("SHA1 verification failed after 5 download attempts");
        return result;
    }

    /**
     * Get the content length for a URL, or -1 if the server does not provide a successful HEAD
     * response. Connections are always disconnected, including on timeout or HTTP error.
     */
    public static long getContentLength(String url) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(new URL(url), "HEAD");
            connection.setDoInput(false);
            connection.setDoOutput(false);
            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) return connection.getContentLength();
            return -1;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static final class BoundedOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final int maxBytes;
        private int written;

        BoundedOutputStream(OutputStream delegate, int maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override public void write(int value) throws IOException {
            reserve(1);
            delegate.write(value);
        }

        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            reserve(length);
            delegate.write(bytes, offset, length);
        }

        private void reserve(int amount) throws IOException {
            if (amount < 0 || written > maxBytes - amount) throw new IOException("Server response is too large");
            written += amount;
        }

        @Override public void flush() throws IOException { delegate.flush(); }
        @Override public void close() throws IOException { delegate.close(); }
    }

    public interface ParseCallback<T> {
        T process(String input) throws ParseException;
    }

    public static class ParseException extends Exception {
        public ParseException(Exception e) { super(e); }
    }

    public static class HttpStatusException extends IOException {
        public final int statusCode;

        HttpStatusException(int statusCode, String message) {
            super("HTTP " + statusCode + (message == null || message.isEmpty() ? "" : ": " + message));
            this.statusCode = statusCode;
        }

        public boolean isRetryable() {
            return statusCode == 408 || statusCode == 429 || statusCode >= 500;
        }
    }

    public static class SHA1VerificationException extends IOException {
        public SHA1VerificationException(String message) { super(message); }
    }
}
