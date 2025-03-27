/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.profilers.cpu.analysis

import com.android.tools.adtui.model.Range
import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.analysis.CpuAnalysisTabModel.Type
import com.android.tools.profilers.cpu.systemtrace.CpuSystemTraceData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever

class FramesAnalysisModelTest {
  @Test
  fun `frames model has all-frames tab`() {
    val systemTraceData = Mockito.mock(CpuSystemTraceData::class.java).apply {
      whenever(androidFrameLayers).thenReturn(listOf(TraceProcessor.AndroidFrameEventsResult.Layer.getDefaultInstance()))
    }
    val capture = Mockito.mock(CpuCapture::class.java).apply {
      whenever(this.range).thenReturn(Range())
      whenever(this.systemTraceData).thenReturn(systemTraceData)
    }
    val model = FramesAnalysisModel.of(capture)!!
    val analysisModels = model.tabModels.map(CpuAnalysisTabModel<*>::getTabType).toSet()
    assertThat(analysisModels).containsExactly(Type.FRAMES)
  }
}