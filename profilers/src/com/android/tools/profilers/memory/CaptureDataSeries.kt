/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.memory

import com.android.tools.adtui.model.DataSeries
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.SeriesData
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.analytics.FeatureTracker
import com.android.tools.profilers.memory.MemoryProfiler.Companion.getAllocationInfosForSession
import com.android.tools.profilers.memory.MemoryProfiler.Companion.getHeapDumpsForSession
import com.android.tools.profilers.memory.MemoryProfiler.Companion.getNativeHeapSamplesForSession
import com.android.tools.profilers.memory.adapters.CaptureObject
import com.android.tools.profilers.memory.adapters.HeapDumpCaptureObject
import com.android.tools.profilers.memory.adapters.LegacyAllocationCaptureObject
import com.android.tools.profilers.memory.adapters.LiveAllocationCaptureObject
import com.android.tools.profilers.memory.adapters.NativeAllocationSampleCaptureObject
import com.intellij.openapi.application.ApplicationManager
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

/**
 * This module implements capture data series for different kinds of underlying data.
 *
 * To implement data series retrieval for a new kind of data, supply appropriate adapter functions to `of`
 */
object CaptureDataSeries {
  @JvmStatic
  fun ofLegacyAllocationInfos(client: ProfilerClient, session: Common.Session, tracker: FeatureTracker, stage: BaseMemoryProfilerStage) =
    of(
      { getAllocationInfosForSession(client, session, it) },
      { it.startTime },
      { it.endTime },
      {
        LegacyAllocationCaptureObject(
          client,
          session,
          it,
          tracker,
          Supplier { getTraceFile(client, session.streamId, it.startTime.toString(), retry = true) },
        )
      },
      { durUs, _, entry -> CaptureDurationData(durUs, false, false, entry, LegacyAllocationCaptureObject::class.java) },
    )

  @JvmStatic
  fun ofAllocationInfos(client: ProfilerClient, session: Common.Session, tracker: FeatureTracker, stage: BaseMemoryProfilerStage) =
    of(
      { getAllocationInfosForSession(client, session, it) },
      { it.startTime },
      { it.endTime },
      { LiveAllocationCaptureObject(client, session, it.startTime, null, stage) },
      { durUs, _, entry -> CaptureDurationData(durUs, true, true, entry, LiveAllocationCaptureObject::class.java) },
    )

  @JvmStatic
  fun ofHeapDumpSamples(client: ProfilerClient, session: Common.Session, tracker: FeatureTracker, stage: BaseMemoryProfilerStage) =
    of(
      { getHeapDumpsForSession(client, session, it) },
      { it.startTime },
      { it.endTime },
      {
        HeapDumpCaptureObject(client, session, it, null, tracker, stage.studioProfilers.ideServices) {
          getTraceFile(client, session.streamId, it.startTime.toString(), retry = false)
        }
      },
      { durUs, _, entry -> CaptureDurationData(durUs, false, false, entry, HeapDumpCaptureObject::class.java) },
    )

  @JvmStatic
  fun ofNativeAllocationSamples(client: ProfilerClient, session: Common.Session, tracker: FeatureTracker, stage: BaseMemoryProfilerStage) =
    of(
      { getNativeHeapSamplesForSession(client, session, it) },
      { it.fromTimestamp },
      { it.toTimestamp },
      {
        NativeAllocationSampleCaptureObject(
          client,
          session,
          it,
          stage.context.ideProfilerServices,
          stage.studioProfilers.sessionsManager.selectedSessionMetaData.processAbi,
        ) {
          getTraceFile(client, session.streamId, it.fromTimestamp.toString(), retry = true)
        }
      },
      { durUs, _, entry -> CaptureDurationData(durUs, false, false, entry, NativeAllocationSampleCaptureObject::class.java) },
    )

  private fun <C : CaptureObject, T> of(
    getSamples: (Range) -> List<T>,
    startTimeNs: (T) -> Long,
    endTimeNs: (T) -> Long,
    makeCapture: (T) -> C,
    makeDurationData: (Long, T, CaptureEntry<C>) -> CaptureDurationData<out CaptureObject>,
  ) = DataSeries.using { range ->
    getSamples(range).map {
      val startNs = startTimeNs(it)
      val endNs = endTimeNs(it)
      val durUs = if (endNs == Long.MAX_VALUE) Long.MAX_VALUE else (endNs - startNs).nanosToMicros()
      SeriesData(startNs.nanosToMicros(), makeDurationData(durUs, it, CaptureEntry(it!!) { makeCapture(it) }))
    }
  }

  /**
   * Retrieves the trace file from the transport daemon.
   *
   * @param retry If true, blocks and polls for up to 10 seconds waiting for the file to become available. WARNING: If retry is true, this
   *   is a blocking call and must NOT be called on the UI thread.
   */
  private fun getTraceFile(client: ProfilerClient, streamId: Long, id: String, retry: Boolean): File {
    ApplicationManager.getApplication()?.assertIsNonDispatchThread()
    var response = Transport.FileResponse.getDefaultInstance()
    // TODO(b/519154304): Investigate if we can wait for the file to be fully cached before emitting the capture event, rather than polling
    // here.
    var retryCount = 100 // ~10 seconds
    while (response.filePath.isEmpty()) {
      response = client.transportClient.getFile(Transport.BytesRequest.newBuilder().setStreamId(streamId).setId(id).build())
      if (response.filePath.isNotEmpty()) break
      if (!retry || retryCount-- == 0) break
      try {
        Thread.sleep(100L)
      } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        break
      }
    }
    return File(response.filePath)
  }
}

private fun Long.nanosToMicros() = TimeUnit.NANOSECONDS.toMicros(this)
