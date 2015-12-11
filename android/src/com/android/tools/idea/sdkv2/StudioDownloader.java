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
package com.android.tools.idea.sdkv2;

import com.android.repository.api.Downloader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.SettingsController;
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
  private static final StudioDownloader INSTANCE = new StudioDownloader();

  public static Downloader getInstance() {
    return INSTANCE;
  }

  private StudioDownloader() {};

  @Override
  public InputStream download(@NotNull URL url, SettingsController settings, @NotNull ProgressIndicator indicator)
    throws IOException {
    // We don't use the settings here explicitly, since HttpRequests picks up the network settings from studio directly.
    indicator.logInfo("Downloading " + url);
    indicator.setText("Downloading...");
    indicator.setSecondaryText(url.toString());
    // TODO: caching
    String suffix = url.getPath();
    suffix = suffix.substring(suffix.lastIndexOf("/") + 1);
    File tempFile = FileUtil.createTempFile("StudioDownloader", suffix);
    HttpRequests.request(url.toExternalForm()).saveToFile(tempFile, new StudioProgressIndicatorAdapter(indicator));
    return new FileInputStream(tempFile);
  }
}
