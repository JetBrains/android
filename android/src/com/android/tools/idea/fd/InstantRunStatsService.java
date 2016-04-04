/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.fd;

import com.android.tools.idea.stats.UsageTracker;
import com.google.common.collect.Maps;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class InstantRunStatsService {
  // On average, we'll lose stats from half the upload interval.
  public static final int UPLOAD_INTERVAL_MINUTES = 10;

  public enum DeployType {
    LEGACY,   // full apk installation when IR is disabled
    FULLAPK,  // full apk installation when IR is enabled
    HOTSWAP,
    SPLITAPK, // split apk installation as part of cold swap (however, split APKs are currently disabled..)
    DEX,      // cold swap scheme that uses dex files
  }

  private final Object LOCK = new Object();

  /**
   * Current session id: A session starts from installing an APK, continues through multiple hot/cold swaps until the next full apk install
   */
  private UUID mySessionId;

  /**
   * Time (in ms) at which the build was started. The build & deploy is considered done when {@link #notifyDeployType(DeployType)} is
   * invoked. It is possible for the deploy type notification to never show up (e.g. build failures).
   *
   * NOTE: the combination of a single variable for build start time, and this service being scoped at a project level could cause
   * conflicts if multiple launches from multiple modules are performed in parallel
   */
  private long myBuildStartTime;

  /** A map from the deploy type to a list of timings for each deployment of that type. */
  private StringBuilder[] myDeployTimesByType = new StringBuilder[DeployType.values().length];

  /**
   * Keeps track of the length of the deploy timings so as to perform an early upload (before {@link #UPLOAD_INTERVAL_MINUTES} have
   * elapsed) if the constructed URL looks like it may exceed {@link UsageTracker#MAX_URL_LENGTH}.
   */
  private int myDeployTimesLength = 0;

  private int myDeployStartedCount;
  private int[] myDeployTypeCounts = new int[DeployType.values().length];

  private int myRestartLaunchCount;

  public static InstantRunStatsService get(@NotNull Project project) {
    return ServiceManager.getService(project, InstantRunStatsService.class);
  }

  private InstantRunStatsService() {
    JobScheduler.getScheduler().scheduleWithFixedDelay(new Runnable() {
      @Override
      public void run() {
        uploadStats();
      }
    }, UPLOAD_INTERVAL_MINUTES, UPLOAD_INTERVAL_MINUTES, TimeUnit.MINUTES);
  }

  public void notifyBuildStarted() {
    synchronized (LOCK) {
      myBuildStartTime = System.currentTimeMillis();
    }
  }

  public void notifyDeployStarted() {
    synchronized (LOCK) {
      myDeployStartedCount++;
    }
  }

  public void notifyDeployType(@NotNull DeployType type) {
    synchronized (LOCK) {
      long buildAndDeployTime = System.currentTimeMillis() - myBuildStartTime;

      if (type == DeployType.FULLAPK || type == DeployType.LEGACY || type == DeployType.SPLITAPK) {
        // We want to assign a session id for all launches in order to compute the number of hot/coldswaps between each APK push
        // Installing an APK starts a new session.
        resetSession();
      }
      else if (myDeployTimesLength > UsageTracker.MAX_URL_LENGTH - 50) {
        uploadStats();
      }

      int ordinal = type.ordinal();
      myDeployTypeCounts[ordinal]++;

      // append to the list of deploy timings
      StringBuilder timings = myDeployTimesByType[ordinal];
      if (timings == null) {
        timings = new StringBuilder(256);
      }
      else if (timings.length() > 0) {
        timings.append("a"); // a comma gets escaped by url escaper into 3 chars, so we use a single ascii char in lieu of the comma
        myDeployTimesLength++;
      }
      String timeInSeconds = Long.toString(buildAndDeployTime / 1000); // send time in seconds, also see param indicating time unit

      timings.append(timeInSeconds);
      myDeployTimesLength += timeInSeconds.length();

      myDeployTimesByType[ordinal] = timings;
    }
  }

  private void resetSession() {
    synchronized (LOCK) {
      if (mySessionId != null) {
        // Since we only keep track of the current session, upload all existing stats that belong to the previous session
        uploadStats();
      }

      mySessionId = UUID.randomUUID();
    }
  }

  public void incrementRestartLaunchCount() {
    synchronized (LOCK) {
      myRestartLaunchCount++;
    }
  }

  private void uploadStats() {
    int deployCount;
    int[] deployTypeCount = new int[myDeployTypeCounts.length];
    String[] deployTimings = new String[myDeployTimesByType.length];
    int restartCount;
    String sessionId;

    synchronized (LOCK) {
      if (myDeployStartedCount == 0) {
        return;
      }

      deployCount = myDeployStartedCount;
      restartCount = myRestartLaunchCount;
      System.arraycopy(myDeployTypeCounts, 0, deployTypeCount, 0, myDeployTypeCounts.length);

      myDeployStartedCount = 0;
      myRestartLaunchCount = 0;
      for (int i = 0; i < myDeployTypeCounts.length; i++) {
        myDeployTypeCounts[i] = 0;
      }

      for (int i = 0; i < myDeployTimesByType.length; i++) {
        if (myDeployTimesByType[i] == null) {
          continue;
        }

        deployTimings[i] = myDeployTimesByType[i].toString();
        myDeployTimesByType[i].setLength(0);
        myDeployTimesLength = 0;
      }
      sessionId = mySessionId.toString();
    }

    Map<String,String> kv = Maps.newHashMap();
    kv.put("deploycount", Integer.toString(deployCount));
    kv.put("restartBuild", Integer.toString(restartCount));
    kv.put("sessionId", sessionId);
    for (DeployType type : DeployType.values()) {
      kv.put(type.toString(), Integer.toString(deployTypeCount[type.ordinal()]));
    }

    UsageTracker.getInstance().trackInstantRunStats(kv);

    kv = Maps.newHashMap();
    for (DeployType type : DeployType.values()) {
      String deployTiming = deployTimings[type.ordinal()];
      if (!StringUtil.isEmpty(deployTiming)) {
        kv.put(type.toString(), deployTiming);
      }
    }

    if (kv.isEmpty()) {
      return;
    }

    kv.put("timeUnit", "seconds");
    UsageTracker.getInstance().trackInstantRunTimings(kv);
  }
}
