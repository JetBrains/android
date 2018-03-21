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
import com.android.tools.profilers.cpu.atrace.AtraceTestUtils.Companion.buildModelFragment
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import trebuchet.model.Model
import trebuchet.model.ProcessModel
import trebuchet.task.ImportTask
import trebuchet.util.PrintlnImportFeedback

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
  fun testStandardFrames() {
    val frameFactory = AtraceFrameFactory(model, process)
    val frames = frameFactory.buildFramesList()
    // Validation metrics come from systrace for the same file.
    assertThat(frames).hasSize(122)
    assertThat(frames[0].slices).hasSize(2)
    val uiThreadSlice = frames[0].slices[1]
    val renderThreadSlice = frames[0].slices[0]
    assertThat(uiThreadSlice.name).isEqualTo(AtraceFrameFactory.UIThreadDrawType.MARSHMALLOW.getDrawName())
    assertThat(renderThreadSlice.name).isEqualTo(AtraceFrameFactory.RenderThreadDrawNames.RENDER_THREAD.getDrawName())
  }

  @Test
  fun testNoRenderThread() {
    val modelFragment = buildModelFragment()
    val noRenderModel = Model(modelFragment)
    val frameFactory = AtraceFrameFactory(noRenderModel, noRenderModel.processes[1]!!)
    val frames = frameFactory.buildFramesList()
    assertThat(frames).hasSize(1)
    assertThat(frames[0].slices[0].name).isEqualTo(AtraceFrameFactory.UIThreadDrawType.MARSHMALLOW.getDrawName())
  }

  @Test
  fun testRenderThreadFrame() {
    val modelFragment = buildModelFragment()
    val renderThread = modelFragment.processes[0].threadFor(2, AtraceFrameFactory.RENDER_THREAD_NAME)
    renderThread.slicesBuilder.beginSlice {
      it.name = AtraceFrameFactory.RenderThreadDrawNames.RENDER_THREAD_INDEP.getDrawName()
      it.startTime = 1.0
    }
    renderThread.slicesBuilder.endSlice {
      it.endTime = 2.0
    }
    val renderModel = Model(modelFragment)
    val frameFactory = AtraceFrameFactory(renderModel, renderModel.processes[1]!!)
    val frames = frameFactory.buildFramesList()
    assertThat(frames).hasSize(2)
    assertThat(frames[1].slices[0].name).isEqualTo(AtraceFrameFactory.RenderThreadDrawNames.RENDER_THREAD_INDEP.getDrawName())
  }
}