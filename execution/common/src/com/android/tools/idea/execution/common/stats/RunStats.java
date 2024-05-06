/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.execution.common.stats;

import static com.android.tools.idea.execution.common.stats.RunStatsUtilsKt.getDeviceInfo;

import com.android.ddmlib.IDevice;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.analytics.UsageTrackerUtils;
import com.android.tools.deployer.model.component.ComponentType;
import com.android.tools.idea.stats.AnonymizerUtil;
import com.android.tools.tracer.Trace;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.LaunchTaskDetail;
import com.google.wireless.android.sdk.stats.RunEvent;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RunStats {

  public static final Key<RunStats> KEY = Key.create("android.stats.run");
  /**
   * A set of events related to the run, but not necessarily of kind {@link AndroidStudioEvent.EventKind#RUN_EVENT}, to be logged when
   * {@link #commit(RunEvent.Status)} is called.
   */
  private final Set<AndroidStudioEvent.Builder> myAdditionalOnCommitEvents;
  private final AndroidStudioEvent.Builder myEvent;
  private boolean myLogged;
  private final Project myProject;

  public RunStats(Project project) {
    myProject = project;
    myEvent = AndroidStudioEvent.newBuilder().setKind(AndroidStudioEvent.EventKind.RUN_EVENT);
    myAdditionalOnCommitEvents = new HashSet<>();
  }

  public void abort() {
    commit(RunEvent.Status.ABORT);
  }

  public void abandoned() {
    commit(RunEvent.Status.ABANDONED);
  }

  public void success() {
    commit(RunEvent.Status.SUCCESS);
  }

  public void fail() {
    commit(RunEvent.Status.FAIL);
  }

  private void commit(RunEvent.Status status) {
    Trace.end();
    Trace.flush();
    if (!myLogged) {
      myEvent.getRunEventBuilder()
        .setStatus(status)
        .setEndTimestampMs(System.currentTimeMillis());
      UsageTracker.log(UsageTrackerUtils.withProjectId(myEvent, myProject));

      for (AndroidStudioEvent.Builder event : myAdditionalOnCommitEvents) {
        UsageTracker.log(UsageTrackerUtils.withProjectId(event, myProject));
      }

      myLogged = true;
    }
  }

  public void start() {
    Trace.start();
    Trace.begin("start");
    myEvent.getRunEventBuilder().setBeginTimestampMs(System.currentTimeMillis());
  }

  public void markStateCreated() {
  }

  public void addAllLaunchTaskDetail(Iterable<LaunchTaskDetail> details) {
    myEvent.getRunEventBuilder().addAllLaunchTaskDetail(details);
  }

  public void beginBeforeRunTasks() {
    Trace.begin("beforeRunktask.");
    myEvent.getRunEventBuilder().setBeginBeforeRunTasksTimestampMs(System.currentTimeMillis());
  }

  public void endBeforeRunTasks() {
    Trace.end();
    myEvent.getRunEventBuilder().setEndBeforeRunTasksTimestampMs(System.currentTimeMillis());
  }

  public void beginWaitForDevice() {
    Trace.begin("WaitForDevice.");
    myEvent.getRunEventBuilder().setBeginWaitForDeviceTimestampMs(System.currentTimeMillis());
  }

  public void endWaitForDevice(@Nullable IDevice device) {
    Trace.end();
    myEvent.getRunEventBuilder().setEndWaitForDeviceTimestampMs(System.currentTimeMillis());
    if (device == null) {
      return;
    }
    myEvent.setDeviceInfo(getDeviceInfo(device, myProject));
    myEvent.getRunEventBuilder().setDeviceCount(myEvent.getRunEventBuilder().getDeviceCount() + 1);
  }

  public void setDebuggable(boolean debuggable) {
    myEvent.getRunEventBuilder().setDebuggable(debuggable);
  }

  public void setPackage(String packageName) {
    myEvent.setProjectId(AnonymizerUtil.anonymizeUtf8(packageName)).setRawProjectId(packageName);
  }

  public @NotNull CustomTask beginCustomTask(@NotNull String taskId) {
    return new CustomTask(LaunchTaskDetail.newBuilder()
                            .setId(taskId)
                            .setTid((int)Thread.currentThread().getId())
                            .setStartTimestampMs(System.currentTimeMillis()));
  }

  public void endCustomTask(@NotNull CustomTask task, @Nullable Throwable exception) {
    LaunchTaskDetail.Builder detail = task.getBuilder();
    detail.setEndTimestampMs(System.currentTimeMillis());
    detail.setStatus(exception == null ? "success" : "error");
    myEvent.getRunEventBuilder().addLaunchTaskDetail(detail);
  }

  public void setAppComponentType(ComponentType type) {
    RunEvent.AppComponent runEventType;
    switch (type) {
      case TILE:
        runEventType = RunEvent.AppComponent.TILE;
        break;
      case ACTIVITY:
        runEventType = RunEvent.AppComponent.ACTIVITY;
        break;
      case WATCH_FACE:
        runEventType = RunEvent.AppComponent.WATCH_FACE;
        break;
      case COMPLICATION:
        runEventType = RunEvent.AppComponent.COMPLICATION;
        break;
      default:
        runEventType = RunEvent.AppComponent.UNKNOWN;
    }
    myEvent.getRunEventBuilder().setAppComponentType(runEventType);
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
    Trace.begin("beginLaunchTasks");
    myEvent.getRunEventBuilder().setBeginLaunchTasksTimestampMs(System.currentTimeMillis());
  }

  public void endLaunchTasks() {
    Trace.end();
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

  public void setErrorId(String id) {
    myEvent.getRunEventBuilder().setDeployFailureId(id);
  }

  public void setApplyChangesFallbackToRun(boolean fallback) {
    myEvent.getRunEventBuilder().setApplyChangesFallbackToRun(fallback);
  }

  public void setApplyCodeChangesFallbackToRun(boolean fallback) {
    myEvent.getRunEventBuilder().setApplyCodeChangesFallbackToRun(fallback);
  }

  public void setRunAlwaysInstallWithPm(boolean alwaysUsesPackageManager) {
    myEvent.getRunEventBuilder().setRunAlwaysInstallWithPm(alwaysUsesPackageManager);
  }

  public void setIsComposeProject(boolean isCompose) {
    myEvent.getRunEventBuilder().setIsComposeProject(isCompose);
  }

  private void setPartial(boolean partial) {
    myEvent.getRunEventBuilder().setPartial(partial);
  }

  public void addAdditionalOnCommitEvent(AndroidStudioEvent.Builder event) {
    myAdditionalOnCommitEvents.add(event);
  }

  public static RunStats from(ExecutionEnvironment env) {
    RunStats data = env.getUserData(KEY);
    if (data == null) {
      // This would be unexpected, so a transient stats is created and marked as such.
      data = new RunStats(env.getProject());
      data.setPartial(true);
      env.putUserData(KEY, data);
    }
    return data;
  }

  public static class CustomTask {
    private final LaunchTaskDetail.Builder myBuilder;

    private CustomTask(LaunchTaskDetail.Builder builder) {
      myBuilder = builder;
    }

    public LaunchTaskDetail.Builder getBuilder() {
      return myBuilder;
    }
  }
}
