/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.cpu.atrace

import com.android.tools.adtui.model.SeriesData
import com.android.tools.profilers.cpu.atrace.SurfaceflingerEvent.Type
import com.android.tools.profilers.systemtrace.CounterModel
import com.android.tools.profilers.systemtrace.CpuCoreModel
import com.android.tools.profilers.systemtrace.ProcessModel
import com.android.tools.profilers.systemtrace.SystemTraceModelAdapter
import com.android.tools.profilers.systemtrace.ThreadModel
import com.android.tools.profilers.systemtrace.TraceEventModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.lang.UnsupportedOperationException

class SystemTraceSurfaceflingerManagerTest {

  private companion object {
    private val SF_PID = 1
    private val SF_MAIN_THREAD_MODE = ThreadModel(SF_PID, SF_PID, "surfaceflinger",
                                                  listOf(
                                                  createEvent(3000, 5000),
                                                  createEvent(7000, 15000),
                                                  createEvent(15000, 20000)),
                                                  listOf())

    private val VSYNC_COUNTER = CounterModel("VSYNC-app",
                                             sortedMapOf(
                                               5000L to 0.0,
                                               10000L to 1.0,
                                               15000L to 0.0,
                                               20000L to 1.0,
                                               25000L to 0.0))

    private val SF_PROCESS_MODEL = ProcessModel(SF_PID, "/system/bin/surfaceflinger",
                                                mapOf(SF_PID to SF_MAIN_THREAD_MODE),
                                                mapOf("VSYNC-app" to VSYNC_COUNTER))

    private val MODEL = TestModel(listOf(SF_PROCESS_MODEL))

    private fun createEvent(startTimeUs: Long, endTimeUs: Long): TraceEventModel {
      return TraceEventModel("onMessageReceived", startTimeUs, endTimeUs, endTimeUs - startTimeUs, listOf())
    }
  }



  @Test
  fun surfaceflingerEvents() {
    val sfManager = SystemTraceSurfaceflingerManager(MODEL)
    val sfEvents = sfManager.surfaceflingerEvents

    // The test trace contains 3 real events + 1 start IDLE + 1 end IDLE + 1 padding IDLE between [3, 5] and [7, 15].
    assertThat(sfEvents.size).isEqualTo(6)
    assertThat(sfEvents).containsExactly(
      // The first event should be a starting IDLE event.
      SeriesData(0, SurfaceflingerEvent(0, 3000, Type.IDLE)),
      // The second event should be the first real PROCESSING event.
      SeriesData(3000, SurfaceflingerEvent(3000, 5000, Type.PROCESSING, "onMessageReceived")),
      // Then a padded IDLE event.
      SeriesData(5000, SurfaceflingerEvent(5000, 7000, Type.IDLE)),
      // Then another PROCESSING EVENT.
      SeriesData(7000, SurfaceflingerEvent(7000, 15000, Type.PROCESSING, "onMessageReceived")),
      // Then another PROCESSING EVENT, without a gap because start of this is equal to the previous end.
      SeriesData(15000, SurfaceflingerEvent(15000, 20000, Type.PROCESSING, "onMessageReceived")),
      // The last event should be a ending IDLE event.
      SeriesData(20000, SurfaceflingerEvent(20000, Long.MAX_VALUE, Type.IDLE))
    ).inOrder()
  }

  @Test
  fun vsyncCounterValues() {
    val sfManager = SystemTraceSurfaceflingerManager(MODEL)
    val vsyncValues = sfManager.vsyncCounterValues

    assertThat(vsyncValues.size).isEqualTo(5)
    assertThat(vsyncValues).containsExactly(
      SeriesData(5000, 0L),
      SeriesData(10000, 1L),
      SeriesData(15000, 0L),
      SeriesData(20000, 1L),
      SeriesData(25000, 0L)
    ).inOrder()
  }


  @Test
  fun sfProcessWithNoName() {
    // Same model, but with the process with a blank name.
    val model = TestModel(listOf(SF_PROCESS_MODEL.copy(name = "")))
    val sfManager = SystemTraceSurfaceflingerManager(model)

    val sfEvents = sfManager.surfaceflingerEvents
    assertThat(sfEvents.size).isEqualTo(6)
    val vsyncValues = sfManager.vsyncCounterValues
    assertThat(vsyncValues.size).isEqualTo(5)
  }

  private class TestModel(private val processes: List<ProcessModel>) : SystemTraceModelAdapter {
    override fun getProcesses(): List<ProcessModel> = processes

    override fun getCaptureStartTimestampUs() = throw UnsupportedOperationException("Not Implemented For Test")
    override fun getCaptureEndTimestampUs() = throw UnsupportedOperationException("Not Implemented For Test")
    override fun getProcessById(id: Int) = throw UnsupportedOperationException("Not Implemented For Test")
    override fun getCpuCores(): List<CpuCoreModel> = throw UnsupportedOperationException("Not Implemented For Test")
    override fun getSystemTraceTechnology() = throw UnsupportedOperationException("Not Implemented For Test")
    override fun isCapturePossibleCorrupted() = throw UnsupportedOperationException("Not Implemented For Test")
  }
}