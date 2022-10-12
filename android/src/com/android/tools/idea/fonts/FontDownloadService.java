/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.fonts;

import com.android.ide.common.fonts.FontDetail;
import com.android.ide.common.fonts.FontFamily;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.MessageType;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link FontDownloadService} is a service for downloading individual font (*.ttf) files.
 * For each request a list of {@link FontFamily} are specified and if we only want to
 * download the menu file (i.e. a *.ttf file for displaying the name of the font),
 * or if we want all the fonts in the family {@link FontDetail} to be downloaded as well.
 * The menu font file is usually a smaller font file (~4k).
 */
public class FontDownloadService {
  private static final NotificationGroup PROBLEM_NOTIFICATION_GROUP =
    NotificationGroupManager.getInstance().getNotificationGroup("Font Downloading Problems");

  private final DownloadableFontCacheServiceImpl myCacheService;
  private final File myFontPath;
  private final List<FontFamily> myFontsToDownload;
  private final boolean myDownloadMenuFontsOnly;
  private final Runnable mySuccess;
  private final Runnable myFailure;

  public static void download(@NotNull List<FontFamily> fontsToDownload,
                              boolean menuFontsOnly,
                              @Nullable Runnable success,
                              @Nullable Runnable failure) {
    FontDownloadService service = new FontDownloadService(fontsToDownload, menuFontsOnly, success, failure);
    service.download();
  }

  private FontDownloadService(@NotNull List<FontFamily> fontsToDownload,
                              boolean menuFontsOnly,
                              @Nullable Runnable success,
                              @Nullable Runnable failure) {
    myCacheService = DownloadableFontCacheServiceImpl.getInstance();
    myFontPath = myCacheService.getFontPath();
    myFontsToDownload = fontsToDownload;
    myDownloadMenuFontsOnly = menuFontsOnly;
    mySuccess = success;
    myFailure = failure;
  }

  public void download() {
    ApplicationManager.getApplication().executeOnPooledThread(this::performDownload);
  }

  private void performDownload() {
    List<DownloadableFileDescription> files = new ArrayList<>();
    for (FontFamily fontFamily : myFontsToDownload) {
      addFontFamily(files, fontFamily);
    }
    if (myFontPath == null) {
      notify(myFailure);
      return;
    }
    FontFileDownloader downloader = new FontFileDownloader(files);
    try {
      downloader.download(myFontPath);
      notify(mySuccess);
    }
    catch (Exception ex) {
      // The multiple file download failed.
      // Attempt to download the files one by one.
      if (performSingleFileDownloads(myFontPath, files)) {
        notify(mySuccess);
      }
      else if (myFailure != null) {
        notify(myFailure);
      }
    }
  }

  private static void notify(@Nullable Runnable callback) {
    if (callback != null) {
      callback.run();
    }
  }

  private static boolean performSingleFileDownloads(@NotNull File fontPath, @NotNull List<DownloadableFileDescription> files) {
    boolean success = true;
    for (DownloadableFileDescription file : files) {
      FontFileDownloader downloader = new FontFileDownloader(Collections.singletonList(file));
      try {
        downloader.download(fontPath);
      }
      catch (Exception ex) {
        String errorMessage = "Unable to download: " + file.getDownloadUrl();
        Logger.getInstance(FontDownloadService.class).warn(errorMessage, ex);

        ApplicationManager.getApplication().invokeLater(() ->
          PROBLEM_NOTIFICATION_GROUP.createNotification(errorMessage, MessageType.WARNING).notify(null)
        );
        success = false;
      }
    }
    return success;
  }

  private boolean cachedFileExists(@NotNull File relativeCachedFile) {
    File file = new File(myFontPath, relativeCachedFile.getPath());
    return file.exists();
  }

  private void addFontFamily(@NotNull List<DownloadableFileDescription> files, @NotNull FontFamily fontFamily) {
    File file = myCacheService.getRelativeCachedMenuFile(fontFamily);
    if (file != null && !cachedFileExists(file)) {
      files.add(createFileDescription(fontFamily.getMenu(), file));
    }
    if (!myDownloadMenuFontsOnly) {
      for (FontDetail font : fontFamily.getFonts()) {
        addFont(files, font);
      }
    }
  }

  private void addFont(@NotNull List<DownloadableFileDescription> files, @NotNull FontDetail font) {
    File file = myCacheService.getRelativeFontFile(font);
    if (file != null && !cachedFileExists(file)) {
      files.add(createFileDescription(font.getFontUrl(), file));
    }
  }

  @NotNull
  private static DownloadableFileDescription createFileDescription(@NotNull String url, @NotNull File relativeFile) {
    return DownloadableFileService.getInstance().createFileDescription(url, relativeFile.getPath());
  }
}
