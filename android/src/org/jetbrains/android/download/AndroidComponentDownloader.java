// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.download;

import com.android.tools.idea.IdeInfo;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
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
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.swing.SwingUtilities;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

public abstract class AndroidComponentDownloader {

  private static final Logger LOG = Logger.getInstance(AndroidComponentDownloader.class);
  private static final String VERSION = "221.0.19.0";
  public static final String BINTRAY_ANDROID_TOOLS_BASE =
    "https://cache-redirector.jetbrains.com/intellij-dependencies/org/jetbrains/intellij/deps/android/tools/base/";
  public static final String ZIP = "zip";
  public static final String ANDROID_GROUP_DISPLAY_ID = "Android";

  private ReentrantReadWriteLock downloadLock = new ReentrantReadWriteLock();

  public boolean makeSureComponentIsInPlace() {
    if (ApplicationManager.getApplication() == null || ApplicationManager.getApplication().isDisposed()) return false; // to support regular junit tests with no Application initialized
    if (IdeInfo.getInstance().isAndroidStudio()) return true;

    waitOtherThreadToCompleteIfNotInEDT();
    if (isAlreadyDownloaded()) return true;

    DownloadableFileService service = DownloadableFileService.getInstance();
    String fileName = getArtifactName() + "-" + getVersion() + "." + getExtension();
    DownloadableFileDescription description
      = service.createFileDescription(getBaseUrl() + getArtifactName() + "/" + getVersion() + "/" + fileName, fileName);
    FileDownloader downloader =
      service.createDownloader(Collections.singletonList(description), "Download Android Plugin component: " + getArtifactName());

    if (SwingUtilities.isEventDispatchThread()) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        return downloadWithLock(downloader);
      });
      return false;
    }
    else {
      return downloadWithLock(downloader);
    }
  }

  private void waitOtherThreadToCompleteIfNotInEDT() {
    if (!SwingUtilities.isEventDispatchThread()) {
      downloadLock.readLock().lock();
      downloadLock.readLock().unlock();
    }
  }

  private boolean isAlreadyDownloaded() {
    File pluginDir = getPluginDir();
    File preInstalledDir = getPreInstalledPluginDir();
    if (downloadLock.readLock().tryLock()) {
      try {
        // nobody is downloading/unzipping in parallel. The directory has valid content if it exists.
        return preInstalledDir.exists() || pluginDir.exists();
      }
      finally {
        downloadLock.readLock().unlock();
      }
    }
    else {
      // Other thread is downloading
      return false;
    }
  }

  private boolean downloadWithLock(FileDownloader downloader) {
    downloadLock.writeLock().lock();
    try {
      if (isAlreadyDownloaded()) return true;

      File pluginDir = getPluginDir();
      return downloadWithProgress(() -> doDownload(pluginDir, downloader));
    }
    finally {
      downloadLock.writeLock().unlock();
    }
  }

  private boolean downloadWithProgress(Computable<Boolean> downloadTask) {
    if (ProgressManager.getInstance().hasProgressIndicator()) {
      return downloadTask.compute();
    }
    else {
      BackgroundableProcessIndicator indicator =
        new BackgroundableProcessIndicator(null, AndroidBundle.message("downloading.android.plugin.components"),
                                           PerformInBackgroundOption.ALWAYS_BACKGROUND, null, null, true);
      return ProgressManager.getInstance().runProcess(downloadTask, indicator);
    }
  }

  protected boolean doDownload(File pluginDir, FileDownloader downloader) {
    Path tempDir = null;
    try {
      tempDir = Files.createTempDirectory("android-component-download");
      List<Pair<File, DownloadableFileDescription>> list = downloader.download(tempDir.toFile());
      File file = list.get(0).first;
      ZipUtil.extract(file, getTargetDir(pluginDir), null);
      return true;
    }
    catch (IOException e) {
      String message = "Can't download Android Plugin component: " + getArtifactName();
      LOG.warn(message, e);
      Notifications.Bus.notify(new Notification(ANDROID_GROUP_DISPLAY_ID, message, "Check logs for details", NotificationType.ERROR));
      return false;
    }
    finally {
      if (tempDir != null) {
        FileUtil.delete(tempDir.toFile());
      }
    }
  }

  protected File getTargetDir(File dir) {
    return dir;
  }

  @NotNull
  protected String getExtension() {
    return ZIP;
  }

  @NotNull
  protected String getVersion() {
    return VERSION;
  }

  @NotNull
  protected String getBaseUrl() {
    return BINTRAY_ANDROID_TOOLS_BASE;
  }

  @NotNull
  protected abstract String getArtifactName();

  public File getPluginDir() {
    return new File(PathManager.getSystemPath(), "android/" + getArtifactName() + "/" + getVersion());
  }


  protected abstract File getPreInstalledPluginDir();

  protected File getPreInstalledPluginDir(String hostReleaseDir) {
    return new File(PathManager.getHomePath(), hostReleaseDir);
  }

  public File getHostDir(String hostReleaseDir) {
    File preinstalledDir = getPreInstalledPluginDir(hostReleaseDir);

    if (preinstalledDir.exists()) return preinstalledDir;

    return new File(getPluginDir(), hostReleaseDir);
  }
}
