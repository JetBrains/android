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
package com.android.tools.idea.stats;

import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.GuardedBy;
import com.android.repository.Revision;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ResourceUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service for getting information on Android versions, including usage percentages.
 */
public class DistributionService {
  private static final Logger LOG = Logger.getInstance(DistributionService.class);
  private static final long REFRESH_INTERVAL = TimeUnit.DAYS.toMillis(1);
  private static final long RETRY_INTERVAL = TimeUnit.HOURS.toMillis(1);
  private static final String STATS_URL = "https://dl.google.com/android/studio/metadata/distributions.json";
  private static final String STATS_FILENAME = "distributions.json";
  private static final URL FALLBACK_URL = ResourceUtil.getResource(DistributionService.class, "wizardData", STATS_FILENAME);
  private static final File CACHE_PATH = new File(PathManager.getSystemPath(), "stats");
  private static final String FILE_PATTERN =
    FileUtil.getNameWithoutExtension(STATS_FILENAME) + "(_[0-9]+)?\\." + FileUtilRt.getExtension(STATS_FILENAME);

  private final Object myLock = new Object();
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  @GuardedBy("myLock")
  private List<Distribution> myDistributions;
  @GuardedBy("myLock")
  private final List<Runnable> mySuccesses = Lists.newLinkedList();
  @GuardedBy("myLock")
  private final List<Runnable> myFailures = Lists.newArrayList();
  @GuardedBy("myLock")
  private volatile boolean myRunning = false;
  @GuardedBy("myLock")
  private long myAttemptTime;
  @GuardedBy("myLock")
  private long myRefreshTime;

  @NotNull private final FileDownloader myDownloader;
  @NotNull private final File myCachePath;
  @NotNull private final URL myFallback;

  private static DistributionService ourInstance;

  public static DistributionService getInstance() {
    if (ourInstance == null) {
      DownloadableFileDescription description = DownloadableFileService.getInstance().createFileDescription(STATS_URL, STATS_FILENAME);
      FileDownloader downloader =
        DownloadableFileService.getInstance().createDownloader(ImmutableList.of(description), "Distribution Stats");
      ourInstance = new DistributionService(downloader, CACHE_PATH, FALLBACK_URL);
    }
    return ourInstance;
  }

  @Nullable
  public List<Distribution> getDistributions() {
    // No lock is required here since this read must be atomic according to the Java language spec
    return myDistributions;
  }

  /**
   * Gets the percentage of devices on {@code apiLevel} or older.
   * If the distributions haven't been loaded yet, this call will load them synchronously.
   */
  public double getSupportedDistributionForApiLevel(int apiLevel) {
    refreshSynchronously();
    List<Distribution> distributions = getDistributions();
    if (distributions == null) {
      return -1;
    }
    double unsupportedSum = 0;
    for (Distribution d : distributions) {
      if (d.getApiLevel() >= apiLevel) {
        break;
      }
      unsupportedSum += d.getDistributionPercentage();
    }
    return 1 - unsupportedSum;
  }

  /**
   * Gets the {@link Distribution} for the given api level.
   * If the distributions haven't been loaded yet, this call will load them synchronously.
   */
  @Nullable
  public Distribution getDistributionForApiLevel(int apiLevel) {
    refreshSynchronously();
    List<Distribution> distributions = getDistributions();
    if (distributions == null) {
      return null;
    }
    for (Distribution d : distributions) {
      if (d.getApiLevel() == apiLevel) {
        return d;
      }
    }
    return null;
  }

  /**
   * Loads the latest distributions, and returns when complete.
   */
  public void refreshSynchronously() {
    final Semaphore completed = new Semaphore();
    completed.down();
    Runnable complete = new Runnable() {
      @Override
      public void run() {
        completed.up();
      }
    };
    refresh(complete, complete);
    completed.waitFor();
  }

  /**
   * Loads the latest distributions asynchronously. Tries to load from STATS_URL. Failing that they will be loaded from FALLBACK_URL.
   * Callbacks will be run in a worker thread; you must invokeLater yourself if they need to make UI changes.
   *
   * @param success Callback to be run if the remote distributions are loaded successfully.
   * @param failure Callback to be run if the remote distributions are not successfully loaded.
   */
  public void refresh(@Nullable Runnable success, @Nullable Runnable failure) {
    final long time = System.currentTimeMillis();
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
        return;
      }
      else if (time < myAttemptTime + RETRY_INTERVAL) {
        runContinuations(myFailures);
        return;
      }

      myAttemptTime = time;
      myRunning = true;
    }
    ProgressManager.getInstance()
      .run(new Task.Backgroundable(null, "Downloading Stats", false, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          loadStatsSynchronously(time);
        }

        @Override
        public boolean isHeadless() {
          // Necessary, otherwise runs synchronously in unit tests.
          return false;
        }
      });
  }

  private void loadStatsSynchronously(long time) {
    try {
      File downloaded = null;
      try {
        List<Pair<File, DownloadableFileDescription>> result = myDownloader.download(myCachePath);
        if (!result.isEmpty()) {
          downloaded = fixupFile(result.get(0).getFirst());
        }
      }
      catch (Exception e) {
        // ignore -- downloaded will be null, so failure runner will run if we hadn't loaded something previously.
      }
      if (downloaded == null) {
        downloaded = findLatestDownload();
      }
      if (downloaded != null) {
        try {
          loadFromFile(downloaded.toURI().toURL());
          myRefreshTime = time;
        }
        catch (MalformedURLException e) {
          // this shouldn't happen. Ignore (myDistributions will be null)
        }
      }
    }
    finally {
      synchronized (myLock) {
        if (myDistributions == null) {
          loadFromFile(myFallback);
          runContinuations(myFailures);
        }
        else {
          runContinuations(mySuccesses);
        }
        myRunning = false;
      }
    }
  }

  private File findLatestDownload() {
    long latestModTime = 0;
    File latestFile = null;
    File[] files = myCachePath.listFiles();
    if (files != null) {
      for (File f : files) {
        if (f.getName().matches(FILE_PATTERN)) {
          if (f.lastModified() > latestModTime) {
            latestFile = f;
            latestModTime = f.lastModified();
          }
        }
      }
    }
    return latestFile;
  }

  // This is primarily to work around https://youtrack.jetbrains.com/issue/IDEA-145475
  private File fixupFile(File downloaded) {
    File target = new File(myCachePath, STATS_FILENAME).getAbsoluteFile();
    if (!FileUtil.filesEqual(downloaded.getAbsoluteFile(), target)) {
      try {
        if (target.delete()) {
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

  private void runContinuations(List<Runnable> continuations) {
    for (Runnable r : continuations) {
      r.run();
    }
    mySuccesses.clear();
    myFailures.clear();
  }

  @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
  DistributionService(@NotNull FileDownloader downloader, @NotNull File cachePath, @NotNull URL fallback) {
    myDownloader = downloader;
    myCachePath = cachePath;
    myFallback = fallback;
  }

  private void loadFromFile(@NotNull URL url) {
    try {
      String jsonString = ResourceUtil.loadText(url);
      List<Distribution> distributions = loadDistributionsFromJson(jsonString);
      myDistributions = distributions != null ? ImmutableList.copyOf(distributions) : null;
    }
    catch (IOException e) {
      LOG.error("Error while trying to load distributions file", e);
    }
  }

  @Nullable
  private static List<Distribution> loadDistributionsFromJson(String jsonString) {
    Type fullRevisionType = new TypeToken<Revision>() {
    }.getType();
    GsonBuilder gsonBuilder = new GsonBuilder().registerTypeAdapter(fullRevisionType, new JsonDeserializer<Revision>() {
      @Override
      public Revision deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return Revision.parseRevision(json.getAsString());
      }
    });
    Gson gson = gsonBuilder.create();
    Type listType = new TypeToken<ArrayList<Distribution>>() {
    }.getType();
    try {
      return gson.fromJson(jsonString, listType);
    }
    catch (JsonParseException e) {
      LOG.error("Parse exception while reading distributions.json", e);
    }
    return null;
  }
}
