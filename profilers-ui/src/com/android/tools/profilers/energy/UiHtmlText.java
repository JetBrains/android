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
package com.android.tools.profilers.energy;

import com.android.tools.profiler.proto.EnergyProfiler;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Collectors;

/**
 * Helper of the energy details' html text building.
 */
final class UiHtmlText {
  @NotNull private final StringBuilder myStringBuilder;

  public UiHtmlText() {
    myStringBuilder = new StringBuilder();
    myStringBuilder.append("<html>");
  }

  public void appendTitle(@NotNull String title) {
    myStringBuilder.append("<p>").append(title).append("</p>");
  }

  public void appendTitleAndValues(@NotNull String title, @NotNull Iterable<String> values) {
    appendTitleAndValue(title, StringUtil.join(values, ", "));
  }

  public void appendTitleAndValue(@NotNull String title, @NotNull String value) {
    myStringBuilder.append("<p><b>").append(title).append("</b>:&nbsp;<span>");
    myStringBuilder.append(value).append("</span></p>");
  }

  public void appendNewLine() {
    myStringBuilder.append("<br>");
  }

  @NotNull
  public String toString() {
    return myStringBuilder.append("</html>").toString();
  }

  public void renderAlarmSet(@NotNull EnergyProfiler.AlarmSet alarmSet) {
    appendTitleAndValue("Type", alarmSet.getType().name());
    // triggerTimeMs depends on alarm type, see https://developer.android.com/reference/android/app/AlarmManager.html#constants.
    String triggerTime = StringUtil.formatDuration(alarmSet.getTriggerMs());
    switch (alarmSet.getType()) {
      case RTC:
      case RTC_WAKEUP:
        appendTitleAndValue("TriggerTime in System.currentTimeMillis()", triggerTime);
        break;
      case ELAPSED_REALTIME:
      case ELAPSED_REALTIME_WAKEUP:
        appendTitleAndValue("TriggerTime in SystemClock.elapsedRealtime()", triggerTime);
        break;
      default:
        break;
    }
    // Interval time and Window time are not required in all set methods, only visible when the value is not zero.
    if (alarmSet.getIntervalMs() > 0) {
      appendTitleAndValue("IntervalTime", StringUtil.formatDuration(alarmSet.getIntervalMs()));
    }
    if (alarmSet.getWindowMs() > 0) {
      appendTitleAndValue("WindowTime", StringUtil.formatDuration(alarmSet.getWindowMs()));
    }
    switch(alarmSet.getSetActionCase()) {
      case OPERATION:
        renderPendingIntent(alarmSet.getOperation());
        break;
      case LISTENER:
        appendTitleAndValue("ListenerTag", alarmSet.getListener().getTag());
        break;
      default:
        break;
    }
  }

  public void renderAlarmCancelled(@NotNull EnergyProfiler.AlarmCancelled alarmCancelled) {
    switch (alarmCancelled.getCancelActionCase()) {
      case OPERATION:
        renderPendingIntent(alarmCancelled.getOperation());
        break;
      case LISTENER:
        appendTitleAndValue("ListenerTag", alarmCancelled.getListener().getTag());
        break;
      default:
        break;
    }
  }

  private void renderPendingIntent(@NotNull EnergyProfiler.PendingIntent operation) {
    if (!operation.getCreatorPackage().isEmpty()) {
      String value = String.format("%s&nbsp;(UID:&nbsp;%d)", operation.getCreatorPackage(), operation.getCreatorUid());
      appendTitleAndValue("Creator", value);
    }
  }

  public void renderWakeLockAcquired(@NotNull EnergyProfiler.WakeLockAcquired wakeLockAcquired) {
    appendTitleAndValue("Tag", wakeLockAcquired.getTag());
    appendTitleAndValue("Level", wakeLockAcquired.getLevel().name());
    if (!wakeLockAcquired.getFlagsList().isEmpty()) {
      String creationFlags = wakeLockAcquired.getFlagsList().stream()
        .map(EnergyProfiler.WakeLockAcquired.CreationFlag::name)
        .collect(Collectors.joining(", "));
      appendTitleAndValue("Flags", creationFlags);
    }
  }

  public void renderJobScheduled(@NotNull EnergyProfiler.JobScheduled jobScheduled) {
    if (jobScheduled.hasJob()) {
      renderJobInfo(jobScheduled.getJob());
    }
    appendTitleAndValue("Result", jobScheduled.getResult().name());
  }

  private void renderJobInfo(@NotNull EnergyProfiler.JobInfo job) {
    appendTitleAndValue("JobId", String.valueOf(job.getJobId()));
    appendTitleAndValue("ServiceName", job.getServiceName());
    appendTitleAndValue("BackoffPolicy", job.getBackoffPolicy().name());
    appendTitleAndValue("InitialBackoffTime", StringUtil.formatDuration(job.getInitialBackoffMs()));

    appendTitleAndValue("IsPeriodic", String.valueOf(job.getIsPeriodic()));
    if (job.getIsPeriodic()) {
      appendTitleAndValue("FlexTime", StringUtil.formatDuration(job.getFlexMs()));
      appendTitleAndValue("IntervalTime", StringUtil.formatDuration(job.getIntervalMs()));
    }
    else {
      appendTitleAndValue("MinLatencyTime", StringUtil.formatDuration(job.getMinLatencyMs()));
      appendTitleAndValue("MaxExecutionDelayTime", StringUtil.formatDuration(job.getMaxExecutionDelayMs()));
    }

    appendTitleAndValue("NetworkType", job.getNetworkType().name());

    if (job.getTriggerContentUrisCount() != 0) {
      appendTitleAndValues("TriggerContentURIs", job.getTriggerContentUrisList());
    }
    appendTitleAndValue("TriggerContentMaxDelayTime", StringUtil.formatDuration(job.getTriggerContentMaxDelay()));
    appendTitleAndValue("TriggerContentUpdateDelayTime", StringUtil.formatDuration(job.getTriggerContentUpdateDelay()));

    appendTitleAndValue("PersistOnReboot", String.valueOf(job.getIsPersisted()));
    appendTitleAndValue("RequiresBatteryNotLow", String.valueOf(job.getIsRequireBatteryNotLow()));
    appendTitleAndValue("RequiresCharging", String.valueOf(job.getIsRequireCharging()));
    appendTitleAndValue("RequiresDeviceIdle", String.valueOf(job.getIsRequireDeviceIdle()));
    appendTitleAndValue("RequiresStorageNotLow", String.valueOf(job.getIsRequireStorageNotLow()));

    if (!job.getExtras().isEmpty()) {
      appendTitleAndValue("Extras", job.getExtras());
    }
    if (!job.getTransientExtras().isEmpty()) {
      appendTitleAndValue("TransientExtras", job.getTransientExtras());
    }
  }

  public void renderJobStarted(@NotNull EnergyProfiler.JobStarted jobStarted) {
    renderJobParams(jobStarted.getParams());
    appendTitleAndValue("WorkOngoing", String.valueOf(jobStarted.getWorkOngoing()));
  }

  public void renderJobStopped(@NotNull EnergyProfiler.JobStopped jobStopped) {
    renderJobParams(jobStopped.getParams());
    appendTitleAndValue("Reschedule", String.valueOf(jobStopped.getReschedule()));
  }

  public void renderJobFinished(@NotNull EnergyProfiler.JobFinished jobFinished) {
    renderJobParams(jobFinished.getParams());
    appendTitleAndValue("NeedsReschedule", String.valueOf(jobFinished.getNeedsReschedule()));
  }

  private void renderJobParams(@NotNull EnergyProfiler.JobParameters jobParams) {
    // Job Id is redundant from JobInfo, so does not show it.
    if (jobParams.getTriggeredContentAuthoritiesCount() != 0) {
      appendTitleAndValues("TriggerContentAuthorities", jobParams.getTriggeredContentAuthoritiesList());
    }
    if (jobParams.getTriggeredContentUrisCount() != 0) {
      appendTitleAndValues("TriggerContentUris", jobParams.getTriggeredContentUrisList());
    }
    appendTitleAndValue("IsOverrideDeadlineExpired", String.valueOf(jobParams.getIsOverrideDeadlineExpired()));
    appendTitleAndValue("Extras", jobParams.getExtras());
    appendTitleAndValue("TransientExtras", jobParams.getTransientExtras());
  }
}
