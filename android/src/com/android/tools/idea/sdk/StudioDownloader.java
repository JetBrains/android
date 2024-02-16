/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.sdk;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.io.CancellableFileIo;
import com.android.repository.api.Checksum;
import com.android.repository.api.Downloader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.SettingsController;
import com.android.sdklib.devices.Storage;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.progress.StudioProgressIndicatorAdapter;
import com.android.utils.PathUtils;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.RequestBuilder;
import com.intellij.util.net.NetUtils;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link Downloader} that uses Studio's {@link HttpRequests} to download files. Saves the file to a temp location and returns a
 * stream from that file.
 */
public class StudioDownloader implements Downloader {
  @VisibleForTesting
  static final String DOWNLOAD_SUFFIX_FN = ".asdownload";

  @Nullable Path mDownloadIntermediatesLocation;

  @VisibleForTesting
  static class DownloadProgressIndicator extends StudioProgressIndicatorAdapter {
    private final String mTargetName;
    private final long mContentLength;
    private final String mTotalDisplaySize;
    private int mCurrentPercentage;
    private long mStartOffset;
    private Storage.Unit mReasonableUnit;

    DownloadProgressIndicator(@NotNull ProgressIndicator wrapped, @NotNull String targetName, long contentLength,
                              long startOffset) {
      super(wrapped);
      mTargetName = targetName;
      if (contentLength > 0) {
        mCurrentPercentage = (int)(mStartOffset / (double)contentLength);
        mContentLength = contentLength;
        mStartOffset = startOffset;
        Storage storage = new Storage(mContentLength);
        mReasonableUnit = storage.getLargestReasonableUnits();
        mTotalDisplaySize = storage.toUiString(1);
        setIndeterminate(false);
      }
      else {
        mCurrentPercentage = 0;
        mContentLength = 0;
        mStartOffset = 0;
        mTotalDisplaySize = null;
        setText(String.format("Downloading $1%s...", mTargetName));
        setIndeterminate(true);
      }
    }

    @Override
    public void setFraction(double fraction) {
      if (isIndeterminate()) {
        return;
      }

      double adjustedFraction = ((mStartOffset + fraction * (mContentLength - mStartOffset)) / mContentLength);
      super.setFraction(adjustedFraction);

      checkCanceled();
      int percentage = (int)(adjustedFraction * 100);
      if (percentage == mCurrentPercentage) {
        return; // Do not update too often
      }

      mCurrentPercentage = percentage;
      long downloadedSize = (long)(adjustedFraction * mContentLength);
      double downloadedSizeInReasonableUnits = new Storage(downloadedSize).getPreciseSizeAsUnit(mReasonableUnit);
      setText(String
                .format(Locale.US, "Downloading %1$s (%2$d%%): %3$.1f / %4$s ...", mTargetName, mCurrentPercentage,
                        downloadedSizeInReasonableUnits, mTotalDisplaySize));
    }
  }

  @NotNull private final SettingsController mySettingsController;

  @NonNull private final RepositoryAddonsListVersionUrlFilter myUrlFilter = new RiscVUrlFilter();


  public StudioDownloader() {
    this(StudioSettingsController.getInstance());
  }

  @VisibleForTesting
  StudioDownloader(@NotNull SettingsController settingsController) {
    mySettingsController = settingsController;
  }

  @Override
  @Nullable
  public InputStream downloadAndStream(@NotNull URL url, @NotNull ProgressIndicator indicator)
    throws IOException {
    return downloadAndStreamWithOptions(url, indicator, StandardOpenOption.DELETE_ON_CLOSE);
  }

  @Nullable
  public InputStream downloadAndStreamWithOptions(@NotNull URL url, @NotNull ProgressIndicator indicator, OpenOption... streamOpenOptions)
    throws IOException {
    Path file = downloadFully(url, indicator);
    if (file == null) {
      return null;
    }
    return CancellableFileIo.newInputStream(file, streamOpenOptions);
  }

  @Override
  public void downloadFully(@NotNull URL url, @NotNull Path target, @Nullable Checksum checksum,
                            @NotNull ProgressIndicator indicator) throws IOException {
    doDownloadFully(url, target, checksum, false, indicator);
  }

  @Override
  public void downloadFullyWithCaching(@NotNull URL url, @NotNull Path target,
                                       @Nullable Checksum checksum,
                                       @NotNull ProgressIndicator indicator) throws IOException {
    doDownloadFully(url, target, checksum, true, indicator);
  }

  @Override
  public void setDownloadIntermediatesLocation(@Nullable Path downloadIntermediatesLocation) {
    try {
      if (downloadIntermediatesLocation != null) {
        PathUtils.createDirectories(downloadIntermediatesLocation);
      }
      mDownloadIntermediatesLocation = downloadIntermediatesLocation;
    }
    catch (IOException exception) {
      Logger.getInstance(StudioDownloader.class).warn("Unable resolve intermediates location", exception);
      // Use the default temp dir.
    }
  }

  private void doDownloadFully(@NotNull URL url, @NotNull Path target, @Nullable Checksum checksum,
                            boolean allowNetworkCaches, @NotNull ProgressIndicator indicator)
    throws IOException {

   if (!myUrlFilter.isUrlAllowed(url)) {
       throw new HttpRequests.HttpStatusException("URL may exist, but is internally disabled by " + myUrlFilter.getClass().getName(), 404, url.toExternalForm());
   }

    if (CancellableFileIo.exists(target) && checksum != null) {
      if (checksum.getValue().equals(Downloader.hash(
        new BufferedInputStream(CancellableFileIo.newInputStream(target)), CancellableFileIo.size(target),
        checksum.getType(), indicator))) {
        return;
      }
    }

    String preparedUrl = prepareUrl(url);
    indicator.logInfo("Downloading " + preparedUrl);
    indicator.setText("Starting download...");
    indicator.setSecondaryText(preparedUrl);
    // We can't pick up the existing studio progress indicator since the one passed in here might be a sub-indicator working over a
    // different range.
    RequestBuilder rb = HttpRequests.request(preparedUrl).productNameAsUserAgent();
    if (mySettingsController.getForceHttp()) {
      // Ensure no default value interferes with the somewhat opposite Studio setting. At the same time, it is not the
      // exact opposite: we do not want to force https just because http is not forced. So only the 'false' case is
      // enacted here.
      rb.forceHttps(false);
    }
    // Whether to allow network caches depends on the semantics of this download request, which is only known
    // to the caller. For example, there are certain requests where caching must not be used, e.g., checks
    // for software updates availability. In that case requests should go directly to the original server
    // and the caller context would pass false. On the other hand, for a large file download which is not
    // expected to change often on the original server, using network caches may beneficial (e.g.,
    // for a considerable number of who are users behind a proxy, such as in a corporate environment).
    rb.tuner(c -> c.setUseCaches(allowNetworkCaches));

    Path interimDownload = getInterimDownloadLocationForTarget(target);
    boolean interimExists = CancellableFileIo.exists(interimDownload);
    if (interimExists) {
      // Partial download isn't exactly about network caching, but it's still an optimization that is put in place
      // for the same reason as network caches, and there are exactly the same cases when it should not be used too.
      // So rely on that flag value here to determine whether to attempt partial download re-use.
      if (allowNetworkCaches) {
        // https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.35
        String rangeHeader = String.format("bytes=%1$s-", CancellableFileIo.size(interimDownload));
        rb.tuner(c -> c.setRequestProperty("Range", rangeHeader));
      }
      else {
        FileUtil.delete(interimDownload);
      }
    }

    rb.connect(request -> {
      // If the range is specified, then the returned content length will be the length of the remaining content to download.
      // To simplify calculations, regard content length invariant: always keep the value as the full content length.
      long startOffset = interimExists ? CancellableFileIo.size(interimDownload) : 0;
      long contentLength = startOffset + request.getConnection().getContentLengthLong();
      DownloadProgressIndicator downloadProgressIndicator = new DownloadProgressIndicator(indicator, target.getFileName().toString(),
                                                                                          contentLength, startOffset);
      PathUtils.createDirectories(interimDownload.getParent());

      try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(interimDownload, StandardOpenOption.APPEND, StandardOpenOption.CREATE))) {
        NetUtils.copyStreamContent(downloadProgressIndicator, request.getInputStream(), out,
                                   request.getConnection().getContentLengthLong());
      }

      try {
        PathUtils.createDirectories(target.getParent());
        Files.move(interimDownload, target, StandardCopyOption.REPLACE_EXISTING);
        if (CancellableFileIo.exists(target) && checksum != null) {
          if (!checksum.getValue().equals(Downloader.hash(new BufferedInputStream(CancellableFileIo.newInputStream(target)),
                                               CancellableFileIo.size(target),
                                               checksum.getType(),
                                               indicator))) {
            throw new IllegalStateException("Checksum of the downloaded result didn't match the expected value.");
          }
        }
      }
      catch (Throwable e) {
        if (allowNetworkCaches) {
          indicator.logWarning("This download could not be finalized from the interim state. Retrying without caching.");
          doDownloadFully(url, target, checksum, false, indicator);
          return null;
        }
        else {
          throw e; // Re-throw. There is nothing we can do in this case.
        }
      }
      return target;
    });
  }

  @NonNull
  private Path getInterimDownloadLocationForTarget(@NonNull Path target) {
    if (mDownloadIntermediatesLocation != null) {
      return mDownloadIntermediatesLocation.resolve(target.getFileName().toString() + DOWNLOAD_SUFFIX_FN);
    }
    return target.getFileSystem().getPath(target + DOWNLOAD_SUFFIX_FN);
  }

  @Nullable
  @Override
  public Path downloadFully(@NotNull URL url,
                            @NotNull ProgressIndicator indicator) throws IOException {
    String suffix = url.getPath();
    suffix = suffix.substring(suffix.lastIndexOf('/') + 1);
    Path tempDir =
      mDownloadIntermediatesLocation == null ? Paths.get(System.getProperty("java.io.tmpdir")) : mDownloadIntermediatesLocation;
    Path tempFile = Files.createTempFile(tempDir, suffix, "");
    PathUtils.addRemovePathHook(tempFile);
    downloadFully(url, tempFile, null, indicator);
    return tempFile;
  }

  @VisibleForTesting
  @NotNull
  String prepareUrl(@NotNull URL url) {
    // HttpRequests picks up the network settings from studio directly, however we need to query settings for the
    // custom 'Force HTTP' option coming from the integrated SDK manager and persisted in the Studio-wide settings instance.
    String prepared = url.toExternalForm();
    if (mySettingsController.getForceHttp() && StringUtil.startsWith(prepared, "https:")) {
      prepared = "http:" + prepared.substring(6);
    }
    return prepared;
  }

  /**
   * Filter URLs that match "{@code https://dl.google.com/android/repository/addons_list-[xxx].xml}",
   * where "[xxx]" is an integer smaller than or equal to {@link #getMaximumVersionAllowed()}
   */
  @VisibleForTesting
  static abstract class RepositoryAddonsListVersionUrlFilter {

    /**
     * The official URL is "{@code https://dl.google.com/android/repository/addons_list-6.xml}", but
     * the "base" URL can be customized via system properties, so we check the last part of the path only.
     *
     * @see com.android.repository.impl.sources.RemoteListSourceProviderImpl#getSources(Downloader, ProgressIndicator, boolean)
     */
    private static final Pattern addonsListPattern = Pattern.compile("/addons_list-(\\d+).xml$");

    public boolean isUrlAllowed(@NotNull URL url) {
      int maximumVersionAllowed = getMaximumVersionAllowed();
      if (maximumVersionAllowed >= 0) {
        Matcher matcher = addonsListPattern.matcher(url.getPath());
        if (matcher.find()) {
          try {
            int versionNumber = Integer.parseInt(matcher.group(1));
            if (versionNumber > maximumVersionAllowed) {
              return false; // URL is *not* supported
            }
          }
          catch (NumberFormatException e) {
            // If the placeholder is not an integer value, fall-through, i.e. assume the URL is supported
          }
        }
      }
      return true; // URL is supported
    }

    /**
     * Returns the maximum version of `addons-list.xml` allowed, or {@code -1} to allow all versions.
     */
    protected abstract int getMaximumVersionAllowed();
  }

  /**
   * An {@link RepositoryAddonsListVersionUrlFilter} that filters out repository URLs specific to risc-v system images
   * (if {@link StudioFlags#RISC_V} is disabled).
   */
  @VisibleForTesting
  static class RiscVUrlFilter extends RepositoryAddonsListVersionUrlFilter {

    @Override
    protected int getMaximumVersionAllowed() {
      if (StudioFlags.RISC_V.get()) {
        return -1;
      } else {
        // If risc-v support is disabled, we allow addons-list until v5, as v6 is the first schema containing the definition of
        // v4 system images, which is the schema version where images supporting the "risc-v" ABI start to appear.
        return 5;
      }
    }
  }
}
