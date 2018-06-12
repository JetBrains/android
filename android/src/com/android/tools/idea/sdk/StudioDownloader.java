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
import com.android.repository.api.Downloader;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.devices.Storage;
import com.android.tools.idea.sdk.progress.StudioProgressIndicatorAdapter;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.HttpRequests;
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
      super(wrapped, null);
      mContentLength = contentLength;
      Storage storage = new Storage(mContentLength);
      mReasonableUnit = storage.getLargestReasonableUnits();
      mTotalDisplaySize = String.format("%.1f %s", storage.getPreciseSizeAsUnit(mReasonableUnit), mReasonableUnit.toString());
    }

    @Override
    public void setFraction(double fraction) {
      super.setFraction(fraction);

      int percentage = (int)(fraction * 100);
      if (percentage == mCurrentPercentage) {
        return; // Do not update too often
      }

      mCurrentPercentage = percentage;
      long downloadedSize = (long)(fraction * mContentLength);
      double downloadedSizeInReasonableUnits = new Storage(downloadedSize).getPreciseSizeAsUnit(mReasonableUnit);
      setText(String.format("Downloading (%1$d%%): %2$.1f / %3$s ...",
                            mCurrentPercentage, downloadedSizeInReasonableUnits, mTotalDisplaySize));
    }
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
  public void downloadFully(@NotNull URL url, @NotNull File target, @Nullable String checksum, @NotNull ProgressIndicator indicator)
    throws IOException {
    if (target.exists() && checksum != null) {
      if (checksum.equals(Downloader.hash(new BufferedInputStream(new FileInputStream(target)), target.length(), indicator))) {
        return;
      }
    }

    // We don't use the settings here explicitly, since HttpRequests picks up the network settings from studio directly.
    indicator.logInfo("Downloading " + url);
    indicator.setText("Downloading...");
    indicator.setSecondaryText(url.toString());
    // We can't pick up the existing studio progress indicator since the one passed in here might be a sub-indicator
    // working over a different range.
    HttpRequests.request(url.toExternalForm()).productNameAsUserAgent().connect(request -> {
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
}
