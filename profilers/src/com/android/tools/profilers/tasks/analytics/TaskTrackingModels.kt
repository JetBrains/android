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

/** Metadata required to track the lifecycle of a profiler task. */
data class TaskMetadata(
  val taskType: ProfilerTaskType,
  val taskId: Long,
  val taskDataOrigin: TaskDataOrigin,
  val taskAttachmentPoint: TaskAttachmentPoint,
  val exposureLevel: ExposureLevel,
  // Null if there is no custom task configuration for the respective task.
  val taskConfig: ProfilingConfiguration?,
)

/** Metadata for a task that failed to start. Only one of the properties will be non-null. */
data class TaskStartFailedMetadata
@JvmOverloads
constructor(
  val traceStartStatus: Trace.TraceStartStatus? = null,
  val allocationTrackStatus: TrackStatus? = null,
  val heapDumpStatus: Memory.HeapDumpStatus? = null,
  val leakCanaryStartStatus: LeakCanaryStartErrorCode? = null,
)

/** Metadata for a task that failed to stop. Only one of the properties will be non-null. */
data class TaskStopFailedMetadata
@JvmOverloads
constructor(
  val traceStopStatus: Trace.TraceStopStatus? = null,
  val allocationTrackStatus: TrackStatus? = null,
  val cpuCaptureMetadata: CpuCaptureMetadata? = null,
)

/** Metadata for a task that failed during processing. */
data class TaskProcessingFailedMetadata
@JvmOverloads
constructor(val cpuCaptureMetadata: CpuCaptureMetadata? = null, val leakCanaryProcessingStatus: LeakCanaryProcessingErrorCode? = null)

enum class LeakCanaryStartErrorCode {
  UNKNOWN_ERROR,
  AGENT_ATTACH_FAILED,
  APP_CONTEXT_NULL,
  LIBRARY_NOT_INSTALLED_TIMEOUT,
  TRANSPORT_TIMEOUT,
}

enum class LeakCanaryProcessingErrorCode {
  UNKNOWN_ERROR,
  BROADCAST_DELIVERY_FAILED,
  HEAP_DUMP_GENERATION_FAILED,
  HPROF_DOWNLOAD_FAILED,
  SHARK_ANALYSIS_OOM,
  SHARK_ANALYSIS_EXCEPTION,
  PARSING_FAILURE,
}

enum class LeakCanaryUiAction {
  UNKNOWN_ACTION,
  FORCE_DUMP_CLICKED,
  STOP_RECORDING_CLICKED,
  GO_TO_DECLARATION_CLICKED,
  COPY_TRACE_CLICKED,
  EXPAND_ALL_NODES_CLICKED,
  COLLAPSE_ALL_NODES_CLICKED,
  NEW_LEAK_SELECTED,
  CANCELLED_DURING_ANALYSIS,
  INSIGHT_PANEL_OPENED,
  INSIGHT_PANEL_CLOSED,
  INSIGHT_AUTO_GENERATE_ENABLED,
  INSIGHT_AUTO_GENERATE_DISABLED,
  INSIGHT_FETCH_TRIGGERED,
  INSIGHT_FETCH_SUCCEEDED,
  INSIGHT_FETCH_FAILED,
  INSIGHT_SENTIMENT_UP,
  INSIGHT_SENTIMENT_DOWN,
  INSIGHT_GENERATE_FIX_CLICKED,
  INSIGHT_COPY_CLICKED,
  INSIGHT_SENTIMENT_CLEARED,
  INSIGHT_FETCH_CANCELLED,
}

/** Metadata payload for a successfully parsed memory leak trace. */
data class LeakCanaryLeakAnalysis(
  val retainedObjectsCount: Int? = null,
  val occurrencesCount: Int? = null,
  val estimatedMemoryLeakedBytes: Long? = null,
  val leakingNoRows: Int? = null,
  val leakingMaybeRows: Int? = null,
  val leakingYesRows: Int? = null,
  val heapDumpAnalysisTimeMs: Long? = null,
  val totalRecordingTimeMs: Long? = null,
  val isLibraryLeak: Boolean? = null,
  val hprofFileSizeBytes: Long? = null,
  val hprofDownloadDurationMs: Long? = null,
)

enum class TaskDataOrigin {
  UNSPECIFIED,
  NEW,
  PAST_RECORDING,
  IMPORTED,
}

enum class TaskAttachmentPoint {
  UNSPECIFIED,
  NEW_PROCESS,
  EXISTING_PROCESS,
}

enum class TaskFinishedState {
  UNSPECIFIED,
  COMPLETED,
  USER_CANCELLED,
}
