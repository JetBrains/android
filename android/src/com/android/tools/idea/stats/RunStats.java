/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.ddmlib.IDevice;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.run.ApkFileUnit;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.ArtifactDetail;
import com.google.wireless.android.sdk.stats.LaunchTaskDetail;
import com.google.wireless.android.sdk.stats.RunEvent;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.Nullable;

public class RunStats {

  public static final Key<RunStats> KEY = Key.create("android.stats.run");

  private AndroidStudioEvent.Builder myEvent;

  private boolean myLogged;
  private Project myProject;

  public RunStats(Project project) {
    myProject = project;
    myEvent = AndroidStudioEvent.newBuilder().setKind(AndroidStudioEvent.EventKind.RUN_EVENT);
  }

  public void abort() {
    commit(RunEvent.Status.ABORT);
  }

  public void success() {
    commit(RunEvent.Status.SUCCESS);
  }

  public void fail() {
    commit(RunEvent.Status.FAIL);
  }

  private void commit(RunEvent.Status status) {
    if (!myLogged) {
      myEvent.getRunEventBuilder()
             .setStatus(status)
             .setEndTimestampMs(System.currentTimeMillis());
      UsageTracker.log(UsageTrackerUtils.withProjectId(myEvent, myProject));
      myLogged = true;
    }
  }

  public void start() {
    myEvent.getRunEventBuilder().setBeginTimestampMs(System.currentTimeMillis());
  }

  public void markStateCreated() {
  }

  public LaunchTaskDetail.Builder beginLaunchTask(LaunchTask task) {
    LaunchTaskDetail.Builder details = LaunchTaskDetail.newBuilder()
                                                       .setId(task.getId())
                                                       .setStartTimestampMs(System.currentTimeMillis());
    for (ApkInfo apk : task.getApkInfos()) {
      for (ApkFileUnit unit : apk.getFiles()) {
        details.addArtifact(ArtifactDetail.newBuilder().setSize(unit.getApkFile().length()));
      }
    }
    return details;
  }

  public void endLaunchTask(LaunchTaskDetail.Builder detail, boolean success) {
    detail.setEndTimestampMs(System.currentTimeMillis());
    myEvent.getRunEventBuilder().addLaunchTaskDetail(detail);
  }

  public void beginBeforeRunTasks() {
    myEvent.getRunEventBuilder().setBeginBeforeRunTasksTimestampMs(System.currentTimeMillis());
  }

  public void endBeforeRunTasks() {
    myEvent.getRunEventBuilder().setEndBeforeRunTasksTimestampMs(System.currentTimeMillis());
  }

  public void beginWaitForDevice() {
    myEvent.getRunEventBuilder().setBeginWaitForDeviceTimestampMs(System.currentTimeMillis());
  }

  public void endWaitForDevice(@Nullable IDevice device) {
    myEvent.getRunEventBuilder().setEndWaitForDeviceTimestampMs(System.currentTimeMillis());
    if (device == null) {
      return;
    }
    myEvent.setDeviceInfo(AndroidStudioUsageTracker.deviceToDeviceInfo(device));
    myEvent.getRunEventBuilder().setDeviceCount(myEvent.getRunEventBuilder().getDeviceCount() + 1);
  }

  public void setDebuggable(boolean debuggable) {
    myEvent.getRunEventBuilder().setDebuggable(debuggable);
  }

  public void setPackage(String packageName) {
    myEvent.setProjectId(AnonymizerUtil.anonymizeUtf8(packageName)).setRawProjectId(packageName);
  }

  public void setInstantRunEnabled(boolean enabled) {
    myEvent.getRunEventBuilder().setInstantRunEnabled(enabled);
  }

  public void setExecutor(String executorId) {
    myEvent.getRunEventBuilder().setExecutor(executorId);
  }

  public void setApplyChanges(boolean hotSwap) {
    myEvent.getRunEventBuilder().setApplyChanges(hotSwap);
  }

  public void setUserSelectedTarget(boolean selection) {
    myEvent.getRunEventBuilder().setUserSelectedTarget(selection);
  }

  public void beginLaunchTasks() {
    myEvent.getRunEventBuilder().setBeginLaunchTasksTimestampMs(System.currentTimeMillis());
  }

  public void endLaunchTasks() {
    myEvent.getRunEventBuilder().setEndLaunchTasksTimestampMs(System.currentTimeMillis());
  }

  public void setLaunchedDevices(boolean b) {
    myEvent.getRunEventBuilder().setLaunchedDevices(b);
  }

  public void setDeployedAsInstant(boolean instant) {
    myEvent.getRunEventBuilder().setDeployedAsInstant(instant);
  }

  public void setDeployedFromBundle(boolean fromBundle) {
    myEvent.getRunEventBuilder().setDeployedFromBundle(fromBundle);
  }

  public static RunStats from(ExecutionEnvironment env) {
    RunStats data = env.getUserData(KEY);
    if (data == null) {
      // This would be unexpected, so a transient stats is created and marked as such.
      data = new RunStats(env.getProject());
      data.setPartial(true);
    }
    return data;
  }

  private void setPartial(boolean partial) {
    myEvent.getRunEventBuilder().setPartial(partial);
  }
}
