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
package com.android.tools.profilers

import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.android.tools.profilers.cpu.systemtrace.ProcessModel
import com.google.common.truth.Truth
import org.junit.Test

class FakeTraceProcessorServiceTest {

  private val fakeIdeProfilerServices = FakeIdeProfilerServices()

  @Test
  fun `deserialize valid trace`() {
    val fakeService = FakeTraceProcessorService()

    val loadOk = fakeService.loadTrace(1, CpuProfilerTestUtils.getTraceFile("perfetto.trace"), fakeIdeProfilerServices)
    Truth.assertThat(loadOk).isTrue()

    val processList = fakeService.getProcessMetadata(1, fakeIdeProfilerServices)
    // Equivalent to "SELECT count(upid) FROM process"
    Truth.assertThat(processList).hasSize(76)

    val model = fakeService.loadCpuData(1, listOf(processList[0]), ProcessModel(1, "", emptyMap(), emptyMap()), fakeIdeProfilerServices)
    Truth.assertThat(model.getProcesses()).hasSize(76)
    Truth.assertThat(model.getCpuCores()).hasSize(8)
  }

  @Test
  fun `deserialize unknown trace`() {
    val fakeService = FakeTraceProcessorService()

    val loadOk = fakeService.loadTrace(1, CpuProfilerTestUtils.getTraceFile("valid_trace.trace"), fakeIdeProfilerServices)
    Truth.assertThat(loadOk).isFalse()
  }

}