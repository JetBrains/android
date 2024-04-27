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

import com.android.tools.profilers.FakeFeatureTracker
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.cpu.config.PerfettoSystemTraceConfiguration
import com.android.tools.profilers.cpu.config.SimpleperfConfiguration
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CpuCaptureHandlerTest {
  @Test
  fun updateUpdatesRange() {
    val model = CpuCaptureHandler(FakeIdeProfilerServices(), CpuProfilerTestUtils.getTraceFile("simpleperf.trace"), 123,
                                  ProfilersTestData.DEFAULT_CONFIG, null, 0)
    assertThat(model.range.isEmpty).isTrue()
    model.update(1234L)
    assertThat(model.range.isEmpty).isTrue()
    model.parse {
      assertThat(it).isNotNull()
      assertThat(model.range.min).isEqualTo(0.0)
      assertThat(model.range.max).isEqualTo(0.0)
    }
  }

  @Test
  fun parsingFailureShowsNotificationAndTracksExceptionType() {
    val config = PerfettoSystemTraceConfiguration("Test", false)
    val services = FakeIdeProfilerServices()
    val fakeFeatureTracker = services.featureTracker as FakeFeatureTracker
    val model = CpuCaptureHandler(services, CpuProfilerTestUtils.getTraceFile("corrupted_trace.trace"), 123, config, null, 0)
    model.parse {
      assertThat(it).isNull()
      assertThat(services.notification).isNotNull()
      assertThat(fakeFeatureTracker.lastCpuCaptureMetadata.status).isEqualTo(
        CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_FILE_HEADER_ERROR)
    }
  }

  @Test
  fun reportsTraceTypeAndModeInMetrics() {
    val config = SimpleperfConfiguration("Test")
    val services = FakeIdeProfilerServices()
    val fakeFeatureTracker = services.featureTracker as FakeFeatureTracker
    val model = CpuCaptureHandler(services, CpuProfilerTestUtils.getTraceFile("simpleperf_callchain.trace"), 123, config, null, 1)
    model.parse {
      assertThat(it).isNotNull()
      assertThat(fakeFeatureTracker.lastCpuCaptureMetadata.profilingConfiguration).isEqualTo(config)
    }
  }
}