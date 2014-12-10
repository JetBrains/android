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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
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
public final class DownloadOperation extends InstallOperation<File, File> {
  @NotNull private final String myUrl;

  public DownloadOperation(@NotNull InstallContext context, @NotNull String url, double progressShare) {
    super(context, progressShare);
    myUrl = url;
  }

  @NotNull
  private static String getFileName(@NotNull String urlString) {
    try {
      // In case we need to strip query string
      if (URLUtil.containsScheme(urlString)) {
        URL url = new URL(urlString);
        return PathUtil.getFileName(url.getPath());
      }
    }
    catch (MalformedURLException e) {
      // Ignore it
    }
    return PathUtil.getFileName(urlString);
  }

  @Override
  @NotNull
  protected File perform(@NotNull ProgressIndicator indicator, @NotNull File arg) throws WizardException, InstallationCancelledException {
    // Progress indicator will be handled by IntelliJ code
    DownloadableFileService fileService = DownloadableFileService.getInstance();
    DownloadableFileDescription myDescription = fileService.createFileDescription(myUrl, getFileName(myUrl));
    FileDownloader downloader = fileService.createDownloader(ImmutableList.of(myDescription), "Android Studio components");
    while (true) {
      try {
        List<Pair<File, DownloadableFileDescription>> result = downloader.download(myContext.getTempDirectory());
        if (result.size() == 1) {
          return result.get(0).getFirst();
        }
        else {
          throw new WizardException("Unable to download " + myUrl);
        }
      }
      catch (IOException e) {
        String details = StringUtil.isEmpty(e.getMessage()) ? "Unable to download Android Studio components." : e.getMessage();
        promptToRetry(details + "\n\nPlease check your Internet connection and retry.", details, e);
      }
      catch (ProcessCanceledException e) {
        throw new InstallationCancelledException();
      }
    }
  }

  @Override
  public void cleanup(@NotNull File result) {
    if (result.isFile() && FileUtil.isAncestor(result, myContext.getTempDirectory(), false)) {
      FileUtil.delete(result);
    }
  }
}
