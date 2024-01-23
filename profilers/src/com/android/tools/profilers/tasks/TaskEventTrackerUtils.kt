/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.profilers.tasks

import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.Process.ExposureLevel
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.config.CpuProfilerConfigModel
import com.android.tools.profilers.cpu.config.ProfilingConfiguration
import com.android.tools.profilers.cpu.config.UnspecifiedConfiguration

object TaskEventTrackerUtils {

  private fun getCustomTaskConfigs(profilers: StudioProfilers): List<ProfilingConfiguration> {
    val model = CpuProfilerConfigModel(profilers, CpuProfilerStage(profilers))
    // The CpuProfilingConfigModel requires a default profiling configuration to be set.
    model.setProfilingConfiguration(UnspecifiedConfiguration(""))
    val taskConfigs = model.taskProfilingConfigurations
    return taskConfigs
  }

  private fun buildTaskMetadata(profilers: StudioProfilers): TaskMetadata {
    return buildTaskMetadata(profilers, profilers.sessionsManager.isSessionAlive)
  }

  private fun buildTaskMetadata(profilers: StudioProfilers, isNewlyRecordedTask: Boolean): TaskMetadata {
    val sessionsManager = profilers.sessionsManager

    val taskType = sessionsManager.currentTaskType
    val taskId = sessionsManager.selectedSession.sessionId

    val taskDataOrigin = if (isNewlyRecordedTask) {
      TaskDataOrigin.NEW
    }
    else if (sessionsManager.selectedSessionMetaData.type == Common.SessionMetaData.SessionType.FULL) {
      TaskDataOrigin.PAST_RECORDING
    }
    else if (sessionsManager.selectedSessionMetaData.type == Common.SessionMetaData.SessionType.MEMORY_CAPTURE
             || sessionsManager.selectedSessionMetaData.type == Common.SessionMetaData.SessionType.CPU_CAPTURE) {
      TaskDataOrigin.IMPORTED
    }
    else {
      TaskDataOrigin.UNSPECIFIED
    }

    val taskAttachmentPoint = if (!isNewlyRecordedTask) {
      TaskAttachmentPoint.UNSPECIFIED
    }
    else if (sessionsManager.isCurrentTaskStartup) {
      TaskAttachmentPoint.NEW_PROCESS
    }
    else {
      TaskAttachmentPoint.EXISTING_PROCESS
    }

    val exposureLevel = if (isNewlyRecordedTask && profilers.process != null) {
      profilers.process!!.exposureLevel
    }
    else {
      ExposureLevel.UNKNOWN
    }

    val taskConfigs = getCustomTaskConfigs(profilers)

    return TaskMetadata(taskType, taskId, taskDataOrigin, taskAttachmentPoint, exposureLevel, taskConfigs)
  }

  @JvmStatic
  fun trackTaskEntered(profilers: StudioProfilers) {
    profilers.ideServices.featureTracker.trackTaskEntered(buildTaskMetadata(profilers))
  }

  /**
   * Overload of trackTaskEnter to take in whether the session is alive or not. This is useful for when trackTaskFinished is called in
   * a future callback. The value of isSessionsAlive at the time of callback registration is what is wanted, not by the time it is called.
   * As, by the time the callback is called, SessionsManager.isSessionAlive might return a different value.
   */
  @JvmStatic
  fun trackTaskFinished(profilers: StudioProfilers, isNewlyRecordedTask: Boolean, taskFinishedState: TaskFinishedState) {
    val taskMetadata = buildTaskMetadata(profilers, isNewlyRecordedTask)
    profilers.ideServices.featureTracker.trackTaskFinished(taskMetadata, taskFinishedState)
  }
}

data class TaskMetadata(
  val taskType: ProfilerTaskType,
  val taskId: Long,
  val taskDataOrigin: TaskDataOrigin,
  val taskAttachmentPoint: TaskAttachmentPoint,
  val exposureLevel: ExposureLevel,
  val taskConfigs: List<ProfilingConfiguration>
)

enum class TaskDataOrigin {
  UNSPECIFIED,
  NEW,
  PAST_RECORDING,
  IMPORTED
}

enum class TaskAttachmentPoint {
  UNSPECIFIED,
  NEW_PROCESS,
  EXISTING_PROCESS
}

enum class TaskFinishedState {
  UNSPECIFIED,
  COMPLETED,
  USER_CANCELLED
}
