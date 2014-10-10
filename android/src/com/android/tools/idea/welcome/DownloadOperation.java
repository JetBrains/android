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

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Downloads files needed to setup Android Studio.
 */
public final class DownloadOperation extends PreinstallOperation {

  public DownloadOperation(InstallContext context) {
    super(context, 0.7);
  }

  @Override
  protected void perform() throws WizardException {
    DownloadableFileService fileService = DownloadableFileService.getInstance();
    FileDownloader downloader = fileService.createDownloader(myContext.getFilesToDownload(), "Android Studio components");
    do {
      try {
        List<Pair<File, DownloadableFileDescription>> result = downloader.download(myContext.getTempDirectory());
        for (Pair<File, DownloadableFileDescription> fileDescriptionPair : result) {
          myContext.setDownloadedLocation(fileDescriptionPair.getSecond(), fileDescriptionPair.getFirst());
        }
        break;
      }
      catch (IOException e) {
        String details = StringUtil.isEmpty(e.getMessage()) ? "." : (": " + e.getMessage());
        String prompt = String.format("Unable to download Android Studio components%s " +
                                      "Please check your Internet connection and retry.", details);
        promptToRetry(prompt, "Unable to download Android Studio components: ", e);
      }
    }
    while (true);
  }
}
