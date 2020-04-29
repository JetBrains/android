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
import com.android.tools.profilers.cpu.CpuThreadInfo
import com.android.tools.profilers.cpu.atrace.AtraceTestUtils.Companion.TEST_PID
import com.android.tools.profilers.cpu.atrace.AtraceTestUtils.Companion.TEST_RENDER_ID
import com.android.tools.profilers.systemtrace.ProcessModel
import com.android.tools.profilers.systemtrace.ThreadModel
import com.android.tools.profilers.systemtrace.TraceEventModel
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import trebuchet.model.Model
import trebuchet.task.ImportTask
import trebuchet.util.PrintlnImportFeedback
import java.util.concurrent.TimeUnit

class AtraceFrameManagerTest {
  private lateinit var model: Model
  private lateinit var process: ProcessModel

  @Before
  fun setup() {
    val file = CpuProfilerTestUtils.getTraceFile("atrace.ctrace")
    val reader = AtraceProducer()
    assertThat(reader.parseFile(file)).isTrue()
    val task = ImportTask(PrintlnImportFeedback())
    model = task.importBuffer(reader)
    val modelAdapter = TrebuchetModelAdapter(model)
    process = modelAdapter.getProcessById(TEST_PID)!!
  }

  @Test
  fun filterReturnsFramesOfSameThread() {
    val frameManager = AtraceFrameManager(process)
    val frames = frameManager.getFramesList(AtraceFrame.FrameThread.MAIN)
    // Validation metrics come from systrace for the same file.
    assertThat(frames).hasSize(122)
    assertThat(frames.count { it.perfClass == AtraceFrame.PerfClass.GOOD}).isEqualTo(102)
    assertThat(frames.count { it.perfClass == AtraceFrame.PerfClass.BAD}).isEqualTo(20)
    assertThat(frames.count { it.perfClass == AtraceFrame.PerfClass.NOT_SET}).isEqualTo(0)
  }

  @Test
  fun noMatchingThreadReturnsEmptyList() {
    val frameManager = AtraceFrameManager(process)
    assertThat(frameManager.getFramesList(AtraceFrame.FrameThread.OTHER)).hasSize(0)
  }

  private fun getSlice(startTime: Long, endTime: Long, name: String): TraceEventModel {
    val startUs = TimeUnit.SECONDS.toMicros(startTime)
    val endUs = TimeUnit.SECONDS.toMicros(endTime)
    return TraceEventModel(name, startUs, endUs, endUs - startUs, listOf())
  }

  @Test
  fun mainThreadFramesShouldBeAssociatedWithRenderThreadFrames() {
    val mainThread = ThreadModel(TEST_PID, TEST_PID, "Main",
                                 listOf(
                                   getSlice(2, 5, "Choreographer#doFrame"),
                                   getSlice(7, 11, "Choreographer#doFrame"),
                                   getSlice(20, 22, "Choreographer#doFrame"),
                                   getSlice(30, 50, "Choreographer#doFrame")
                                 ),
                                 listOf())
    val renderThread = ThreadModel(TEST_RENDER_ID, TEST_PID, CpuThreadInfo.RENDER_THREAD_NAME,
                                 listOf(
                                   getSlice(4, 7, "DrawFrame"),
                                   getSlice(10, 13, "doFrame"),
                                   getSlice(15, 17, "queueBuffer"),
                                   getSlice(18, 20, "DrawFrame"),
                                   getSlice(40, 55, "queueBuffer")
                                 ),
                                 listOf())

    val process = ProcessModel(TEST_PID, "Test",
                               mapOf(TEST_PID to mainThread, TEST_RENDER_ID to renderThread),
                               emptyMap())

    val frameManager = AtraceFrameManager(process)

    val mainThreadFrames = frameManager.getFramesList(AtraceFrame.FrameThread.MAIN)
    val renderThreadFrames = frameManager.getFramesList(AtraceFrame.FrameThread.RENDER)

    assertThat(mainThreadFrames.size).isEqualTo(4)
    assertThat(renderThreadFrames.size).isEqualTo(5)

    assertThat(mainThreadFrames[0].associatedFrame).isEqualTo(renderThreadFrames[0])
    assertThat(mainThreadFrames[1].associatedFrame).isEqualTo(renderThreadFrames[1])
    assertThat(mainThreadFrames[2].associatedFrame).isEqualTo(null)
    assertThat(mainThreadFrames[3].associatedFrame).isEqualTo(renderThreadFrames[4])

    assertThat(renderThreadFrames[0].associatedFrame).isEqualTo(mainThreadFrames[0])
    assertThat(renderThreadFrames[1].associatedFrame).isEqualTo(mainThreadFrames[1])
    assertThat(renderThreadFrames[2].associatedFrame).isEqualTo(null)
    assertThat(renderThreadFrames[3].associatedFrame).isEqualTo(null)
    assertThat(renderThreadFrames[4].associatedFrame).isEqualTo(mainThreadFrames[3])
  }

  @Test
  fun framesEndWithEmptyFrame() {
    val frameManager = AtraceFrameManager(process)
    val frames = frameManager.getFrames(AtraceFrame.FrameThread.MAIN)
    // Each frame has a empty frame after it for spacing.
    assertThat(frames).hasSize(122 * 2)
    for (i in 0 until frames.size step 2) {
      assertThat(frames[i].value).isNotEqualTo(AtraceFrame.EMPTY)
      assertThat(frames[i + 1].value).isEqualTo(AtraceFrame.EMPTY)
    }
  }
}