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

import com.android.annotations.Nullable;
import com.google.common.annotations.VisibleForTesting;
import com.android.repository.api.Downloader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.SettingsController;
import com.android.sdklib.devices.Storage;
import com.android.tools.idea.sdk.progress.StudioProgressIndicatorAdapter;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.RequestBuilder;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * A {@link Downloader} that uses Studio's {@link HttpRequests} to download files. Saves the file to a temp location and returns a
 * stream from that file.
 */
public class StudioDownloader implements Downloader {
  private static class DownloadProgressIndicator extends StudioProgressIndicatorAdapter {
    private final long mContentLength;
    private final String mTotalDisplaySize;
    private int mCurrentPercentage;
    private Storage.Unit mReasonableUnit;

    public DownloadProgressIndicator(@NotNull ProgressIndicator wrapped, long contentLength) {
      super(wrapped);
      mContentLength = contentLength;
      Storage storage = new Storage(mContentLength);
      mReasonableUnit = storage.getLargestReasonableUnits();
      mTotalDisplaySize = storage.toUiString(1);
    }

    @Override
    public void setFraction(double fraction) {
      super.setFraction(fraction);

      checkCanceled();
      int percentage = (int)(fraction * 100);
      if (percentage == mCurrentPercentage) {
        return; // Do not update too often
      }

      mCurrentPercentage = percentage;
      long downloadedSize = (long)(fraction * mContentLength);
      double downloadedSizeInReasonableUnits = new Storage(downloadedSize).getPreciseSizeAsUnit(mReasonableUnit);
      setText(String
                .format(Locale.US, "Downloading (%1$d%%): %2$.1f / %3$s ...", mCurrentPercentage, downloadedSizeInReasonableUnits,
                        mTotalDisplaySize));
    }
  }

  @NotNull private final SettingsController mySettingsController;

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
    Path file = downloadFully(url, indicator);
    if (file == null) {
      return null;
    }
    return Files.newInputStream(file, StandardOpenOption.DELETE_ON_CLOSE);
  }

  @Override
  public void downloadFully(@NotNull URL url, @NotNull File target, @Nullable String checksum,
                            @NotNull ProgressIndicator indicator) throws IOException {
    doDownloadFully(url, target, checksum, false, indicator);
  }

  @Override
  public void downloadFullyWithCaching(@NotNull URL url, @NotNull File target,
                                       @Nullable String checksum,
                                       @NotNull ProgressIndicator indicator) throws IOException {
    doDownloadFully(url, target, checksum, true, indicator);
  }

  private void doDownloadFully(@NotNull URL url, @NotNull File target, @Nullable String checksum,
                            boolean allowNetworkCaches, @NotNull ProgressIndicator indicator)
    throws IOException {
    if (target.exists() && checksum != null) {
      if (checksum.equals(Downloader.hash(new BufferedInputStream(new FileInputStream(target)), target.length(), indicator))) {
        return;
      }
    }

    String preparedUrl = prepareUrl(url);
    indicator.logInfo("Downloading " + preparedUrl);
    indicator.setText("Downloading...");
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

    rb.connect(request -> {
      long contentLength = request.getConnection().getContentLength();
      return request.saveToFile(target, new DownloadProgressIndicator(indicator, contentLength));
    });
  }

  @Nullable
  @Override
  public Path downloadFully(@NotNull URL url,
                            @NotNull ProgressIndicator indicator) throws IOException {
    // TODO: caching
    String suffix = url.getPath();
    suffix = suffix.substring(suffix.lastIndexOf('/') + 1);
    File tempFile = FileUtil.createTempFile("StudioDownloader", suffix, true);
    tempFile.deleteOnExit();
    downloadFully(url, tempFile, null, indicator);
    return tempFile.toPath();
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
}
