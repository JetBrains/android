// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.download;

import com.android.tools.idea.IdeInfo;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;
import com.intellij.util.io.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class AndroidProfilerDownloader {

  private static final Logger LOG = Logger.getInstance(AndroidProfilerDownloader.class);
  private static final String VERSION = "26.5.0.1";

  public static boolean makeSureProfilerIsInPlace() {
    if (ApplicationManager.getApplication() == null) return false; // to support regular junit tests with no Application initialized
    if (IdeInfo.getInstance().isAndroidStudio()) return true;
    File pluginDir = getPluginDir();
    if (pluginDir.exists()) return true;

    DownloadableFileService service = DownloadableFileService.getInstance();
    String fileName = "android-plugin-resources-" + VERSION + ".zip";
    DownloadableFileDescription
      description = service.createFileDescription("https://dl.bintray.com/jetbrains/intellij-third-party-dependencies/org/jetbrains/intellij/deps/android/tools/android-plugin-resources/" + VERSION + "/" + fileName, fileName);
    FileDownloader downloader = service.createDownloader(Collections.singletonList(description), "Download Android Profiler");
    try {
      Path tempDir = Files.createTempDirectory("android-profiler-download");
      List<Pair<File, DownloadableFileDescription>> list = downloader.download(tempDir.toFile());
      File file = list.get(0).first;
      ZipUtil.extract(file, pluginDir, null);
      FileUtil.delete(tempDir.toFile());
      return true;
    }
    catch (IOException e) {
      LOG.warn("Can't download Android profiler", e);
      FileUtil.delete(pluginDir);
      Notifications.Bus.notify(new Notification("Android", "Can't download Android profiler", "Check logs for details", NotificationType.ERROR));
      return false;
    }
  }

  private static File getPluginDir() {
    return new File(PathManager.getSystemPath(), "android/profiler/" + VERSION);
  }

  public static File getHostDir(String hostReleaseDir) {
    String path = StringUtil.trimStart(hostReleaseDir, "plugins/android");
    return new File(getPluginDir(), path);
  }
}
