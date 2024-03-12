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
import com.android.tools.profiler.proto.Memory
import com.android.tools.profiler.proto.Memory.TrackStatus
import com.android.tools.profiler.proto.Trace
import com.android.tools.profiler.proto.Trace.TraceStopStatus.Status.*
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.CpuCaptureMetadata
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.config.ArtInstrumentedConfiguration
import com.android.tools.profilers.cpu.config.ArtSampledConfiguration
import com.android.tools.profilers.cpu.config.CpuProfilerConfigModel
import com.android.tools.profilers.cpu.config.PerfettoNativeAllocationsConfiguration
import com.android.tools.profilers.cpu.config.ProfilingConfiguration
import com.android.tools.profilers.cpu.config.SimpleperfConfiguration
import com.android.tools.profilers.cpu.config.UnspecifiedConfiguration
import com.google.wireless.android.sdk.stats.TaskFailedMetadata
import com.google.wireless.android.sdk.stats.TaskFailedMetadata.TraceStopStatus
import com.android.tools.profilers.taskbased.home.TaskHomeTabModel

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
    val taskConfig: ProfilingConfiguration? =
      // Only include the task configuration if the task was newly created/recorded.
      if (taskDataOrigin != TaskDataOrigin.NEW) {
        null
      }
      else {
        when (taskType) {
          ProfilerTaskType.CALLSTACK_SAMPLE -> {
            taskConfigs.find { it is SimpleperfConfiguration }
          }

          ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING -> {
            when (profilers.taskHomeTabModel.persistentStateOnTaskEnter.recordingType) {
              TaskHomeTabModel.TaskRecordingType.SAMPLED -> taskConfigs.find { it is ArtSampledConfiguration }
              TaskHomeTabModel.TaskRecordingType.INSTRUMENTED -> taskConfigs.find { it is ArtInstrumentedConfiguration }
              else -> null
            }
          }

          ProfilerTaskType.NATIVE_ALLOCATIONS -> {
            taskConfigs.find { it is PerfettoNativeAllocationsConfiguration }
          }

          else -> null
        }
      }

    return TaskMetadata(taskType, taskId, taskDataOrigin, taskAttachmentPoint, exposureLevel, taskConfig)
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

  /**
   * Overload of trackTaskEnter to take in whether the session is alive or not. This is useful for when trackTaskFailed is called in
   * a future callback. The value of isSessionsAlive at the time of callback registration is what is wanted, not by the time it is called.
   * As, by the time the callback is called, SessionsManager.isSessionAlive might return a different value.
   */
  @JvmStatic
  fun trackStartTaskFailed(profilers: StudioProfilers, isNewlyRecordedTask: Boolean,
                           taskStartFailedMetadata: TaskStartFailedMetadata) {
    val taskMetadata = buildTaskMetadata(profilers, isNewlyRecordedTask)
    profilers.ideServices.featureTracker.trackTaskFailed(taskMetadata, taskStartFailedMetadata)
  }

  @JvmStatic
  fun trackStopTaskFailed(profilers: StudioProfilers, isNewlyRecordedTask: Boolean,
                           taskStopFailedMetadata: TaskStopFailedMetadata) {
    val taskMetadata = buildTaskMetadata(profilers, isNewlyRecordedTask)
    profilers.ideServices.featureTracker.trackTaskFailed(taskMetadata, taskStopFailedMetadata)
  }

  @JvmStatic
  fun trackProcessingTaskFailed(profilers: StudioProfilers, isNewlyRecordedTask: Boolean,
                                taskProcessingFailedMetadata: TaskProcessingFailedMetadata) {
    val taskMetadata = buildTaskMetadata(profilers, isNewlyRecordedTask)
    profilers.ideServices.featureTracker.trackTaskFailed(taskMetadata, taskProcessingFailedMetadata)
  }
}

class TaskMetadataStatus {
  companion object {
    fun getTaskFailedAllocationTrackStatus(status: TrackStatus): TaskFailedMetadata.AllocationTrackStatus {
      val trackStatus: TaskFailedMetadata.AllocationTrackStatus.Status = when (status.status) {
        TrackStatus.Status.SUCCESS -> TaskFailedMetadata.AllocationTrackStatus.Status.SUCCESS
        TrackStatus.Status.UNSPECIFIED -> TaskFailedMetadata.AllocationTrackStatus.Status.STATUS_UNSPECIFIED
        TrackStatus.Status.IN_PROGRESS -> TaskFailedMetadata.AllocationTrackStatus.Status.IN_PROGRESS
        TrackStatus.Status.NOT_ENABLED -> TaskFailedMetadata.AllocationTrackStatus.Status.NOT_ENABLED
        TrackStatus.Status.NOT_PROFILING -> TaskFailedMetadata.AllocationTrackStatus.Status.NOT_PROFILING
        TrackStatus.Status.FAILURE_UNKNOWN -> TaskFailedMetadata.AllocationTrackStatus.Status.FAILURE_UNKNOWN
        TrackStatus.Status.UNRECOGNIZED -> TaskFailedMetadata.AllocationTrackStatus.Status.UNRECOGNIZED
        TrackStatus.Status.AGENT_UNATTACHABLE -> TaskFailedMetadata.AllocationTrackStatus.Status.AGENT_UN_ATTACHABLE
      }
      return TaskFailedMetadata.AllocationTrackStatus.newBuilder()
        .setStartTimeNs(status.startTime).setStatus(trackStatus).build()
    }

    fun getTaskFailedTraceStopStatus(status: Trace.TraceStopStatus): TraceStopStatus {
      val trackStatus = when (status.status) {
        UNSPECIFIED -> TraceStopStatus.Status.STATUS_UNSPECIFIED
        SUCCESS -> TraceStopStatus.Status.SUCCESS
        NO_ONGOING_PROFILING -> TraceStopStatus.Status.NO_ONGOING_PROFILING
        APP_PROCESS_DIED -> TraceStopStatus.Status.APP_PROCESS_DIED
        APP_PID_CHANGED -> TraceStopStatus.Status.APP_PID_CHANGED
        PROFILER_PROCESS_DIED -> TraceStopStatus.Status.PROFILER_PROCESS_DIED
        STOP_COMMAND_FAILED -> TraceStopStatus.Status.STOP_COMMAND_FAILED
        STILL_PROFILING_AFTER_STOP -> TraceStopStatus.Status.STILL_PROFILING_AFTER_STOP
        CANNOT_START_WAITING -> TraceStopStatus.Status.CANNOT_START_WAITING
        WAIT_TIMEOUT -> TraceStopStatus.Status.WAIT_TIMEOUT
        WAIT_FAILED -> TraceStopStatus.Status.WAIT_FAILED
        CANNOT_READ_WAIT_EVENT -> TraceStopStatus.Status.CANNOT_READ_WAIT_EVENT
        CANNOT_COPY_FILE -> TraceStopStatus.Status.CANNOT_COPY_FILE
        CANNOT_FORM_FILE -> TraceStopStatus.Status.CANNOT_FORM_FILE
        CANNOT_READ_FILE -> TraceStopStatus.Status.CANNOT_READ_FILE
        OTHER_FAILURE -> TraceStopStatus.Status.OTHER_FAILURE
        UNRECOGNIZED -> TraceStopStatus.Status.UNRECOGNIZED
      }
      return TraceStopStatus.newBuilder().setStatus(trackStatus)
        .apply { if (status.errorCode != 0L) errorCode = status.errorCode }
        .setStoppingDurationNs(status.stoppingDurationNs).build()
    }

    fun getTaskFailedTraceStartStatus(status: Trace.TraceStartStatus): TaskFailedMetadata.TraceStartStatus {
      val trackStatus = when (status.status) {
        Trace.TraceStartStatus.Status.SUCCESS -> TaskFailedMetadata.TraceStartStatus.Status.SUCCESS
        Trace.TraceStartStatus.Status.UNSPECIFIED -> TaskFailedMetadata.TraceStartStatus.Status.STATUS_UNSPECIFIED
        Trace.TraceStartStatus.Status.FAILURE -> TaskFailedMetadata.TraceStartStatus.Status.FAILURE
        Trace.TraceStartStatus.Status.UNRECOGNIZED -> TaskFailedMetadata.TraceStartStatus.Status.UNRECOGNIZED
      }
      return TaskFailedMetadata.TraceStartStatus.newBuilder().setStatus(trackStatus)
        .apply { if (status.errorCode != 0L) errorCode = status.errorCode }
        .setStartTimeNs(status.startTimeNs).build()
    }

    fun getTaskFailedHeapDumpStatus(status: Memory.HeapDumpStatus): TaskFailedMetadata.HeapDumpStatus {
      val trackStatus = when (status.status) {
        Memory.HeapDumpStatus.Status.UNSPECIFIED -> TaskFailedMetadata.HeapDumpStatus.Status.STATUS_UNSPECIFIED
        Memory.HeapDumpStatus.Status.SUCCESS -> TaskFailedMetadata.HeapDumpStatus.Status.SUCCESS
        Memory.HeapDumpStatus.Status.IN_PROGRESS -> TaskFailedMetadata.HeapDumpStatus.Status.IN_PROGRESS
        Memory.HeapDumpStatus.Status.NOT_PROFILING -> TaskFailedMetadata.HeapDumpStatus.Status.NOT_PROFILING
        Memory.HeapDumpStatus.Status.FAILURE_UNKNOWN -> TaskFailedMetadata.HeapDumpStatus.Status.FAILURE_UNKNOWN
        Memory.HeapDumpStatus.Status.UNRECOGNIZED -> TaskFailedMetadata.HeapDumpStatus.Status.UNRECOGNIZED
      }
      return TaskFailedMetadata.HeapDumpStatus.newBuilder().setStatus(trackStatus)
        .setStartTimeNs(status.startTime).build()
    }
  }
}

data class TaskMetadata(
  val taskType: ProfilerTaskType,
  val taskId: Long,
  val taskDataOrigin: TaskDataOrigin,
  val taskAttachmentPoint: TaskAttachmentPoint,
  val exposureLevel: ExposureLevel,
  // Set to null if there is no custom task configuration for the respective task.
  val taskConfig: ProfilingConfiguration?
)

data class TaskStartFailedMetadata(
  // Only one of the below 3 fields can be not null
  val traceStartStatus: Trace.TraceStartStatus?,
  val allocationTrackStatus: TrackStatus?,
  val heapDumpStatus: Memory.HeapDumpStatus?
)

data class TaskStopFailedMetadata(
  // Only one of the below 3 fields can be not null
  val traceStopStatus: Trace.TraceStopStatus?,
  val allocationTrackStatus: TrackStatus?,
  val cpuCaptureMetadata: CpuCaptureMetadata?
)

data class TaskProcessingFailedMetadata(
  val cpuCaptureMetadata: CpuCaptureMetadata?
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
