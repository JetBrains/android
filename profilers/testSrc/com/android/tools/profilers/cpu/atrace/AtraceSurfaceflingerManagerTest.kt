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
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.android.tools.profilers.cpu.atrace.SurfaceflingerEvent.Type
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import trebuchet.task.ImportTask
import trebuchet.util.PrintlnImportFeedback
import java.util.concurrent.TimeUnit
import java.util.function.Function

class AtraceSurfaceflingerManagerTest {
  private val model by lazy {
    val file = CpuProfilerTestUtils.getTraceFile("atrace.ctrace")
    val reader = AtraceProducer()
    assertThat(reader.parseFile(file)).isTrue()
    val task = ImportTask(PrintlnImportFeedback())
    task.importBuffer(reader)
  }

  @Test
  fun surfaceflingerEvents() {
    val sfManager = AtraceSurfaceflingerManager(model, TimestampConverter)
    val sfEvents = sfManager.surfaceflingerEvents

    // The test trace contains 96 onMessageReceived trace events. With padded IDLE events we should have 2n + 1 events.
    assertThat(sfEvents.size).isEqualTo(193)
    // The first event should be a padded IDLE event.
    assertThat(sfEvents[0]).isEqualTo(SeriesData(0, SurfaceflingerEvent(0, 87691153587, Type.IDLE)))
    // The second event should be the first real PROCESSING event.
    assertThat(sfEvents[1]).isEqualTo(SeriesData(87691153587, SurfaceflingerEvent(87691153587, 87691156147, Type.PROCESSING)))
    // The last event should be a padded IDLE event.
    assertThat(sfEvents[sfEvents.lastIndex]).isEqualTo(SeriesData(87701852439, SurfaceflingerEvent(87701852439, Long.MAX_VALUE, Type.IDLE)))
  }

  @Test
  fun vsyncCounterValues() {
    val sfManager = AtraceSurfaceflingerManager(model, TimestampConverter)
    val vsyncValues = sfManager.vsyncCounterValues

    assertThat(vsyncValues.size).isEqualTo(244)
    assertThat(vsyncValues[0]).isEqualTo(SeriesData(87691147730, 1L))
    assertThat(vsyncValues[1]).isEqualTo(SeriesData(87691166291, 0L))
  }

  companion object {
    // Convenient converter that simply converts from seconds (double) to microseconds (long).
    private object TimestampConverter : Function<Double, Long> {
      override fun apply(timeSec: Double): Long {
        return (TimeUnit.SECONDS.toMicros(1) * timeSec).toLong()
      }
    }
  }
}