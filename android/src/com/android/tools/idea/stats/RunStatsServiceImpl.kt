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
package com.android.tools.idea.stats

import com.android.annotations.VisibleForTesting
import com.android.ddmlib.IDevice
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.util.BuildMode
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.StudioRunEvent
import java.util.*

@VisibleForTesting
data class Run(val runId: UUID, val packageName: String, val runType: StudioRunEvent.RunType, val startTimestamp: Long) {
  var studioProcessFinishedTimestamp: Long? = null
  var emulatorStartTimestamp: Long? = null
  var emulatorFinishTimestamp: Long? = null
  var gradleInvokeTimestamp: Long? = null
  var gradleFinishedTimestamp: Long? = null
  var deployStartTimestamp: Long? = null
  var deployFinishTimestamp: Long? = null
  var runFinishTimestamp: Long? = null
}

/**
 * Project Service to track end to end deploy metrics. See go/studio-e2e
 * Used by other classes in various points to log timestamp of events and upload metrics
 * through [AndroidStudioUsageTracker].
 */
class RunStatsServiceImpl : RunStatsService() {
  private val lock = Any()

  override fun notifyRunStarted(packageName: String,
                                runType: String,
                                forceColdswap: Boolean,
                                instantRunEnabled: Boolean) {
    synchronized(lock) {
      myRun = Run(UUID.randomUUID(), packageName, determineRunType(runType), System.currentTimeMillis())
    }
    UsageTracker.getInstance().log(getEventBuilder(StudioRunEvent.newBuilder()
                                                     .setRunId(myRun.runId.toString())
                                                     .setRunType(myRun.runType)
                                                     .setSectionType(StudioRunEvent.SectionType.TOTAL)
                                                     .setEventType(StudioRunEvent.EventType.START)
                                                     .build(), myRun.packageName))
    UsageTracker.getInstance().log(getEventBuilder(StudioRunEvent.newBuilder()
                                                     .setRunId(myRun.runId.toString())
                                                     .setRunType(myRun.runType)
                                                     .setSectionType(StudioRunEvent.SectionType.STUDIO)
                                                     .setEventType(StudioRunEvent.EventType.START)
                                                     .build(), myRun.packageName))
  }

  override fun notifyStudioSectionFinished(isSuccessful: Boolean,
                                           isDebugging: Boolean,
                                           isInstantRun: Boolean) {
    synchronized(lock) {
      myRun.studioProcessFinishedTimestamp = System.currentTimeMillis()
    }
    UsageTracker.getInstance().log(getEventBuilder(StudioRunEvent.newBuilder()
                                                     .setRunId(myRun.runId.toString())
                                                     .setRunType(myRun.runType)
                                                     .setSectionType(StudioRunEvent.SectionType.STUDIO)
                                                     .setEventType(StudioRunEvent.EventType.FINISH)
                                                     .setDurationMs(
                                                       calcDuration(myRun.studioProcessFinishedTimestamp, myRun.startTimestamp).toInt())
                                                     .setIsSuccessful(isSuccessful)
                                                     .build(), myRun.packageName))
  }

  // TODO add gradle task, dynamic app info etc, target device
  override fun notifyGradleStarted(buildMode: BuildMode?) {
    synchronized(lock) {
      myRun.gradleInvokeTimestamp = System.currentTimeMillis()
    }
    UsageTracker.getInstance().log(getEventBuilder(StudioRunEvent.newBuilder()
                                                     .setRunId(myRun.runId.toString())
                                                     .setRunType(myRun.runType)
                                                     .setSectionType(StudioRunEvent.SectionType.GRADLE)
                                                     .setEventType(StudioRunEvent.EventType.START)
                                                     .setBuildMode(determineBuildMode(buildMode))
                                                     .build(), myRun.packageName))
  }

  override fun notifyGradleFinished(isSuccessful: Boolean) {
    synchronized(lock) {
      myRun.gradleFinishedTimestamp = System.currentTimeMillis()
    }
    UsageTracker.getInstance().log(getEventBuilder(StudioRunEvent.newBuilder()
                                                     .setRunId(myRun.runId.toString())
                                                     .setRunType(myRun.runType)
                                                     .setSectionType(StudioRunEvent.SectionType.GRADLE)
                                                     .setEventType(StudioRunEvent.EventType.FINISH)
                                                     .setDurationMs(
                                                       calcDuration(myRun.gradleFinishedTimestamp, myRun.gradleInvokeTimestamp).toInt())
                                                     .setIsSuccessful(isSuccessful)
                                                     .build(), myRun.packageName))
  }

  override fun notifyEmulatorStarting() {
    synchronized(lock) {
      myRun.emulatorStartTimestamp = System.currentTimeMillis()
    }
    UsageTracker.getInstance().log(getEventBuilder(StudioRunEvent.newBuilder()
                                                     .setRunId(myRun.runId.toString())
                                                     .setRunType(myRun.runType)
                                                     .setSectionType(StudioRunEvent.SectionType.EMULATOR)
                                                     .setEventType(StudioRunEvent.EventType.START)
                                                     .build(), myRun.packageName))
  }

  override fun notifyEmulatorStarted(isSuccessful: Boolean) {
    synchronized(lock) {
      myRun.emulatorFinishTimestamp = System.currentTimeMillis()
    }
    UsageTracker.getInstance().log(getEventBuilder(StudioRunEvent.newBuilder()
                                                     .setRunId(myRun.runId.toString())
                                                     .setRunType(myRun.runType)
                                                     .setSectionType(StudioRunEvent.SectionType.EMULATOR)
                                                     .setDurationMs(
                                                       calcDuration(myRun.emulatorFinishTimestamp, myRun.emulatorStartTimestamp).toInt())
                                                     .setIsSuccessful(isSuccessful)
                                                     .setEventType(StudioRunEvent.EventType.FINISH)
                                                     .build(), myRun.packageName))
  }

  override fun notifyDeployStarted(deployTask: StudioRunEvent.DeployTask,
                                   device: IDevice,
                                   artifactCount: Int,
                                   isPatchBuild: Boolean,
                                   dontKill: Boolean) {
    synchronized(lock) {
      myRun.deployStartTimestamp = System.currentTimeMillis()
    }
    UsageTracker.getInstance().log(getEventBuilder(StudioRunEvent.newBuilder()
                                                     .setRunId(myRun.runId.toString())
                                                     .setRunType(myRun.runType)
                                                     .setSectionType(StudioRunEvent.SectionType.DEPLOY)
                                                     .setEventType(StudioRunEvent.EventType.START)
                                                     .setArtifactCount(artifactCount)
                                                     .setDeployTask(deployTask)
                                                     .build(), myRun.packageName)
                                     .setDeviceInfo(AndroidStudioUsageTracker.deviceToDeviceInfo(device)))
  }

  override fun notifyDeployFinished(isSuccessful: Boolean) {
    synchronized(lock) {
      myRun.deployFinishTimestamp = System.currentTimeMillis()
    }
    UsageTracker.getInstance().log(getEventBuilder(StudioRunEvent.newBuilder()
                                                     .setRunId(myRun.runId.toString())
                                                     .setRunType(myRun.runType)
                                                     .setSectionType(StudioRunEvent.SectionType.DEPLOY)
                                                     .setEventType(StudioRunEvent.EventType.FINISH)
                                                     .setDurationMs(
                                                       calcDuration(myRun.deployFinishTimestamp, myRun.deployStartTimestamp).toInt())
                                                     .setIsSuccessful(isSuccessful)
                                                     .build(), myRun.packageName))
  }

  override fun notifyRunFinished(isSuccessful: Boolean) {
    synchronized(lock) {
      myRun.runFinishTimestamp = System.currentTimeMillis()
    }
    UsageTracker.getInstance().log(getEventBuilder(StudioRunEvent.newBuilder()
                                                     .setRunId(myRun.runId.toString())
                                                     .setRunType(myRun.runType)
                                                     .setSectionType(StudioRunEvent.SectionType.TOTAL)
                                                     .setEventType(StudioRunEvent.EventType.FINISH)
                                                     .setDurationMs(calcDuration(myRun.runFinishTimestamp, myRun.startTimestamp).toInt())
                                                     .setIsSuccessful(isSuccessful)
                                                     .build(), myRun.packageName))
  }

  private fun calcDuration(finishTime: Long?, startTime: Long?): Long {
    // use asserts to detect any incorrect assumptions in dev/canary builds. Asserts are disabled in prod so it'll just use 0 duration.
    assert(startTime != null)
    assert(finishTime != null)
    assert(finishTime!! > startTime!!)
    if (finishTime == null || startTime == null || finishTime < startTime) return 0
    return finishTime - startTime
  }

  private fun determineRunType(runType: String): StudioRunEvent.RunType {
    return when (runType) {
      "Run" -> StudioRunEvent.RunType.RUN
      "Debug" -> StudioRunEvent.RunType.DEBUG
      "Android Profiler" -> StudioRunEvent.RunType.PROFILE
      else -> StudioRunEvent.RunType.UNKNOWN
    }
  }

  private fun determineBuildMode(buildMode: BuildMode?): StudioRunEvent.BuildMode {
    if (buildMode == null) return StudioRunEvent.BuildMode.UNKNOWN_BUILD_MODE
    return StudioRunEvent.BuildMode.valueOf(buildMode.toString())
  }

  private fun getEventBuilder(studioRunEvent: StudioRunEvent, packageName: String): AndroidStudioEvent.Builder {
    return AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.STUDIO_RUN_EVENT)
      .setProjectId(AnonymizerUtil.anonymizeUtf8(packageName))
      .setRawProjectId(packageName)
      .setStudioRunEvent(studioRunEvent)
  }
}

