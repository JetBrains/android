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
import java.util.concurrent.TimeUnit

/**
 * This module implements capture data series for different kinds of underlying data.
 *
 * To implement data series retrieval for a new kind of data, supply appropriate adapter functions to `of`
 */
object CaptureDataSeries {
  @JvmStatic
  fun ofAllocationInfos(client: ProfilerClient, session: Common.Session, tracker: FeatureTracker, stage: BaseMemoryProfilerStage) =
    of({ getAllocationInfosForSession(client, session, it, stage.studioProfilers.ideServices) },
       { it.startTime }, { it.endTime },
       { if (it.legacy) LegacyAllocationCaptureObject(client, session, it, tracker)
         else LiveAllocationCaptureObject(client, session, it.startTime, null, stage) },
       { durUs, info, entry -> CaptureDurationData(durUs, !info.legacy, !info.legacy, entry)})

  @JvmStatic
  fun ofHeapDumpSamples(client: ProfilerClient, session: Common.Session, tracker: FeatureTracker, stage: BaseMemoryProfilerStage) =
    of({ getHeapDumpsForSession(client, session, it, stage.studioProfilers.ideServices) },
       { it.startTime }, { it.endTime },
       { HeapDumpCaptureObject(client, session, it, null, tracker, stage.studioProfilers.ideServices) },
       { durUs, _, entry -> CaptureDurationData(durUs, false, false, entry, HeapDumpCaptureObject::class.java)})

  @JvmStatic
  fun ofNativeAllocationSamples(client: ProfilerClient, session: Common.Session, tracker: FeatureTracker, stage: BaseMemoryProfilerStage) =
    of({ getNativeHeapSamplesForSession(client, session, it) },
       { it.fromTimestamp }, { it.toTimestamp },
       { NativeAllocationSampleCaptureObject(client, session, it, stage) },
       { durUs, _, entry -> CaptureDurationData(durUs, false, false, entry, NativeAllocationSampleCaptureObject::class.java)})

  private fun<C: CaptureObject,T> of(getSamples: (Range) -> List<T>,
                                     startTimeNs: (T) -> Long, endTimeNs: (T) -> Long,
                                     makeCapture: (T) -> C,
                                     makeDurationData: (Long, T, CaptureEntry<C>) -> CaptureDurationData<out CaptureObject>) =
    DataSeries.using { range ->
      getSamples(range).map {
        val startNs = startTimeNs(it)
        val endNs = endTimeNs(it)
        val durUs = if (endNs == Long.MAX_VALUE) Long.MAX_VALUE else (endNs - startNs).nanosToMicros()
        SeriesData(startNs.nanosToMicros(), makeDurationData(durUs, it, CaptureEntry(it!!) {makeCapture(it)}))
      }
    }
}

private fun Long.nanosToMicros() = TimeUnit.NANOSECONDS.toMicros(this)