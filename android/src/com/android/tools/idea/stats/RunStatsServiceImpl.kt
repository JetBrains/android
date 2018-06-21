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
                                isDebuggable: Boolean,
                                forceColdswap: Boolean,
                                instantRunEnabled: Boolean) {
    synchronized(lock) {
      myRun = Run(UUID.randomUUID(), packageName, determineRunType(runType), System.currentTimeMillis())
    }
    val currentRun = myRun?: return
    UsageTracker.log(getEventBuilder(StudioRunEvent.newBuilder()
                                                     .setRunId(currentRun.runId.toString())
                                                     .setRunType(currentRun.runType)
                                                     .setSectionType(StudioRunEvent.SectionType.TOTAL)
                                                     .setEventType(StudioRunEvent.EventType.START)
                                                     .setDebuggable(isDebuggable)
                                                     .build(), currentRun.packageName))
    UsageTracker.log(getEventBuilder(StudioRunEvent.newBuilder()
                                                     .setRunId(currentRun.runId.toString())
                                                     .setRunType(currentRun.runType)
                                                     .setSectionType(StudioRunEvent.SectionType.STUDIO)
                                                     .setEventType(StudioRunEvent.EventType.START)
                                                     .build(), currentRun.packageName))
  }

  override fun notifyStudioSectionFinished(isSuccessful: Boolean,
                                           isInstantRun: Boolean,
                                           userSelectedDeployTarget: Boolean) {
    val currentRun = myRun?: return
    synchronized(lock) {
      currentRun.studioProcessFinishedTimestamp = System.currentTimeMillis()
    }

    UsageTracker.log(getEventBuilder(StudioRunEvent.newBuilder()
                                                     .setRunId(currentRun.runId.toString())
                                                     .setRunType(currentRun.runType)
                                                     .setSectionType(StudioRunEvent.SectionType.STUDIO)
                                                     .setEventType(StudioRunEvent.EventType.FINISH)
                                                     .setDurationMs(
                                                       calcDuration(currentRun.studioProcessFinishedTimestamp,
                                                                    currentRun.startTimestamp).toInt())
                                                     .setInstantRun(isInstantRun)
                                                     .setIsSuccessful(isSuccessful)
                                                     .setUserSelectedTarget(userSelectedDeployTarget)
                                                     .build(), currentRun.packageName))
  }

  // TODO add gradle task, dynamic app info etc, target device
  override fun notifyGradleStarted(buildMode: BuildMode?) {
    val currentRun = myRun?: return
    synchronized(lock) {
      currentRun.gradleInvokeTimestamp = System.currentTimeMillis()
    }
    UsageTracker.log(getEventBuilder(StudioRunEvent.newBuilder()
                                                     .setRunId(currentRun.runId.toString())
                                                     .setRunType(currentRun.runType)
                                                     .setSectionType(StudioRunEvent.SectionType.GRADLE)
                                                     .setEventType(StudioRunEvent.EventType.START)
                                                     .setBuildMode(determineBuildMode(buildMode))
                                                     .build(), currentRun.packageName))
  }

  override fun notifyGradleFinished(isSuccessful: Boolean) {
    val currentRun = myRun?: return
    synchronized(lock) {
      currentRun.gradleFinishedTimestamp = System.currentTimeMillis()
    }
    UsageTracker.log(getEventBuilder(StudioRunEvent.newBuilder()
                                                     .setRunId(currentRun.runId.toString())
                                                     .setRunType(currentRun.runType)
                                                     .setSectionType(StudioRunEvent.SectionType.GRADLE)
                                                     .setEventType(StudioRunEvent.EventType.FINISH)
                                                     .setDurationMs(
                                                       calcDuration(currentRun.gradleFinishedTimestamp,
                                                                    currentRun.gradleInvokeTimestamp).toInt())
                                                     .setIsSuccessful(isSuccessful)
                                                     .build(), currentRun.packageName))
  }

  /**
   * Called before starting an AVD. Note this could be called outside of a run through the AVD manager on here and [notifyRunStarted] is a
   * could the [Run] instance be null. In that case we do not track the emulator duration as it is not part of a run.
   */
  override fun notifyEmulatorStarting() {
    val currentRun = myRun?: return
    synchronized(lock) {
      currentRun.emulatorStartTimestamp = System.currentTimeMillis()
    }
    UsageTracker.log(getEventBuilder(StudioRunEvent.newBuilder()
                                                     .setRunId(currentRun.runId.toString())
                                                     .setRunType(currentRun.runType)
                                                     .setSectionType(StudioRunEvent.SectionType.EMULATOR)
                                                     .setEventType(StudioRunEvent.EventType.START)
                                                     .build(), currentRun.packageName))
  }

  override fun notifyEmulatorStarted(isSuccessful: Boolean) {
    val currentRun = myRun?: return
    if (myRun?.emulatorStartTimestamp == null) return
    synchronized(lock) {
      currentRun.emulatorFinishTimestamp = System.currentTimeMillis()
    }
    UsageTracker.log(getEventBuilder(StudioRunEvent.newBuilder()
                                                     .setRunId(currentRun.runId.toString())
                                                     .setRunType(currentRun.runType)
                                                     .setSectionType(StudioRunEvent.SectionType.EMULATOR)
                                                     .setDurationMs(
                                                       calcDuration(currentRun.emulatorFinishTimestamp,
                                                                    currentRun.emulatorStartTimestamp).toInt())
                                                     .setIsSuccessful(isSuccessful)
                                                     .setEventType(StudioRunEvent.EventType.FINISH)
                                                     .build(), currentRun.packageName))
  }

  /**
   * Start tracking a deploy. Better to use one of the deploy task specific methods in [RunStatsService]
   * instead for shorter list of parameters.
   */
  override fun notifyDeployStarted(deployTask: StudioRunEvent.DeployTask,
                                   device: IDevice,
                                   artifactCount: Int,
                                   isPatchBuild: Boolean,
                                   dontKill: Boolean, disabledDynamicFeaturesCount: Int) {
    val currentRun = myRun?: return
    synchronized(lock) {
      currentRun.deployStartTimestamp = System.currentTimeMillis()
    }
    UsageTracker.log(getEventBuilder(StudioRunEvent.newBuilder()
                                                     .setRunId(currentRun.runId.toString())
                                                     .setRunType(currentRun.runType)
                                                     .setSectionType(StudioRunEvent.SectionType.DEPLOY)
                                                     .setEventType(StudioRunEvent.EventType.START)
                                                     .setArtifactCount(artifactCount)
                                                     .setDeployTask(deployTask)
                                                     .setDisabledDynamicFeaturesCount(disabledDynamicFeaturesCount)
                                                     .setPatchBuild(isPatchBuild)
                                                     .setDoNotRestart(dontKill)
                                                     .build(), currentRun.packageName)
                                     .setDeviceInfo(AndroidStudioUsageTracker.deviceToDeviceInfo(device)))
  }

  override fun notifyDeployFinished(isSuccessful: Boolean) {
    val currentRun = myRun?: return
    synchronized(lock) {
      currentRun.deployFinishTimestamp = System.currentTimeMillis()
    }
    UsageTracker.log(getEventBuilder(StudioRunEvent.newBuilder()
                                                     .setRunId(currentRun.runId.toString())
                                                     .setRunType(currentRun.runType)
                                                     .setSectionType(StudioRunEvent.SectionType.DEPLOY)
                                                     .setEventType(StudioRunEvent.EventType.FINISH)
                                                     .setDurationMs(
                                                       calcDuration(currentRun.deployFinishTimestamp,
                                                                    currentRun.deployStartTimestamp).toInt())
                                                     .setIsSuccessful(isSuccessful)
                                                     .build(), currentRun.packageName))
  }

  override fun notifyRunFinished(isSuccessful: Boolean) {
    val currentRun = myRun?: return
    synchronized(lock) {
      currentRun.runFinishTimestamp = System.currentTimeMillis()
    }
    UsageTracker.log(getEventBuilder(StudioRunEvent.newBuilder()
                                                     .setRunId(currentRun.runId.toString())
                                                     .setRunType(currentRun.runType)
                                                     .setSectionType(StudioRunEvent.SectionType.TOTAL)
                                                     .setEventType(StudioRunEvent.EventType.FINISH)
                                                     .setDurationMs(
                                                       calcDuration(currentRun.runFinishTimestamp, currentRun.startTimestamp).toInt())
                                                     .setIsSuccessful(isSuccessful)
                                                     .build(), currentRun.packageName))
    // clear current run instance when deploy is done.
    synchronized(lock) {
      myRun = null
    }
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

