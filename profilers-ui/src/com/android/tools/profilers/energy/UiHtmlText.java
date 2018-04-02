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

import java.util.ArrayList;
import java.util.List;

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
    myStringBuilder.append("<p><b>").append(title).append("</b></p>");
  }

  public void appendTitleAndValues(@NotNull String title, @NotNull Iterable<String> values) {
    appendTitleAndValue(title, StringUtil.join(values, ", "));
  }

  public void appendTitleAndValue(@NotNull String title, @NotNull String value) {
    myStringBuilder.append("<p><b>").append(title).append("</b>:&nbsp;<span>");
    myStringBuilder.append(value).append("</span></p>");
  }

  public void appendValueWithIndentation(String value, int intendation) {
    myStringBuilder.append("<p>").append(StringUtil.repeat("&nbsp;", intendation)).append(value).append("</p>");
  }

  public void appendNewLine() {
    myStringBuilder.append("<br>");
  }

  @NotNull
  public String toString() {
    return myStringBuilder.append("</html>").toString();
  }

  public void renderAlarmSet(@NotNull EnergyProfiler.AlarmSet alarmSet) {
    appendTitleAndValue("Type", EnergyDuration.getAlarmTypeName(alarmSet.getType()));
    // triggerTimeMs depends on alarm type, see https://developer.android.com/reference/android/app/AlarmManager.html#constants.
    String triggerTime = StringUtil.formatDuration(alarmSet.getTriggerMs());
    switch (alarmSet.getType()) {
      case RTC:
      case RTC_WAKEUP:
        appendTitleAndValue("Trigger Time in System.currentTimeMillis()", triggerTime);
        break;
      case ELAPSED_REALTIME:
      case ELAPSED_REALTIME_WAKEUP:
        appendTitleAndValue("Trigger Time in SystemClock.elapsedRealtime()", triggerTime);
        break;
      default:
        break;
    }
    // Interval time and Window time are not required in all set methods, only visible when the value is not zero.
    if (alarmSet.getIntervalMs() > 0) {
      appendTitleAndValue("Interval Time", StringUtil.formatDuration(alarmSet.getIntervalMs()));
    }
    if (alarmSet.getWindowMs() > 0) {
      appendTitleAndValue("Window Time", StringUtil.formatDuration(alarmSet.getWindowMs()));
    }
    switch(alarmSet.getSetActionCase()) {
      case OPERATION:
        renderPendingIntent(alarmSet.getOperation());
        break;
      case LISTENER:
        appendTitleAndValue("Listener tag", alarmSet.getListener().getTag());
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
    appendTitleAndValue("Level", EnergyDuration.getWakeLockLevelName(wakeLockAcquired.getLevel()));
  }

  public void renderJobScheduled(@NotNull EnergyProfiler.JobScheduled jobScheduled) {
    appendTitleAndValue("Result", getJobResult(jobScheduled.getResult()));
    if (jobScheduled.hasJob()) {
      renderJobInfo(jobScheduled.getJob());
    }
  }

  @NotNull
  private static String getJobResult(@NotNull EnergyProfiler.JobScheduled.Result result) {
    switch (result) {
      case RESULT_SUCCESS:
        return "SUCCESS";
      case RESULT_FAILURE:
        return "FAILURE";
      default:
        // Job result is a required field, so returns n/a.
        return "n/a";
    }
  }

  private void renderJobInfo(@NotNull EnergyProfiler.JobInfo job) {
    appendTitleAndValue("Job ID", String.valueOf(job.getJobId()));
    appendTitleAndValue("Service", job.getServiceName());

    String backoffPolicy = getBackoffPolicyName(job.getBackoffPolicy());
    if (!backoffPolicy.isEmpty()) {
      appendTitleAndValue("Backoff Criteria", String.join(" ", StringUtil.formatDuration(job.getInitialBackoffMs()), backoffPolicy));
    }

    if (job.getIsPeriodic()) {
      StringBuilder builder = new StringBuilder();
      builder.append(String.format("%s interval", StringUtil.formatDuration(job.getIntervalMs())));
      if (job.getFlexMs() > 0) {
        builder.append(String.format(", %s flex", StringUtil.formatDuration(job.getFlexMs())));
      }
      appendTitleAndValue("Periodic", builder.toString());
    }
    else {
      if (job.getMinLatencyMs() > 0) {
        appendTitleAndValue("Minimum Latency", StringUtil.formatDuration(job.getMinLatencyMs()));
      }
      if (job.getMaxExecutionDelayMs() > 0) {
        appendTitleAndValue("Override Deadline", StringUtil.formatDuration(job.getMaxExecutionDelayMs()));
      }
    }

    if (job.getIsPersisted()) {
      appendTitleAndValue("Is Persisted", String.valueOf(job.getIsPersisted()));
    }

    List<String> requiredList = new ArrayList<>();
    String networkType = getRequiredNetworkType(job.getNetworkType());
    if (!networkType.isEmpty()) {
      requiredList.add("- Network Type: " + networkType);
    }
    if (job.getIsRequireBatteryNotLow()) {
      requiredList.add("- Battery Not Low");
    }
    if (job.getIsRequireCharging()) {
      requiredList.add("- Charging");
    }
    if (job.getIsRequireDeviceIdle()) {
      requiredList.add("- Device Idle");
    }
    if (job.getIsRequireStorageNotLow()) {
      requiredList.add("- Storage Not Low");
    }
    if (!requiredList.isEmpty()) {
      appendTitle("Requires:");
      requiredList.forEach(s -> appendValueWithIndentation(s, 5));
    }

    if (job.getTriggerContentUrisCount() != 0) {
      appendTitleAndValues("Trigger Content URIs", job.getTriggerContentUrisList());
    }
    if (job.getTriggerContentMaxDelay() > 0) {
      appendTitleAndValue("Trigger Content Max Delay", StringUtil.formatDuration(job.getTriggerContentMaxDelay()));
    }
    if (job.getTriggerContentUpdateDelay() > 0) {
      appendTitleAndValue("Trigger Content Update Delay", StringUtil.formatDuration(job.getTriggerContentUpdateDelay()));
    }
  }

  @NotNull
  private static String getBackoffPolicyName(EnergyProfiler.JobInfo.BackoffPolicy policy) {
    switch (policy) {
      case BACKOFF_POLICY_LINEAR:
        return "Linear";
      case BACKOFF_POLICY_EXPONENTIAL:
        return "Exponential";
      default:
        return "";
    }
  }

  @NotNull
  private static String getRequiredNetworkType(EnergyProfiler.JobInfo.NetworkType networkType) {
    switch (networkType) {
      case NETWORK_TYPE_ANY:
        return "Any";
      case NETWORK_TYPE_METERED:
        return "Metered";
      case NETWORK_TYPE_UNMETERED:
        return "Unmetered";
      case NETWORK_TYPE_NOT_ROAMING:
        return "Not Roaming";
      default:
        return "";
    }
  }

  public void renderJobFinished(@NotNull EnergyProfiler.JobFinished jobFinished) {
    appendTitleAndValue("Needs Reschedule", String.valueOf(jobFinished.getNeedsReschedule()));
    renderJobParams(jobFinished.getParams());
  }

  private void renderJobParams(@NotNull EnergyProfiler.JobParameters jobParams) {
    // Job Id is redundant from JobInfo, so does not show it.
    if (jobParams.getTriggeredContentAuthoritiesCount() != 0) {
      appendTitleAndValues("Triggered Content Authorities", jobParams.getTriggeredContentAuthoritiesList());
    }
    if (jobParams.getTriggeredContentUrisCount() != 0) {
      appendTitleAndValues("Triggered Content URIs", jobParams.getTriggeredContentUrisList());
    }
    if (jobParams.getIsOverrideDeadlineExpired()) {
      appendTitleAndValue("Is Override Deadline Expired", String.valueOf(jobParams.getIsOverrideDeadlineExpired()));
    }
  }

  public void renderLocationUpdateRequested(@NotNull EnergyProfiler.LocationUpdateRequested locationRequested) {
    EnergyProfiler.LocationRequest request = locationRequested.getRequest();
    appendTitleAndValue("Priority", getLocationPriority(request.getPriority()));
    if (request.getIntervalMs() > 0) {
      appendTitleAndValue("Min Interval Time", StringUtil.formatDuration(request.getIntervalMs()));
    }
    if (request.getFastestIntervalMs() > 0) {
      appendTitleAndValue("Fastest Interval Time", StringUtil.formatDuration(request.getFastestIntervalMs()));
    }
    if (!request.getProvider().isEmpty()) {
      appendTitleAndValue("Provider", request.getProvider());
    }
    if (request.getSmallestDisplacementMeters() > 0) {
      appendTitleAndValue("Min Distance", request.getSmallestDisplacementMeters() + "m");
    }
    if (locationRequested.hasIntent()) {
      renderPendingIntent(locationRequested.getIntent());
    }
  }

  @NotNull
  private static String getLocationPriority(@NotNull EnergyProfiler.LocationRequest.Priority priority) {
    switch (priority) {
      case NO_POWER:
        return "No Power";
      case LOW_POWER:
        return "Low Power";
      case BALANCED:
        return "Balanced";
      case HIGH_ACCURACY:
        return "High Accuracy";
      default:
        // Priority is a required field, so returns n/a.
        return "n/a";
    }
  }
}
