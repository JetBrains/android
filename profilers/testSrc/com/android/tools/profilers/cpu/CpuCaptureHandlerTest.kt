/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu

import com.android.tools.profilers.FakeIdeProfilerServices
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CpuCaptureHandlerTest {
  @Test
  fun updateUpdatesRange() {
    val model = CpuCaptureHandler(FakeIdeProfilerServices(), CpuProfilerTestUtils.getTraceFile("simpleperf.trace"), "Test", null)
    assertThat(model.range.isEmpty).isTrue()
    model.update(1234L)
    assertThat(model.range.isEmpty).isTrue()
    model.parse {
      assertThat(it).isNotNull()
    }
    assertThat(model.range.min).isEqualTo(0.0)
    assertThat(model.range.max).isEqualTo(0.0)
  }

  @Test
  fun failureToParseShowsNotification() {
    val services = FakeIdeProfilerServices()
    val model = CpuCaptureHandler(services, CpuProfilerTestUtils.getTraceFile("corrupted_trace.trace"), "Test", null)
    model.parse {
      assertThat(it).isNull()
    }
    assertThat(services.notification).isNotNull()
  }
}