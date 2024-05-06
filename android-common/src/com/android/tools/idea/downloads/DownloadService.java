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
package com.android.tools.idea.downloads;

import com.android.annotations.concurrency.GuardedBy;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Service for downloading data that is infrequently updated.
 */
public abstract class DownloadService {
  private static final long REFRESH_INTERVAL = TimeUnit.DAYS.toMillis(1);
  private static final long RETRY_INTERVAL = TimeUnit.HOURS.toMillis(1);

  private final FileDownloader myDownloader;
  private final String myServiceName;
  private final File myCachePath;
  private final URL myFallbackUrl;
  private final String myFilename;
  private final String myFilePattern;
  private final Object myLock;

  @GuardedBy("myLock")
  private final List<Runnable> mySuccesses = Lists.newLinkedList();
  @GuardedBy("myLock")
  private final List<Runnable> myFailures = new ArrayList<>();
  @GuardedBy("myLock")
  private volatile boolean myRunning = false;
  @GuardedBy("myLock")
  private long myAttemptTime;
  @GuardedBy("myLock")
  private long myRefreshTime;

  public DownloadService(@NotNull String serviceName,
                         @NotNull String downloadUrl,
                         @NotNull URL fallbackResourceUrl,
                         @NotNull File targetCachePath,
                         @NotNull String tempFilename,
                         @NotNull String filename) {
    this(DownloadableFileService.getInstance().createDownloader(ImmutableList.of(
      DownloadableFileService.getInstance().createFileDescription(downloadUrl, tempFilename)), serviceName),
         serviceName,
         fallbackResourceUrl,
         targetCachePath,
         filename);
  }

  protected DownloadService(@NotNull FileDownloader downloader,
                         @NotNull String serviceName,
                         @NotNull URL fallbackResourceUrl,
                         @NotNull File targetCachePath,
                         @NotNull String filename) {
    myDownloader = downloader;
    myServiceName = serviceName;
    myCachePath = targetCachePath;
    myFallbackUrl = fallbackResourceUrl;
    myFilename = filename;
    myFilePattern = FileUtil.getNameWithoutExtension(filename) + "(_[0-9]+)?\\." + FileUtilRt.getExtension(filename);
    myLock = new Object();
  }

  /**
   * Load the data from a downloaded file or from the fallback URL.
   * @param url of the data to load
   */
  public abstract void loadFromFile(@NotNull URL url);

  /**
   * Loads the latest file, and returns when complete.
   */
  public void refreshSynchronously() {
    Semaphore completed = new Semaphore();
    completed.down();
    Runnable complete = completed::up;
    refresh(complete, complete);
    completed.waitFor();
  }

  /**
   * Loads the latest distributions asynchronously. Tries to load from STATS_URL. Failing that they will be loaded from FALLBACK_URL.
   * Callbacks will be run in a worker thread; you must invokeLater yourself if they need to make UI changes.
   *
   * @param success Callback to be run if the remote distributions are loaded successfully.
   * @param failure Callback to be run if the remote distributions are not successfully loaded.
   * @param runWithProgressIndicator If true, uses a background progress indicator (shown on the status bar) during download.
   */
  public void refresh(@Nullable Runnable success, @Nullable Runnable failure, boolean runWithProgressIndicator) {
    long time = System.currentTimeMillis();
    synchronized (myLock) {
      if (success != null) {
        mySuccesses.add(success);
      }
      if (failure != null) {
        myFailures.add(failure);
      }
      if (myRunning) {
        if (time < myAttemptTime + RETRY_INTERVAL) {
          return;
        }
      }
      if (time < myRefreshTime + REFRESH_INTERVAL) {
        runContinuations(mySuccesses);
        clearCallbacks();
        return;
      }
      else if (time < myAttemptTime + RETRY_INTERVAL) {
        runContinuations(myFailures);
        clearCallbacks();
        return;
      }

      myAttemptTime = time;
      myRunning = true;
    }

    if (runWithProgressIndicator) {
      loadDataWithProgressIndicator(time);
    }
    else {
      ApplicationManager.getApplication().executeOnPooledThread(() -> loadSynchronously(time));
    }
  }

  public void refresh(@Nullable Runnable success, @Nullable Runnable failure) {
    refresh(success, failure, true);
  }

  private void loadDataWithProgressIndicator(long time) {
    ProgressManager.getInstance()
      .run(new Task.Backgroundable(null, "Downloading " + myServiceName, false, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          loadSynchronously(time);
        }

        @Override
        public boolean isHeadless() {
          // Necessary, otherwise runs synchronously in unit tests.
          return false;
        }
      });
  }

  private File loadData(@Nullable File downloaded) {
    if (downloaded == null) {
      downloaded = findLatestDownload();
    }
    if (downloaded != null) {
      try {
        loadFromFile(downloaded.toURI().toURL());
      }
      catch (MalformedURLException e) {
        // this shouldn't happen. Ignore.
      }
    }
    else {
      loadFromFile(myFallbackUrl);
    }
    return downloaded;
  }

  private void loadSynchronously(long time) {
    File downloaded = null;
    try {
      try {
        List<Pair<File, DownloadableFileDescription>> result = myDownloader.download(myCachePath);
        if (!result.isEmpty()) {
          downloaded = fixupFile(result.get(0).getFirst());
        }
      }
      catch (Exception e) {
        // ignore -- downloaded will be null, so failure runner will run if we hadn't loaded something previously.
      }
      downloaded = loadData(downloaded);
    }
    finally {
      List<Runnable> continuations;
      synchronized (myLock) {
        if (downloaded == null) {
          continuations = new ArrayList<>(myFailures);
        }
        else {
          continuations = new ArrayList<>(mySuccesses);
          myRefreshTime = time;
        }
        clearCallbacks();
        myRunning = false;
      }
      runContinuations(continuations);
    }
  }

  private void clearCallbacks() {
    synchronized (myLock) {
      mySuccesses.clear();
      myFailures.clear();
    }
  }

  private File findLatestDownload() {
    long latestModTime = 0;
    File latestFile = null;
    File[] files = myCachePath.listFiles();
    if (files != null) {
      for (File f : files) {
        if (f.getName().matches(myFilePattern)) {
          if (f.lastModified() > latestModTime) {
            latestFile = f;
            latestModTime = f.lastModified();
          }
        }
      }
    }
    return latestFile;
  }

  /**
   * This is primarily to work around https://youtrack.jetbrains.com/issue/IDEA-145475
   *
   * The file is first downloaded as {@code #tempFilename} and then renamed to {@code #myFilename}.
   */
  private File fixupFile(File downloaded) {
    File target = new File(myCachePath, myFilename).getAbsoluteFile();
    if (!FileUtil.filesEqual(downloaded.getAbsoluteFile(), target)) {
      try {
        if (!target.exists() || target.delete()) {
          if (downloaded.renameTo(target)) {
            downloaded = target;
          }
        }
      }
      catch (SecurityException e) {
        // ignore. Just keep the file that was downloaded.
      }
    }
    return downloaded;
  }

  private static void runContinuations(List<Runnable> continuations) {
    for (Runnable runnable : continuations) {
      runnable.run();
    }
  }
}
