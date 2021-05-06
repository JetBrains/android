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

import com.android.annotations.concurrency.Slow;
import com.android.repository.Revision;
import com.android.tools.idea.downloads.DownloadService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ResourceUtil;
import com.intellij.util.download.FileDownloader;
import com.intellij.util.io.URLUtil;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Service for getting information on Android versions, including usage percentages.
 */
public class DistributionService extends DownloadService {
  private static final Logger LOG = Logger.getInstance(DistributionService.class);
  private static final String STATS_URL = "https://dl.google.com/android/studio/metadata/distributions.json";
  private static final String STATS_FILENAME = "distributions.json";
  private static final String DOWNLOAD_FILENAME = "distributions_temp.json";
  private static final URL FALLBACK_URL = DistributionService.class.getClassLoader().getResource("wizardData/" + STATS_FILENAME);
  private static final File CACHE_PATH = new File(PathManager.getSystemPath(), "stats");

  private List<Distribution> myDistributions;

  private static DistributionService ourInstance;

  @NotNull
  public static DistributionService getInstance() {
    if (ourInstance == null) {
      ourInstance = new DistributionService();
    }
    return ourInstance;
  }

  @Nullable
  public List<Distribution> getDistributions() {
    return myDistributions;
  }

  /**
   * Gets the percentage of devices on {@code apiLevel} or older.
   * If the distributions haven't been loaded yet, this call will load them synchronously.
   */
  @Slow
  public double getSupportedDistributionForApiLevel(int apiLevel) {
    if (apiLevel <= 0) {
      return 0;
    }
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

  @VisibleForTesting
  DistributionService(@NotNull FileDownloader downloader, @NotNull File cachePath, @NotNull URL fallback) {
    super(downloader, "Distribution Stats", fallback, cachePath, STATS_FILENAME);
  }

  private DistributionService() {
    super("Distribution Stats", STATS_URL, FALLBACK_URL, CACHE_PATH, DOWNLOAD_FILENAME, STATS_FILENAME);
  }

  @Override
  public void loadFromFile(@NotNull URL url) {
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
    GsonBuilder gsonBuilder = new GsonBuilder().registerTypeAdapter(
      fullRevisionType,
      (JsonDeserializer<Revision>)(json, typeOfT, context) -> Revision.parseRevision(json.getAsString()));
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
