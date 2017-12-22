package org.jetbrains.android.download;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
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

  public static boolean makeSureProfilerIsInPlace() {
    File pluginDir = getPluginDir();
    if (pluginDir.exists()) return true;

    DownloadableFileService service = DownloadableFileService.getInstance();
    String fileName = "android-profiler-3.0.zip";
    DownloadableFileDescription
      description = service.createFileDescription("http://download.jetbrains.com/idea/android-profiler/3.0/" + fileName, fileName);
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
    return new File(PathManager.getSystemPath(), "android/profiler");
  }

  public static File getHostDir(String hostReleaseDir) {
    String path = StringUtil.trimStart(hostReleaseDir, "plugins/android");
    return new File(getPluginDir(), path);
  }
}
