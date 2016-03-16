/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.sdk.legacy.remote.internal;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.prefs.AndroidLocation;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.utils.Pair;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.net.HttpConfigurable;
import org.apache.http.*;
import org.apache.http.message.BasicHeader;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * A simple cache for the XML resources handled by the SDK Manager.
 * <p/>
 * Callers should use {@link #openDirectUrl} to download "large files"
 * that should not be cached (like actual installation packages which are several MBs big)
 * and call {@link #openCachedUrl(String, ITaskMonitor)} to download small XML files.
 * <p/>
 * The cache can work in 3 different strategies (direct is a pass-through, fresh-cache is the
 * default and tries to update resources if they are older than 10 minutes by respecting
 * either ETag or Last-Modified, and finally server-cache is a strategy to always serve
 * cached entries if present.)
 */
public class DownloadCache {

    /*
     * HTTP/1.1 references:
     * - Possible headers:
     *     http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
     * - Rules about conditional requests:
     *     http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.3.4
     * - Error codes:
     *     http://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html#sec6.1.1
     */

    private static final boolean DEBUG = System.getenv("SDKMAN_DEBUG_CACHE") != null; //$NON-NLS-1$

    /** Key for the Status-Code in the info properties. */
    private static final String KEY_STATUS_CODE = "Status-Code";        //$NON-NLS-1$
    /** Key for the URL in the info properties. */
    private static final String KEY_URL = "URL";                        //$NON-NLS-1$

    /** Prefix of binary files stored in the {@link SdkConstants#FD_CACHE} directory. */
    private static final String BIN_FILE_PREFIX = "sdkbin";             //$NON-NLS-1$
    /** Prefix of meta info files stored in the {@link SdkConstants#FD_CACHE} directory. */
    private static final String INFO_FILE_PREFIX = "sdkinf";            //$NON-NLS-1$
    /* Revision suffixed to the prefix. */
    private static final String REV_FILE_PREFIX = "-1_";                //$NON-NLS-1$

    /**
     * Minimum time before we consider a cached entry is potentially stale.
     * Expressed in milliseconds.
     * <p/>
     * When using the {@link Strategy#FRESH_CACHE}, the cache will not try to refresh
     * a cached file if it's has been saved more recently than this time.
     * When using the direct mode or the serve mode, the cache either doesn't serve
     * cached files or always serves caches files so this expiration delay is not used.
     * <p/>
     * Default is 10 minutes.
     * <p/>
     * TODO: change for a dynamic preference later.
     */
    private static final long MIN_TIME_EXPIRED_MS =  10*60*1000;
    /**
     * Maximum time before we consider a cache entry to be stale.
     * Expressed in milliseconds.
     * <p/>
     * When using the {@link Strategy#FRESH_CACHE}, entries that have no ETag
     * or Last-Modified will be refreshed if their file timestamp is older than
     * this value.
     * <p/>
     * Default is 4 hours.
     * <p/>
     * TODO: change for a dynamic preference later.
     */
    private static final long MAX_TIME_EXPIRED_MS = 4*60*60*1000;

    /**
     * The maximum file size we'll cache for "small" files.
     * 640KB is more than enough and is already a stretch since these are read in memory.
     * (The actual typical size of the files handled here is in the 4-64KB range.)
     */
    private static final int MAX_SMALL_FILE_SIZE = 640 * 1024;

    /**
     * HTTP Headers that are saved in an info file.
     * For HTTP/1.1 header names, see http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
     */
    private static final String[] INFO_HTTP_HEADERS = {
        HttpHeaders.LAST_MODIFIED,
        HttpHeaders.ETAG,
        HttpHeaders.CONTENT_LENGTH,
        HttpHeaders.DATE
    };

    private final FileOp mFileOp;
    private final File mCacheRoot;
    private final Strategy mStrategy;

    public enum Strategy {
        /**
         * Exclusively serves data from the cache. If files are available in the
         * cache, serve them as is (without trying to refresh them). If files are
         * not available, they are <em>not</em> fetched at all.
         */
        ONLY_CACHE,
        /**
         * If the files are available in the cache, serve them as-is, otherwise
         * download them and return the cached version. No expiration or refresh
         * is attempted if a file is in the cache.
         */
        SERVE_CACHE,
        /**
         * If the files are available in the cache, check if there's an update
         * (either using an e-tag check or comparing to the default time expiration).
         * If files have expired or are not in the cache then download them and return
         * the cached version.
         */
        FRESH_CACHE,
        /**
         * Disables caching. URLs are always downloaded and returned directly.
         * Downloaded streams aren't cached locally.
         */
        DIRECT
    }

    /** Creates a default instance of the URL cache */
    public DownloadCache(@NonNull Strategy strategy) {
        this(FileOpUtils.create(), strategy);
    }

    /** Creates a default instance of the URL cache */
    public DownloadCache(@NonNull FileOp fileOp, @NonNull Strategy strategy) {
        mFileOp = fileOp;
        mCacheRoot = initCacheRoot();

        // If this is defined in the environment, never use the cache. Useful for testing.
        if (System.getenv("SDKMAN_DISABLE_CACHE") != null) {                 //$NON-NLS-1$
            strategy = Strategy.DIRECT;
        }

        mStrategy = mCacheRoot == null ? Strategy.DIRECT : strategy;
    }

    @NonNull
    public Strategy getStrategy() {
        return mStrategy;
    }

    /**
     * Removes all cached files from the cache directory.
     */
    public void clearCache() {
        if (mCacheRoot != null) {
            File[] files = mFileOp.listFiles(mCacheRoot);
            for (File f : files) {
                if (mFileOp.isFile(f)) {
                    String name = f.getName();
                    if (name.startsWith(BIN_FILE_PREFIX) || name.startsWith(INFO_FILE_PREFIX)) {
                        mFileOp.delete(f);
                    }
                }
            }
        }
    }

    /**
     * Returns the directory to be used as a cache.
     * Creates it if necessary.
     * Makes it possible to disable or override the cache location in unit tests.
     *
     * @return An existing directory to use as a cache root dir,
     *   or null in case of error in which case the cache will be disabled.
     */
    @VisibleForTesting(visibility=Visibility.PRIVATE)
    @Nullable
    protected File initCacheRoot() {
        try {
            File root = new File(AndroidLocation.getFolder());
            root = new File(root, SdkConstants.FD_CACHE);
            if (!mFileOp.exists(root)) {
                mFileOp.mkdirs(root);
            }
            return root;
        } catch (AndroidLocationException e) {
            // No root? Disable the cache.
            return null;
        }
    }

    /**
     * Calls {@link HttpConfigurable#openHttpConnection(String)}
     * to actually perform a download.
     * <p/>
     * Isolated so that it can be overridden by unit tests.
     */
    @VisibleForTesting(visibility=Visibility.PRIVATE)
    @NonNull
    protected Pair<InputStream, URLConnection> openUrl(
            @NonNull String url,
            boolean needsMarkResetSupport,
            @NonNull ITaskMonitor monitor,
            @Nullable Header[] headers) throws IOException, ProcessCanceledException {
        URLConnection connection = HttpConfigurable.getInstance().openConnection(url);
        if (headers != null) {
            for (Header header : headers) {
                connection.setRequestProperty(header.getName(), header.getValue());
            }
        }
        connection.connect();
        InputStream is = connection.getInputStream();
        if (needsMarkResetSupport) {
            is = ensureMarkReset(is);
        }

        return Pair.of(is, connection);
    }

    private InputStream ensureMarkReset(InputStream is) {
        // If the caller requires an InputStream that supports mark/reset, let's
        // make sure we have such a stream.
        if (is != null) {
            if (!is.markSupported()) {
                try {
                    // Consume the whole input stream and offer a byte array stream instead.
                    // This can only work sanely if the resource is a small file that can
                    // fit in memory. It also means the caller has no chance of showing
                    // a meaningful download progress.

                    InputStream is2 = toByteArrayInputStream(is);
                    if (is2 != null) {
                        try {
                            is.close();
                        }
                        catch (Exception ignore) {
                        }
                        return is2;
                    }
                }
                catch (Exception e3) {
                    // Ignore. If this can't work, caller will fail later.
                }
            }
        }
        return is;
    }

    // ByteArrayInputStream is the duct tape of input streams.
    private static InputStream toByteArrayInputStream(InputStream is) throws IOException {
        int inc = 4096;
        int curr = 0;
        byte[] result = new byte[inc];

        int n;
        while ((n = is.read(result, curr, result.length - curr)) != -1) {
            curr += n;
            if (curr == result.length) {
                byte[] temp = new byte[curr + inc];
                System.arraycopy(result, 0, temp, 0, curr);
                result = temp;
            }
        }

        return new ByteArrayInputStream(result, 0, curr);
    }


    /**
     * Does a direct download of the given URL using {@link HttpConfigurable#openHttpConnection(String)}.
     * This does not check the download cache and does not attempt to cache the file.
     * Instead the HttpClient library returns a progressive download stream.
     * <p/>
     * For details on realm authentication and user/password handling,
     * see {@link HttpConfigurable#openHttpConnection(String)}.
     * <p/>
     * The resulting input stream may not support mark/reset.
     *
     * @param urlString the URL string to be opened.
     * @param headers An optional set of headers to pass when requesting the resource. Can be null.
     * @param monitor {@link ITaskMonitor} which is related to this URL
     *                 fetching.
     * @return Returns a pair with a {@link InputStream} and an {@link HttpResponse}.
     *              The pair is never null.
     *              The input stream can be null in case of error, although in general the
     *              method will probably throw an exception instead.
     *              The caller should look at the response code's status and only accept the
     *              input stream if it's the desired code (e.g. 200 or 206).
     * @throws IOException Exception thrown when there are problems retrieving
     *                 the URL or its content.
     * @throws ProcessCanceledException Exception thrown if the user cancels the
     *              authentication dialog.
     */
    @NonNull
    public Pair<InputStream, URLConnection> openDirectUrl(
            @NonNull  String urlString,
            @Nullable Header[] headers,
            @NonNull  ITaskMonitor monitor)
                throws IOException, ProcessCanceledException {
        if (DEBUG) {
            System.out.println(String.format("%s : Direct download", urlString)); //$NON-NLS-1$
        }
        return openUrl(
                urlString,
                false /*needsMarkResetSupport*/,
                monitor,
                headers);
    }

    /**
     * This is a simplified convenience method that calls
     * {@link #openDirectUrl(String, Header[], ITaskMonitor)}
     * without passing any specific HTTP headers  and returns the resulting input stream
     * and the HTTP status code.
     * See the original method's description for details on its behavior.
     * <p/>
     * {@link #openDirectUrl(String, Header[], ITaskMonitor)} can accept customized
     * HTTP headers to send with the requests and also returns the full HTTP
     * response -- status line with code and protocol and all headers.
     * <p/>
     * The resulting input stream may not support mark/reset.
     *
     * @param urlString the URL string to be opened.
     * @param monitor {@link ITaskMonitor} which is related to this URL
     *                 fetching.
     * @return Returns a pair with a {@link InputStream} and an HTTP status code.
     *              The pair is never null.
     *              The input stream can be null in case of error, although in general the
     *              method will probably throw an exception instead.
     *              The caller should look at the response code's status and only accept the
     *              input stream if it's the desired code (e.g. 200 or 206).
     * @throws IOException Exception thrown when there are problems retrieving
     *                 the URL or its content.
     * @throws ProcessCanceledException Exception thrown if the user cancels the
     *              authentication dialog.
     * @see #openDirectUrl(String, Header[], ITaskMonitor)
     */
    @NonNull
    public Pair<InputStream, Integer> openDirectUrl(
            @NonNull  String urlString,
            @NonNull  ITaskMonitor monitor)
                throws IOException, ProcessCanceledException {
        if (DEBUG) {
            System.out.println(String.format("%s : Direct download", urlString)); //$NON-NLS-1$
        }
        Pair<InputStream, URLConnection> result = openUrl(
                urlString,
                false /*needsMarkResetSupport*/,
                monitor,
                null /*headers*/);
        Integer response = null;
        URLConnection connection = result.getSecond();
        if (connection instanceof HttpURLConnection) {
            response = ((HttpURLConnection)connection).getResponseCode();
        }
        return Pair.of(result.getFirst(), response);
    }

    /**
     * Downloads a small file, typically XML manifests.
     * The current {@link Strategy} governs whether the file is served as-is
     * from the cache, potentially updated first or directly downloaded.
     * <p/>
     * For large downloads (e.g. installable archives) please do not invoke the
     * cache and instead use the {@link #openDirectUrl} method.
     * <p/>
     * For details on realm authentication and user/password handling,
     * see {@link HttpConfigurable#openHttpConnection(String)}.
     *
     * @param urlString the URL string to be opened.
     * @param monitor {@link ITaskMonitor} which is related to this URL
     *            fetching.
     * @return Returns an {@link InputStream} holding the URL content.
     *   Returns null if there's no content (e.g. resource not found.)
     *   Returns null if the document is not cached and strategy is {@link Strategy#ONLY_CACHE}.
     * @throws IOException Exception thrown when there are problems retrieving
     *             the URL or its content.
     * @throws ProcessCanceledException Exception thrown if the user cancels the
     *              authentication dialog.
     */
    @NonNull
    public InputStream openCachedUrl(@NonNull String urlString, @NonNull ITaskMonitor monitor)
            throws IOException, ProcessCanceledException {
        // Don't cache in direct mode.
        if (mStrategy == Strategy.DIRECT) {
            Pair<InputStream, URLConnection> result = openUrl(
                    urlString,
                    true /*needsMarkResetSupport*/,
                    monitor,
                    null /*headers*/);
            return result.getFirst();
        }

        File cached = new File(mCacheRoot, getCacheFilename(urlString));
        File info   = new File(mCacheRoot, getInfoFilename(cached.getName()));

        boolean useCached = mFileOp.exists(cached);

        if (useCached && mStrategy == Strategy.FRESH_CACHE) {
            // Check whether the file should be served from the cache or
            // refreshed first.

            long cacheModifiedMs = mFileOp.lastModified(cached); /* last mod time in epoch/millis */
            boolean checkCache = true;

            Properties props = readInfo(info);
            if (props == null) {
                // No properties, no chocolate for you.
                useCached = false;
            } else {
                long minExpiration = System.currentTimeMillis() - MIN_TIME_EXPIRED_MS;
                checkCache = cacheModifiedMs < minExpiration;

                if (!checkCache && DEBUG) {
                    System.out.println(String.format(
                            "%s : Too fresh [%,d ms], not checking yet.",    //$NON-NLS-1$
                            urlString, cacheModifiedMs - minExpiration));
                }
            }

            if (useCached && checkCache) {
                assert props != null;

                // Right now we only support 200 codes and will requery all 404s.
                String code = props.getProperty(KEY_STATUS_CODE, "");   //$NON-NLS-1$
                useCached = Integer.toString(HttpStatus.SC_OK).equals(code);

                if (!useCached && DEBUG) {
                    System.out.println(String.format(
                            "%s : cache disabled by code %s",           //$NON-NLS-1$
                            urlString, code));
                }

                if (useCached) {
                    // Do we have a valid Content-Length? If so, it should match the file size.
                    try {
                        long length = Long.parseLong(props.getProperty(HttpHeaders.CONTENT_LENGTH,
                                                        "-1")); //$NON-NLS-1$
                        if (length >= 0) {
                            useCached = length == mFileOp.length(cached);

                            if (!useCached && DEBUG) {
                                System.out.println(String.format(
                                    "%s : cache disabled by length mismatch %d, expected %d", //$NON-NLS-1$
                                    urlString, length, cached.length()));
                            }
                        }
                    } catch (NumberFormatException ignore) {}
                }

                if (useCached) {
                    // Do we have an ETag and/or a Last-Modified?
                    String etag = props.getProperty(HttpHeaders.ETAG);
                    String lastMod = props.getProperty(HttpHeaders.LAST_MODIFIED);

                    if (etag != null || lastMod != null) {
                        // Details on how to use them is defined at
                        // http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.3.4
                        // Bottom line:
                        // - if there's an ETag, it should be used first with an
                        //   If-None-Match header. That's a strong comparison for HTTP/1.1 servers.
                        // - otherwise use a Last-Modified if an If-Modified-Since header exists.
                        // In this case, we place both and the rules indicates a spec-abiding
                        // server should strongly match ETag and weakly the Modified-Since.

                        // TODO there are some servers out there which report ETag/Last-Mod
                        // yet don't honor them when presented with a precondition. In this
                        // case we should identify it in the reply and invalidate ETag support
                        // for these servers and instead fallback on the pure-timeout case below.

                        AtomicInteger statusCode = new AtomicInteger(0);
                        InputStream is = null;
                        List<Header> headers = new ArrayList<Header>(2);

                        if (etag != null) {
                            headers.add(new BasicHeader(HttpHeaders.IF_NONE_MATCH, etag));
                        }

                        if (lastMod != null) {
                            headers.add(new BasicHeader(HttpHeaders.IF_MODIFIED_SINCE, lastMod));
                        }

                        if (!headers.isEmpty()) {
                            is = downloadAndCache(urlString, monitor, cached, info,
                                    headers.toArray(new Header[headers.size()]),
                                    statusCode);
                        }

                        if (is != null && statusCode.get() == HttpStatus.SC_OK) {
                            // The resource was modified, the server said there was something
                            // new, which has been cached. We can return that to the caller.
                            return is;
                        }

                        // If we get here, we should have is == null and code
                        // could be:
                        // - 304 for not-modified -- same resource, still available, in
                        //       which case we'll use the cached one.
                        // - 404 -- resource doesn't exist anymore in which case there's
                        //       no point in retrying.
                        // - For any other code, just retry a download.

                        if (is != null) {
                            try {
                                is.close();
                            } catch (Exception ignore) {}
                            is = null;
                        }

                        if (statusCode.get() == HttpStatus.SC_NOT_MODIFIED) {
                            // Cached file was not modified.
                            // Change its timestamp for the next MIN_TIME_EXPIRED_MS check.
                            cached.setLastModified(System.currentTimeMillis());

                            // At this point useCached==true so we'll return
                            // the cached file below.
                        } else {
                            // URL fetch returned something other than 200 or 304.
                            // For 404, we're done, no need to check the server again.
                            // For all other codes, we'll retry a download below.
                            useCached = false;
                            if (statusCode.get() == HttpStatus.SC_NOT_FOUND) {
                                return null;
                            }
                        }
                    } else {
                        // If we don't have an Etag nor Last-Modified, let's use a
                        // basic file timestamp and compare to a 1 hour threshold.

                        long maxExpiration = System.currentTimeMillis() - MAX_TIME_EXPIRED_MS;
                        useCached = cacheModifiedMs >= maxExpiration;

                        if (!useCached && DEBUG) {
                            System.out.println(String.format(
                                "[%1$s] cache disabled by timestamp %2$tD %2$tT < %3$tD %3$tT", //$NON-NLS-1$
                                urlString, cacheModifiedMs, maxExpiration));
                        }
                    }
                }
            }
        }

        if (useCached) {
            // The caller needs an InputStream that supports the reset() operation.
            // The default FileInputStream does not, so load the file into a byte
            // array and return that.
            try {
                InputStream is = readCachedFile(cached);
                if (is != null) {
                    if (DEBUG) {
                        System.out.println(String.format("%s : Use cached file", urlString)); //$NON-NLS-1$
                    }

                    return is;
                }
            } catch (IOException ignore) {}
        }

        if (!useCached && mStrategy == Strategy.ONLY_CACHE) {
            // We don't have a document to serve from the cache.
            if (DEBUG) {
                System.out.println(String.format("%s : file not in cache", urlString)); //$NON-NLS-1$
            }
            return null;
        }

        // If we're not using the cache, try to remove the cache and download again.
        try {
            mFileOp.delete(cached);
            mFileOp.delete(info);
        } catch (SecurityException ignore) {}

        return downloadAndCache(urlString, monitor, cached, info,
                null /*headers*/, null /*statusCode*/);
    }



    // --------------

    @Nullable
    private InputStream readCachedFile(@NonNull File cached) throws IOException {
        InputStream is = null;

        int inc = 65536;
        int curr = 0;
        long len = cached.length();
        assert len < Integer.MAX_VALUE;
        if (len >= MAX_SMALL_FILE_SIZE) {
            // This is supposed to cache small files, not 2+ GB files.
            return null;
        }
        byte[] result = new byte[(int) (len > 0 ? len : inc)];

        try {
            is = mFileOp.newFileInputStream(cached);

            int n;
            while ((n = is.read(result, curr, result.length - curr)) != -1) {
                curr += n;
                if (curr == result.length) {
                    byte[] temp = new byte[curr + inc];
                    System.arraycopy(result, 0, temp, 0, curr);
                    result = temp;
                }
            }

            return new ByteArrayInputStream(result, 0, curr);

        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignore) {}
            }
        }
    }

    /**
     * Download, cache and return as an in-memory byte stream.
     * The download is only done if the server returns 200/OK.
     * On success, store an info file next to the download with
     * a few headers.
     * <p/>
     * This method deletes the cached file and the info file ONLY if it
     * attempted a download and it failed to complete. It doesn't erase
     * anything if there's no download because the server returned a 404
     * or 304 or similar.
     *
     * @return An in-memory byte buffer input stream for the downloaded
     *   and locally cached file, or null if nothing was downloaded
     *   (including if it was a 304 Not-Modified status code.)
     */
    @Nullable
    private InputStream downloadAndCache(
            @NonNull String urlString,
            @NonNull ITaskMonitor monitor,
            @NonNull File cached,
            @NonNull File info,
            @Nullable Header[] headers,
            @Nullable AtomicInteger outStatusCode)
                throws IOException, ProcessCanceledException {
        InputStream is = null;
        OutputStream os = null;

        int inc = 65536;
        int curr = 0;
        byte[] result = new byte[inc];

        try {
            Pair<InputStream, URLConnection> r =
                openUrl(urlString, true /*needsMarkResetSupport*/, monitor, headers);

            is = r.getFirst();
            URLConnection connection = r.getSecond();

            if (DEBUG) {
                String message = null;
                if (connection instanceof HttpURLConnection) {
                    message = ((HttpURLConnection)connection).getResponseMessage();
                }
                System.out.println(String.format("%s : fetch: %s => %s",  //$NON-NLS-1$
                                                 urlString, headers == null ? "" : Arrays.toString(headers),    //$NON-NLS-1$
                                                 message));
            }

            int code = connection instanceof HttpURLConnection ? ((HttpURLConnection)connection).getResponseCode() : 200;

            if (outStatusCode != null) {
                outStatusCode.set(code);
            }

            if (code != HttpStatus.SC_OK) {
                // Only a 200 response code makes sense here.
                // Even the other 20x codes should not apply, e.g. no content or partial
                // content are not statuses we want to handle and should never happen.
                // (see http://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html#sec6.1.1 for list)
                return null;
            }

            os = mFileOp.newFileOutputStream(cached);

            int n;
            while ((n = is.read(result, curr, result.length - curr)) != -1) {
                if (os != null && n > 0) {
                    os.write(result, curr, n);
                }

                curr += n;

                if (os != null && curr > MAX_SMALL_FILE_SIZE) {
                    // If the file size exceeds our "small file size" threshold,
                    // stop caching. We don't want to fill the disk.
                    try {
                        os.close();
                    } catch (IOException ignore) {}
                    try {
                        cached.delete();
                        info.delete();
                    } catch (SecurityException ignore) {}
                    os = null;
                }
                if (curr == result.length) {
                    byte[] temp = new byte[curr + inc];
                    System.arraycopy(result, 0, temp, 0, curr);
                    result = temp;
                }
            }

            // Close the output stream, signaling it was stored properly.
            if (os != null) {
                try {
                    os.close();
                    os = null;
                    if (connection instanceof HttpURLConnection) {
                        saveInfo(urlString, (HttpURLConnection)connection, info);
                    }
                } catch (IOException ignore) {}
            }

            return new ByteArrayInputStream(result, 0, curr);

        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignore) {}
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ignore) {}
                // If we get here with the output stream not null, it means there
                // was an issue and we don't want to keep that file. We'll try to
                // delete it.
                try {
                    mFileOp.delete(cached);
                    mFileOp.delete(info);
                } catch (SecurityException ignore) {}
            }
        }
    }

    /**
     * Saves part of the HTTP Response to the info file.
     */
    private void saveInfo(
            @NonNull String urlString,
            @NonNull HttpURLConnection connection,
            @NonNull File info) throws IOException {
        Properties props = new Properties();

        // we don't need the status code & URL right now.
        // Save it in case we want to have it later (e.g. to differentiate 200 and 404.)
        props.setProperty(KEY_URL, urlString);
        props.setProperty(KEY_STATUS_CODE,
                Integer.toString(connection.getResponseCode()));

        for (String name : INFO_HTTP_HEADERS) {
            String h = connection.getHeaderField(name);
            if (h != null) {
                props.setProperty(name, h);
            }
        }

        mFileOp.saveProperties(info, props, "## Meta data for SDK Manager cache. Do not modify."); //$NON-NLS-1$
    }

    /**
     * Reads the info properties file.
     * @return The properties found or null if there's no file or it can't be read.
     */
    @Nullable
    private Properties readInfo(@NonNull File info) {
        if (mFileOp.exists(info)) {
            return mFileOp.loadProperties(info);
        }
        return null;
    }

    /**
     * Computes the cache filename for the given URL.
     * The filename uses the {@link #BIN_FILE_PREFIX}, the full URL string's hashcode and
     * a sanitized portion of the URL filename. The returned filename is never
     * more than 64 characters to ensure maximum file system compatibility.
     *
     * @param urlString The download URL.
     * @return A leaf filename for the cached download file.
     */
    @NonNull
    private String getCacheFilename(@NonNull String urlString) {

        int code = 0;
        for (int i = 0, j = urlString.length(); i < j; i++) {
            code = code * 31 + urlString.charAt(i);
        }
        String hash = String.format("%08x", code);

        String leaf = urlString.toLowerCase(Locale.US);
        if (leaf.length() >= 2) {
            int index = urlString.lastIndexOf('/', leaf.length() - 2);
            leaf = urlString.substring(index + 1);
        }

        leaf = leaf.replaceAll("[^a-z0-9_-]+", "_");
        leaf = leaf.replaceAll("__+", "_");

        leaf = hash + '-' + leaf;
        String prefix = BIN_FILE_PREFIX + REV_FILE_PREFIX;
        int n = 64 - prefix.length();
        if (leaf.length() > n) {
            leaf = leaf.substring(0, n);
        }

        return prefix + leaf;
    }

    @NonNull
    private String getInfoFilename(@NonNull String cacheFilename) {
        return cacheFilename.replaceFirst(BIN_FILE_PREFIX, INFO_FILE_PREFIX);
    }
}
