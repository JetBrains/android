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

import com.android.tools.profiler.proto.Memory
import com.android.tools.profiler.proto.Memory.TrackStatus
import com.android.tools.profiler.proto.Trace
import com.google.wireless.android.sdk.stats.LeakCanaryTaskMetadata.LeakAnalysis
import com.google.wireless.android.sdk.stats.LeakCanaryTaskMetadata.UiAction
import com.google.wireless.android.sdk.stats.TaskFailedMetadata.AllocationTrackStatus
import com.google.wireless.android.sdk.stats.TaskFailedMetadata.HeapDumpStatus
import com.google.wireless.android.sdk.stats.TaskFailedMetadata.LeakCanaryProcessingStatus
import com.google.wireless.android.sdk.stats.TaskFailedMetadata.LeakCanaryStartStatus
import com.google.wireless.android.sdk.stats.TaskFailedMetadata.TraceStartStatus
import com.google.wireless.android.sdk.stats.TaskFailedMetadata.TraceStopStatus

// Extension functions to convert Transport Pipeline Proto statuses to Studio Stats Proto statuses
fun TrackStatus.toStatsProto(): AllocationTrackStatus {
  val statsProtoStatus =
    when (this.status) {
      TrackStatus.Status.SUCCESS -> AllocationTrackStatus.Status.SUCCESS
      TrackStatus.Status.UNSPECIFIED -> AllocationTrackStatus.Status.STATUS_UNSPECIFIED
      TrackStatus.Status.IN_PROGRESS -> AllocationTrackStatus.Status.IN_PROGRESS
      TrackStatus.Status.NOT_ENABLED -> AllocationTrackStatus.Status.NOT_ENABLED
      TrackStatus.Status.NOT_PROFILING -> AllocationTrackStatus.Status.NOT_PROFILING
      TrackStatus.Status.FAILURE_UNKNOWN -> AllocationTrackStatus.Status.FAILURE_UNKNOWN
      TrackStatus.Status.UNRECOGNIZED -> AllocationTrackStatus.Status.UNRECOGNIZED
      TrackStatus.Status.AGENT_UNATTACHABLE -> AllocationTrackStatus.Status.AGENT_UN_ATTACHABLE
      else -> AllocationTrackStatus.Status.UNRECOGNIZED
    }
  return AllocationTrackStatus.newBuilder().setStartTimeNs(this.startTime).setStatus(statsProtoStatus).build()
}

fun Trace.TraceStopStatus.toStatsProto(): TraceStopStatus {
  val statsProtoStatus =
    when (this.status) {
      Trace.TraceStopStatus.Status.UNSPECIFIED -> TraceStopStatus.Status.STATUS_UNSPECIFIED
      Trace.TraceStopStatus.Status.SUCCESS -> TraceStopStatus.Status.SUCCESS
      Trace.TraceStopStatus.Status.NO_ONGOING_PROFILING -> TraceStopStatus.Status.NO_ONGOING_PROFILING
      Trace.TraceStopStatus.Status.APP_PROCESS_DIED -> TraceStopStatus.Status.APP_PROCESS_DIED
      Trace.TraceStopStatus.Status.APP_PID_CHANGED -> TraceStopStatus.Status.APP_PID_CHANGED
      Trace.TraceStopStatus.Status.PROFILER_PROCESS_DIED -> TraceStopStatus.Status.PROFILER_PROCESS_DIED
      Trace.TraceStopStatus.Status.STOP_COMMAND_FAILED -> TraceStopStatus.Status.STOP_COMMAND_FAILED
      Trace.TraceStopStatus.Status.STILL_PROFILING_AFTER_STOP -> TraceStopStatus.Status.STILL_PROFILING_AFTER_STOP
      Trace.TraceStopStatus.Status.CANNOT_START_WAITING -> TraceStopStatus.Status.CANNOT_START_WAITING
      Trace.TraceStopStatus.Status.WAIT_TIMEOUT -> TraceStopStatus.Status.WAIT_TIMEOUT
      Trace.TraceStopStatus.Status.WAIT_FAILED -> TraceStopStatus.Status.WAIT_FAILED
      Trace.TraceStopStatus.Status.CANNOT_READ_WAIT_EVENT -> TraceStopStatus.Status.CANNOT_READ_WAIT_EVENT
      Trace.TraceStopStatus.Status.CANNOT_COPY_FILE -> TraceStopStatus.Status.CANNOT_COPY_FILE
      Trace.TraceStopStatus.Status.CANNOT_FORM_FILE -> TraceStopStatus.Status.CANNOT_FORM_FILE
      Trace.TraceStopStatus.Status.CANNOT_READ_FILE -> TraceStopStatus.Status.CANNOT_READ_FILE
      Trace.TraceStopStatus.Status.OTHER_FAILURE -> TraceStopStatus.Status.OTHER_FAILURE
      Trace.TraceStopStatus.Status.UNRECOGNIZED -> TraceStopStatus.Status.UNRECOGNIZED
      else -> TraceStopStatus.Status.UNRECOGNIZED
    }

  return TraceStopStatus.newBuilder()
    .setStatus(statsProtoStatus)
    .apply { if (this@toStatsProto.errorCode != 0L) errorCode = this@toStatsProto.errorCode }
    .setStoppingDurationNs(this.stoppingDurationNs)
    .build()
}

fun Trace.TraceStartStatus.toStatsProto(): TraceStartStatus {
  val statsProtoStatus =
    when (this.status) {
      Trace.TraceStartStatus.Status.SUCCESS -> TraceStartStatus.Status.SUCCESS
      Trace.TraceStartStatus.Status.UNSPECIFIED -> TraceStartStatus.Status.STATUS_UNSPECIFIED
      Trace.TraceStartStatus.Status.FAILURE -> TraceStartStatus.Status.FAILURE
      Trace.TraceStartStatus.Status.UNRECOGNIZED -> TraceStartStatus.Status.UNRECOGNIZED
      else -> TraceStartStatus.Status.UNRECOGNIZED
    }

  return TraceStartStatus.newBuilder()
    .setStatus(statsProtoStatus)
    .apply { if (this@toStatsProto.errorCode != 0L) errorCode = this@toStatsProto.errorCode }
    .setStartTimeNs(this.startTimeNs)
    .build()
}

fun Memory.HeapDumpStatus.toStatsProto(): HeapDumpStatus {
  val statsProtoStatus =
    when (this.status) {
      Memory.HeapDumpStatus.Status.UNSPECIFIED -> HeapDumpStatus.Status.STATUS_UNSPECIFIED
      Memory.HeapDumpStatus.Status.SUCCESS -> HeapDumpStatus.Status.SUCCESS
      Memory.HeapDumpStatus.Status.IN_PROGRESS -> HeapDumpStatus.Status.IN_PROGRESS
      Memory.HeapDumpStatus.Status.NOT_PROFILING -> HeapDumpStatus.Status.NOT_PROFILING
      Memory.HeapDumpStatus.Status.FAILURE_UNKNOWN -> HeapDumpStatus.Status.FAILURE_UNKNOWN
      Memory.HeapDumpStatus.Status.UNRECOGNIZED -> HeapDumpStatus.Status.UNRECOGNIZED
      else -> HeapDumpStatus.Status.UNRECOGNIZED
    }

  return HeapDumpStatus.newBuilder().setStatus(statsProtoStatus).setStartTimeNs(this.startTime).build()
}

fun LeakCanaryStartErrorCode.toStatsProto(): LeakCanaryStartStatus {
  val statsProtoStatus =
    when (this) {
      LeakCanaryStartErrorCode.UNKNOWN_ERROR -> LeakCanaryStartStatus.ErrorCode.UNKNOWN_ERROR
      LeakCanaryStartErrorCode.AGENT_ATTACH_FAILED -> LeakCanaryStartStatus.ErrorCode.AGENT_ATTACH_FAILED
      LeakCanaryStartErrorCode.APP_CONTEXT_NULL -> LeakCanaryStartStatus.ErrorCode.APP_CONTEXT_NULL
      LeakCanaryStartErrorCode.LIBRARY_NOT_INSTALLED_TIMEOUT -> LeakCanaryStartStatus.ErrorCode.LIBRARY_NOT_INSTALLED_TIMEOUT
      LeakCanaryStartErrorCode.TRANSPORT_TIMEOUT -> LeakCanaryStartStatus.ErrorCode.TRANSPORT_TIMEOUT
    }
  return LeakCanaryStartStatus.newBuilder().setErrorCode(statsProtoStatus).build()
}

fun LeakCanaryProcessingErrorCode.toStatsProto(): LeakCanaryProcessingStatus {
  val statsProtoStatus =
    when (this) {
      LeakCanaryProcessingErrorCode.UNKNOWN_ERROR -> LeakCanaryProcessingStatus.ErrorCode.UNKNOWN_ERROR
      LeakCanaryProcessingErrorCode.BROADCAST_DELIVERY_FAILED -> LeakCanaryProcessingStatus.ErrorCode.BROADCAST_DELIVERY_FAILED
      LeakCanaryProcessingErrorCode.HEAP_DUMP_GENERATION_FAILED -> LeakCanaryProcessingStatus.ErrorCode.HEAP_DUMP_GENERATION_FAILED
      LeakCanaryProcessingErrorCode.HPROF_DOWNLOAD_FAILED -> LeakCanaryProcessingStatus.ErrorCode.HPROF_DOWNLOAD_FAILED
      LeakCanaryProcessingErrorCode.SHARK_ANALYSIS_OOM -> LeakCanaryProcessingStatus.ErrorCode.SHARK_ANALYSIS_OOM
      LeakCanaryProcessingErrorCode.SHARK_ANALYSIS_EXCEPTION -> LeakCanaryProcessingStatus.ErrorCode.SHARK_ANALYSIS_EXCEPTION
      LeakCanaryProcessingErrorCode.PARSING_FAILURE -> LeakCanaryProcessingStatus.ErrorCode.PARSING_FAILURE
    }
  return LeakCanaryProcessingStatus.newBuilder().setErrorCode(statsProtoStatus).build()
}

fun LeakCanaryUiAction.toStatsProto(): UiAction {
  return when (this) {
    LeakCanaryUiAction.UNKNOWN_ACTION -> UiAction.UNKNOWN_ACTION
    LeakCanaryUiAction.FORCE_DUMP_CLICKED -> UiAction.FORCE_DUMP_CLICKED
    LeakCanaryUiAction.STOP_RECORDING_CLICKED -> UiAction.STOP_RECORDING_CLICKED
    LeakCanaryUiAction.GO_TO_DECLARATION_CLICKED -> UiAction.GO_TO_DECLARATION_CLICKED
    LeakCanaryUiAction.COPY_TRACE_CLICKED -> UiAction.COPY_TRACE_CLICKED
    LeakCanaryUiAction.EXPAND_ALL_NODES_CLICKED -> UiAction.EXPAND_ALL_NODES_CLICKED
    LeakCanaryUiAction.COLLAPSE_ALL_NODES_CLICKED -> UiAction.COLLAPSE_ALL_NODES_CLICKED
    LeakCanaryUiAction.NEW_LEAK_SELECTED -> UiAction.NEW_LEAK_SELECTED
    LeakCanaryUiAction.CANCELLED_DURING_ANALYSIS -> UiAction.CANCELLED_DURING_ANALYSIS
    LeakCanaryUiAction.INSIGHT_PANEL_OPENED -> UiAction.INSIGHT_PANEL_OPENED
    LeakCanaryUiAction.INSIGHT_PANEL_CLOSED -> UiAction.INSIGHT_PANEL_CLOSED
    LeakCanaryUiAction.INSIGHT_AUTO_GENERATE_ENABLED -> UiAction.INSIGHT_AUTO_GENERATE_ENABLED
    LeakCanaryUiAction.INSIGHT_AUTO_GENERATE_DISABLED -> UiAction.INSIGHT_AUTO_GENERATE_DISABLED
    LeakCanaryUiAction.INSIGHT_FETCH_TRIGGERED -> UiAction.INSIGHT_FETCH_TRIGGERED
    LeakCanaryUiAction.INSIGHT_FETCH_SUCCEEDED -> UiAction.INSIGHT_FETCH_SUCCEEDED
    LeakCanaryUiAction.INSIGHT_FETCH_FAILED -> UiAction.INSIGHT_FETCH_FAILED
    LeakCanaryUiAction.INSIGHT_SENTIMENT_UP -> UiAction.INSIGHT_SENTIMENT_UP
    LeakCanaryUiAction.INSIGHT_SENTIMENT_DOWN -> UiAction.INSIGHT_SENTIMENT_DOWN
    LeakCanaryUiAction.INSIGHT_GENERATE_FIX_CLICKED -> UiAction.INSIGHT_GENERATE_FIX_CLICKED
    LeakCanaryUiAction.INSIGHT_COPY_CLICKED -> UiAction.INSIGHT_COPY_CLICKED
    LeakCanaryUiAction.INSIGHT_SENTIMENT_CLEARED -> UiAction.INSIGHT_SENTIMENT_CLEARED
    LeakCanaryUiAction.INSIGHT_FETCH_CANCELLED -> UiAction.INSIGHT_FETCH_CANCELLED
  }
}

fun LeakCanaryLeakAnalysis.toStatsProto(): LeakAnalysis {
  return LeakAnalysis.newBuilder()
    .apply {
      this@toStatsProto.retainedObjectsCount?.let { setRetainedObjectsCount(it) }
      this@toStatsProto.occurrencesCount?.let { setOccurrencesCount(it) }
      this@toStatsProto.estimatedMemoryLeakedBytes?.let { setEstimatedMemoryLeakedBytes(it) }
      this@toStatsProto.leakingNoRows?.let { setLeakingNoRows(it) }
      this@toStatsProto.leakingMaybeRows?.let { setLeakingMaybeRows(it) }
      this@toStatsProto.leakingYesRows?.let { setLeakingYesRows(it) }
      this@toStatsProto.heapDumpAnalysisTimeMs?.let { setHeapDumpAnalysisTimeMs(it) }
      this@toStatsProto.totalRecordingTimeMs?.let { setTotalRecordingTimeMs(it) }
      this@toStatsProto.isLibraryLeak?.let { setIsLibraryLeak(it) }
      this@toStatsProto.hprofFileSizeBytes?.let { setHprofFileSizeBytes(it) }
      this@toStatsProto.hprofDownloadDurationMs?.let { setHprofDownloadDurationMs(it) }
    }
    .build()
}
