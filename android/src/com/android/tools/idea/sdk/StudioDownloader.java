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
import com.android.tools.idea.sdk.progress.StudioProgressIndicatorAdapter;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.URL;

/**
 * A {@link Downloader} that uses Studio's {@link HttpRequests} to download files. Saves the file to a temp location and returns a
 * stream from that file.
 */
public class StudioDownloader implements Downloader {
  private com.intellij.openapi.progress.ProgressIndicator myStudioProgressIndicator;

  /**
   * Creates a new {@code StudioDownloader}. The current {@link com.intellij.openapi.progress.ProgressIndicator} will be picked up
   * when downloads are run.
   */
  public StudioDownloader() {}

  /**
   * Like {@link #StudioDownloader()}}, but will run downloads using the given {@link com.intellij.openapi.progress.ProgressIndicator}.
   * @param progress
   */
  public StudioDownloader(@Nullable com.intellij.openapi.progress.ProgressIndicator progress) {
    myStudioProgressIndicator = progress;
  }

  @Override
  public InputStream downloadAndStream(@NotNull URL url, @NotNull ProgressIndicator indicator)
    throws IOException {
    File file = downloadFully(url, indicator);
    if (file == null) {
      return null;
    }
    file.deleteOnExit();
    return new FileInputStream(file) {
      @Override
      public void close() throws IOException {
        super.close();
        file.delete();
      }
    };
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
    com.intellij.openapi.progress.ProgressIndicator studioProgress = myStudioProgressIndicator;
    if (studioProgress == null) {
      studioProgress = ProgressManager.getInstance().getProgressIndicator();
    }
    HttpRequests.request(url.toExternalForm())
      .saveToFile(target, new StudioProgressIndicatorAdapter(indicator, studioProgress));
  }

  @Nullable
  @Override
  public File downloadFully(@NotNull URL url,
                            @NotNull ProgressIndicator indicator) throws IOException {
    // TODO: caching
    String suffix = url.getPath();
    suffix = suffix.substring(suffix.lastIndexOf("/") + 1);
    File tempFile = FileUtil.createTempFile("StudioDownloader", suffix, true);
    tempFile.deleteOnExit();
    downloadFully(url, tempFile, null, indicator);
    return tempFile;
  }
}
