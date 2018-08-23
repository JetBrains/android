/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.android.tools.profilers.cpu.atrace.AtraceTestUtils.Companion.TEST_PID
import com.android.tools.profilers.cpu.atrace.AtraceTestUtils.Companion.convertTimeStamps
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import trebuchet.model.Model
import trebuchet.model.ProcessModel
import trebuchet.task.ImportTask
import trebuchet.util.PrintlnImportFeedback
import java.util.concurrent.TimeUnit

class AtraceFrameFactoryTest {
  private lateinit var model: Model
  private lateinit var process: ProcessModel
  @Before
  fun setup() {
    val file = CpuProfilerTestUtils.getTraceFile("atrace.ctrace")
    val reader = AtraceDecompressor(file)
    val task = ImportTask(PrintlnImportFeedback())
    model = task.importBuffer(reader)
    process = model.processes[TEST_PID]!!
  }

  @Test
  fun filterReturnsFramesOfSameThread() {
    val frameFactory = AtraceFrameFactory(process, ::convertTimeStamps)
    val frameFilter = AtraceFrameFilterConfig("Choreographer#doFrame", TEST_PID,
                                              TimeUnit.MILLISECONDS.toMicros(30));
    val frames = frameFactory.buildFramesList(frameFilter)
    // Validation metrics come from systrace for the same file.
    assertThat(frames).hasSize(122)
    assertThat(frames[0].perfClass).isEqualTo(AtraceFrame.PerfClass.GOOD)
    assertThat(frames[1].perfClass).isEqualTo(AtraceFrame.PerfClass.BAD)
  }

  @Test
  fun noMatchingThreadReturnsEmptyList() {
    val frameFactory = AtraceFrameFactory(process, ::convertTimeStamps)
    var frameFilter = AtraceFrameFilterConfig("Choreographer#doFrame", 0,
                                              TimeUnit.MILLISECONDS.toMicros(30));
    assertThat(frameFactory.buildFramesList(frameFilter)).hasSize(0)
    frameFilter = AtraceFrameFilterConfig("TestEventOnValidThread", TEST_PID,
                                          TimeUnit.MILLISECONDS.toMicros(30));
    assertThat(frameFactory.buildFramesList(frameFilter)).hasSize(0)
  }
}