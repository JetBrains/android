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
import com.google.wireless.android.sdk.stats.TaskFailedMetadata.AllocationTrackStatus
import com.google.wireless.android.sdk.stats.TaskFailedMetadata.HeapDumpStatus
import com.google.wireless.android.sdk.stats.TaskFailedMetadata.TraceStartStatus
import com.google.wireless.android.sdk.stats.TaskFailedMetadata.TraceStopStatus

// Extension functions to convert Transport Pipeline Proto statuses to Studio Stats Proto statuses
fun TrackStatus.toStatsProto(): AllocationTrackStatus {
  val statsProtoStatus = when (this.status) {
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
  return AllocationTrackStatus.newBuilder()
    .setStartTimeNs(this.startTime)
    .setStatus(statsProtoStatus)
    .build()
}

fun Trace.TraceStopStatus.toStatsProto(): TraceStopStatus {
  val statsProtoStatus = when (this.status) {
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
  val statsProtoStatus = when (this.status) {
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
  val statsProtoStatus = when (this.status) {
    Memory.HeapDumpStatus.Status.UNSPECIFIED -> HeapDumpStatus.Status.STATUS_UNSPECIFIED
    Memory.HeapDumpStatus.Status.SUCCESS -> HeapDumpStatus.Status.SUCCESS
    Memory.HeapDumpStatus.Status.IN_PROGRESS -> HeapDumpStatus.Status.IN_PROGRESS
    Memory.HeapDumpStatus.Status.NOT_PROFILING -> HeapDumpStatus.Status.NOT_PROFILING
    Memory.HeapDumpStatus.Status.FAILURE_UNKNOWN -> HeapDumpStatus.Status.FAILURE_UNKNOWN
    Memory.HeapDumpStatus.Status.UNRECOGNIZED -> HeapDumpStatus.Status.UNRECOGNIZED
    else -> HeapDumpStatus.Status.UNRECOGNIZED
  }

  return HeapDumpStatus.newBuilder()
    .setStatus(statsProtoStatus)
    .setStartTimeNs(this.startTime)
    .build()
}