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
import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class InstantRunStatsService {
  // On average, we'll lose stats from half the upload interval.
  public static final int UPLOAD_INTERVAL_MINUTES = 10;

  public enum DeployType {
    LEGACY,
    HOTSWAP,
    SPLITAPK,
    DEX,
  }

  private final Object LOCK = new Object();

  private int myDeployCount;
  private int[] myDeployTypeCounts = new int[DeployType.values().length];

  private long myBuildTimes;
  private final Stopwatch myStopwatch = Stopwatch.createUnstarted();

  private int myRestartLaunchCount;

  public static InstantRunStatsService get(@NotNull Project project) {
    return ServiceManager.getService(project, InstantRunStatsService.class);
  }

  private InstantRunStatsService() {
    JobScheduler.getScheduler().scheduleAtFixedRate(new Runnable() {
      @Override
      public void run() {
        uploadStats();
      }
    }, UPLOAD_INTERVAL_MINUTES, UPLOAD_INTERVAL_MINUTES, TimeUnit.MINUTES);
  }

  public void notifyBuildStarted() {
    synchronized (LOCK) {
      myStopwatch.reset();
      myStopwatch.start();
    }
  }

  public void notifyBuildComplete() {
    synchronized (LOCK) {
      myBuildTimes += myStopwatch.elapsed(TimeUnit.MILLISECONDS);
      myDeployCount++;

      myStopwatch.stop();
    }
  }


  public void notifyDeployType(@NotNull DeployType type) {
    synchronized (LOCK) {
      myDeployTypeCounts[type.ordinal()]++;
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
    long avgBuildTime;
    int restartCount;

    synchronized (LOCK) {
      if (myDeployCount == 0) {
        return;
      }

      deployCount = myDeployCount;
      avgBuildTime = myBuildTimes / deployCount;
      restartCount = myRestartLaunchCount;
      System.arraycopy(myDeployTypeCounts, 0, deployTypeCount, 0, myDeployTypeCounts.length);

      myDeployCount = 0;
      myBuildTimes = 0;
      myRestartLaunchCount = 0;
      for (int i = 0; i < myDeployTypeCounts.length; i++) {
        myDeployTypeCounts[i] = 0;
      }
    }

    Map<String,String> kv = Maps.newHashMap();
    kv.put("deploycount", Integer.toString(deployCount));
    kv.put("avgbuild", Long.toString(avgBuildTime));
    kv.put("restartBuild", Integer.toString(restartCount));
    for (DeployType type : DeployType.values()) {
      kv.put(type.toString(), Integer.toString(deployTypeCount[type.ordinal()]));
    }

    UsageTracker.getInstance().trackInstantRunStats(kv);
  }
}
