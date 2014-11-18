/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * Downloads files needed to setup Android Studio.
 */
public final class DownloadOperation extends PreinstallOperation<File> {
  private final String myUrl;

  public DownloadOperation(InstallContext context, String url, double progressShare) {
    super(context, progressShare);
    myUrl = url;
  }

  @NotNull
  private String getFileName() {
    try {
      // In case we need to strip query string
      if (URLUtil.containsScheme(myUrl)) {
        URL url = new URL(myUrl);
        return PathUtil.getFileName(url.getPath());
      }
    }
    catch (MalformedURLException e) {
      // Ignore it
    }
    return PathUtil.getFileName(myUrl);
  }

  @Override
  @Nullable
  protected File perform() throws WizardException {
    DownloadableFileService fileService = DownloadableFileService.getInstance();
    DownloadableFileDescription myDescription = fileService.createFileDescription(myUrl, getFileName());
    FileDownloader downloader = fileService.createDownloader(ImmutableList.of(myDescription), "Android Studio components");
    while (true) {
      try {
        List<Pair<File, DownloadableFileDescription>> result = downloader.download(myContext.getTempDirectory());
        return result.size() == 1 ? result.get(0).getFirst() : null;
      }
      catch (IOException e) {
        String details = StringUtil.isEmpty(e.getMessage()) ? "." : (": " + e.getMessage());
        String message = WelcomeUIUtils.getMessageWithDetails("Unable to download Android Studio components", details);
        promptToRetry(message + " Please check your Internet connection and retry.", message, e);
      }
    }
  }
}
