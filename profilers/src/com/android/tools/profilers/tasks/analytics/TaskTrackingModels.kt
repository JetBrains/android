/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.profilers.tasks.analytics

import com.android.tools.profiler.proto.Common.Process.ExposureLevel
import com.android.tools.profiler.proto.Memory
import com.android.tools.profiler.proto.Memory.TrackStatus
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.cpu.CpuCaptureMetadata
import com.android.tools.profilers.cpu.config.ProfilingConfiguration
import com.android.tools.profilers.tasks.ProfilerTaskType

/**
 * Metadata required to track the lifecycle of a profiler task.
 */
data class TaskMetadata(
  val taskType: ProfilerTaskType,
  val taskId: Long,
  val taskDataOrigin: TaskDataOrigin,
  val taskAttachmentPoint: TaskAttachmentPoint,
  val exposureLevel: ExposureLevel,
  // Null if there is no custom task configuration for the respective task.
  val taskConfig: ProfilingConfiguration?
)

/**
 * Metadata for a task that failed to start.
 * Only one of the properties will be non-null.
 */
data class TaskStartFailedMetadata(
  val traceStartStatus: Trace.TraceStartStatus? = null,
  val allocationTrackStatus: TrackStatus? = null,
  val heapDumpStatus: Memory.HeapDumpStatus? = null
)

/**
 * Metadata for a task that failed to stop.
 * Only one of the properties will be non-null.
 */
data class TaskStopFailedMetadata(
  val traceStopStatus: Trace.TraceStopStatus? = null,
  val allocationTrackStatus: TrackStatus? = null,
  val cpuCaptureMetadata: CpuCaptureMetadata? = null
)

/**
 * Metadata for a task that failed during processing.
 */
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