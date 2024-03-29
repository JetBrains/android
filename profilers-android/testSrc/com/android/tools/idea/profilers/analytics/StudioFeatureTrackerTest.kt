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
package com.android.tools.idea.profilers.analytics

import com.android.tools.profiler.proto.Memory
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.cpu.CpuCaptureMetadata
import com.android.tools.profilers.cpu.CpuCaptureParser
import com.android.tools.profilers.cpu.config.PerfettoSystemTraceConfiguration
import com.android.tools.profilers.tasks.TaskMetadataStatus
import com.android.tools.profilers.tasks.TaskProcessingFailedMetadata
import com.android.tools.profilers.tasks.TaskStartFailedMetadata
import com.android.tools.profilers.tasks.TaskStopFailedMetadata
import com.intellij.mock.MockProjectEx
import com.intellij.openapi.project.Project
import com.intellij.testFramework.DisposableRule
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class StudioFeatureTrackerTest {

  @get:Rule
  val disposableRule = DisposableRule()

  lateinit var project: Project
  private lateinit var studioFeatureTracker: StudioFeatureTracker

  @Before
  fun setup() {
    project = Mockito.spy(MockProjectEx(disposableRule.disposable))
    studioFeatureTracker = StudioFeatureTracker(project)
  }

  @Test
  fun testBuildStatsTaskStopFailedMetadata() {
    // Build CPU capture metadata
    val cpuCaptureMetadata = CpuCaptureMetadata(PerfettoSystemTraceConfiguration("Testing", false))
    val metadataWithCpuCapture = TaskStopFailedMetadata(
      traceStopStatus = null,
      allocationTrackStatus = null,
      cpuCaptureMetadata = cpuCaptureMetadata
    )
    val resultWithCpuCapture = studioFeatureTracker.buildStatsTaskStopFailedMetadata(metadataWithCpuCapture)
    assertEquals(resultWithCpuCapture.cpuCaptureMetadata, CpuCaptureParser.getCpuCaptureMetadata(cpuCaptureMetadata))

    // Build trace stop status
    val traceStopStatus = Trace.TraceStopStatus.newBuilder()
      .setStatus(Trace.TraceStopStatus.Status.STOP_COMMAND_FAILED)
      .setStoppingDurationNs(1000)
      .setErrorCode(123)
      .build()
    val metadataWithTraceStopStatus = TaskStopFailedMetadata(
      traceStopStatus = traceStopStatus,
      allocationTrackStatus = null,
      cpuCaptureMetadata = null
    )
    val resultWithTraceStopStatus = studioFeatureTracker.buildStatsTaskStopFailedMetadata(metadataWithTraceStopStatus)
    assertEquals(resultWithTraceStopStatus.traceStopStatus,
                 TaskMetadataStatus.getTaskFailedTraceStopStatus(traceStopStatus))

    // Build allocation track status
    val allocationTrackStatus = buildAllocationTrackStatus()
    val metadataWithAllocationTrackStatus = TaskStopFailedMetadata(
      traceStopStatus = null,
      allocationTrackStatus = allocationTrackStatus,
      cpuCaptureMetadata = null
    )
    val resultWithAllocationTrackStatus = studioFeatureTracker.buildStatsTaskStopFailedMetadata(metadataWithAllocationTrackStatus)
    assertEquals(resultWithAllocationTrackStatus.trackStatus,
                 TaskMetadataStatus.getTaskFailedAllocationTrackStatus(allocationTrackStatus))
  }

  @Test
  fun testBuildStatsTaskStartFailedMetadata() {
    // Build heapDump metadata
    val heapDumpMetadata = Memory.HeapDumpStatus.newBuilder()
      .setStartTime(10002)
      .setStatus(Memory.HeapDumpStatus.Status.FAILURE_UNKNOWN)
      .build()
    val heapDumpFailedMetadata = TaskStartFailedMetadata(
      traceStartStatus = null,
      allocationTrackStatus = null,
      heapDumpStatus = heapDumpMetadata
    )
    val resultWithCpuCapture = studioFeatureTracker.buildStatsTaskStartFailedMetadata(heapDumpFailedMetadata)
    assertEquals(resultWithCpuCapture.heapDumpStartStatus, TaskMetadataStatus.getTaskFailedHeapDumpStatus(heapDumpMetadata))

    // Build trace start status
    val traceStartStatus = Trace.TraceStartStatus.newBuilder()
      .setStatus(Trace.TraceStartStatus.Status.FAILURE)
      .setStartTimeNs(1234)
      .setErrorCode(455)
      .build()
    val metadataWithTraceStartStatus = TaskStartFailedMetadata(
      traceStartStatus = traceStartStatus,
      allocationTrackStatus = null,
      heapDumpStatus = null
    )
    val resultWithTraceStopStatus = studioFeatureTracker.buildStatsTaskStartFailedMetadata(metadataWithTraceStartStatus)
    assertEquals(resultWithTraceStopStatus.traceStartStatus, TaskMetadataStatus.getTaskFailedTraceStartStatus(traceStartStatus))

    // Build allocation track status
    val allocationTrackStatus = buildAllocationTrackStatus()
    val metadataWithAllocationTrackStatus = TaskStartFailedMetadata(
      traceStartStatus = null,
      allocationTrackStatus = allocationTrackStatus,
      heapDumpStatus = null
    )
    val resultWithAllocationTrackStatus = studioFeatureTracker.buildStatsTaskStartFailedMetadata(metadataWithAllocationTrackStatus)
    assertEquals(resultWithAllocationTrackStatus.trackStatus,
                 TaskMetadataStatus.getTaskFailedAllocationTrackStatus(allocationTrackStatus))
  }

  @Test
  fun testBuildStatsTaskProcessingFailedMetadata() {
    // Build CPU capture metadata
    val cpuCaptureMetadata = CpuCaptureMetadata(PerfettoSystemTraceConfiguration("Testing", false))
    val metadataWithCpuCapture = TaskProcessingFailedMetadata(
      cpuCaptureMetadata = cpuCaptureMetadata
    )
    val resultWithCpuCapture = studioFeatureTracker.buildStatsTaskProcessingFailedMetadata(metadataWithCpuCapture)
    assertEquals(resultWithCpuCapture.cpuCaptureMetadata, CpuCaptureParser.getCpuCaptureMetadata(cpuCaptureMetadata))

    val metadataWithCpuCaptureNull = TaskProcessingFailedMetadata(
      cpuCaptureMetadata = null
    )
    // Check when cpuCaptureMetadata is null (No errors)
    studioFeatureTracker.buildStatsTaskProcessingFailedMetadata(metadataWithCpuCaptureNull)
  }

  private fun buildAllocationTrackStatus(): Memory.TrackStatus {
    return  Memory.TrackStatus.newBuilder()
      .setStatus(Memory.TrackStatus.Status.FAILURE_UNKNOWN)
      .setStartTime(1000)
      .build()
  }
}