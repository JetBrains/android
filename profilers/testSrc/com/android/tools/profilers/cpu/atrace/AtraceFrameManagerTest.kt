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

import com.android.tools.profilers.cpu.CpuFramesModel
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.android.tools.profilers.cpu.atrace.AtraceTestUtils.Companion.TEST_PID
import com.android.tools.profilers.cpu.atrace.AtraceTestUtils.Companion.TEST_RENDER_ID
import com.android.tools.profilers.cpu.atrace.AtraceTestUtils.Companion.convertTimeStamps
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import trebuchet.model.Model
import trebuchet.model.ProcessModel
import trebuchet.model.fragments.ModelFragment
import trebuchet.model.fragments.ProcessModelFragment
import trebuchet.model.fragments.SliceGroupBuilder
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
    process = model.processes[TEST_PID]!!
  }

  @Test
  fun filterReturnsFramesOfSameThread() {
    val frameManager = AtraceFrameManager(process, ::convertTimeStamps, TEST_RENDER_ID)
    val frameFilter = AtraceFrameFilterConfig("Choreographer#doFrame", TEST_PID,
                                              TimeUnit.MILLISECONDS.toMicros(30))
    val frames = frameManager.buildFramesList(frameFilter)
    // Validation metrics come from systrace for the same file.
    assertThat(frames).hasSize(122)
    assertThat(frames[0].perfClass).isEqualTo(AtraceFrame.PerfClass.GOOD)
    assertThat(frames[1].perfClass).isEqualTo(AtraceFrame.PerfClass.BAD)
  }

  @Test
  fun noMatchingThreadReturnsEmptyList() {
    val frameManager = AtraceFrameManager(process, ::convertTimeStamps, TEST_RENDER_ID)
    var frameFilter = AtraceFrameFilterConfig("Choreographer#doFrame", 0,
                                              TimeUnit.MILLISECONDS.toMicros(30))
    assertThat(frameManager.buildFramesList(frameFilter)).hasSize(0)
    frameFilter = AtraceFrameFilterConfig("TestEventOnValidThread", TEST_PID,
                                          TimeUnit.MILLISECONDS.toMicros(30))
    assertThat(frameManager.buildFramesList(frameFilter)).hasSize(0)
  }

  private fun getSlice(startTime: Double, endTime: Double, name: String): SliceGroupBuilder.MutableSliceGroup {
    return SliceGroupBuilder.MutableSliceGroup(startTime, endTime, false, endTime - startTime, name).apply { validate() }
  }

  @Test
  fun mainThreadFramesShouldBeAssociatedWithRenderThreadFrames() {
    val fragment = ModelFragment()
    fragment.processes.add(ProcessModelFragment(TEST_PID, "Test").apply {
      threadFor(TEST_PID, "Main").apply {
        hint(TEST_PID, "Main", TEST_PID, "Test")
        slicesBuilder.slices.apply {
          add(getSlice(2.0, 5.0, "Choreographer#doFrame"))
          add(getSlice(7.0, 11.0, "Choreographer#doFrame"))
          add(getSlice(20.0, 22.0, "Choreographer#doFrame"))
          add(getSlice(30.0, 50.0, "Choreographer#doFrame"))
        }
      }

      threadFor(TEST_RENDER_ID, "Render").apply {
        hint(TEST_RENDER_ID, "Render", TEST_PID, "Test")
        slicesBuilder.slices.apply {
          add(getSlice(4.0, 7.0, "DrawFrame"))
          add(getSlice(10.0, 13.0, "doFrame"))
          add(getSlice(15.0, 17.0, "queueBuffer"))
          add(getSlice(18.0, 20.0, "DrawFrame"))
          add(getSlice(40.0, 55.0, "queueBuffer"))
        }
      }
    })

    model = Model(fragment)
    process = model.processes[TEST_PID]!!
    val frameManager = AtraceFrameManager(process, ::convertTimeStamps, TEST_RENDER_ID)

    val mainThreadFrames = frameManager.buildFramesList(AtraceFrameFilterConfig(
      AtraceFrameFilterConfig.APP_MAIN_THREAD_FRAME_ID_MPLUS, TEST_PID, CpuFramesModel.SLOW_FRAME_RATE_US))
    val renderThreadFrames = frameManager.buildFramesList(AtraceFrameFilterConfig(
      AtraceFrameFilterConfig.APP_RENDER_THREAD_FRAME_ID_MPLUS, TEST_RENDER_ID, CpuFramesModel.SLOW_FRAME_RATE_US))

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
}