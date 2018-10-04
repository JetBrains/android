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
package com.android.tools.profilers.cpu

import com.android.tools.profiler.proto.CpuProfiler.*
import com.google.common.truth.Truth.assertThat

import org.junit.Test

class ProfilingTechnologyTest {
  @Test
  fun fromConfigArtSampled() {
    val artSampledConfiguration = ProfilingConfiguration("MyConfiguration", CpuProfilerType.ART,
                                                         CpuProfilerMode.SAMPLED)
    assertThat(ProfilingTechnology.fromConfig(artSampledConfiguration)).isEqualTo(ProfilingTechnology.ART_SAMPLED)
  }

  @Test
  fun fromConfigArtInstrumented() {
    val artInstrumentedConfiguration = ProfilingConfiguration("MyConfiguration", CpuProfilerType.ART,
                                                              CpuProfilerMode.INSTRUMENTED)
    assertThat(ProfilingTechnology.fromConfig(artInstrumentedConfiguration))
      .isEqualTo(ProfilingTechnology.ART_INSTRUMENTED)
  }

  @Test
  fun fromConfigArtUnspecified() {
    val artUnspecifiedConfiguration = ProfilingConfiguration("MyConfiguration", CpuProfilerType.ART,
                                                             CpuProfilerMode.UNSPECIFIED_MODE)
    assertThat(ProfilingTechnology.fromConfig(artUnspecifiedConfiguration)).isEqualTo(ProfilingTechnology.ART_UNSPECIFIED)
  }

  @Test
  fun fromConfigSimpleperf() {
    val simpleperfConfiguration = ProfilingConfiguration("MyConfiguration", CpuProfilerType.SIMPLEPERF,
                                                         CpuProfilerMode.SAMPLED)
    assertThat(ProfilingTechnology.fromConfig(simpleperfConfiguration)).isEqualTo(ProfilingTechnology.SIMPLEPERF)
  }

  @Test
  fun fromConfigAtrace() {
    val atraceConfiguration = ProfilingConfiguration("MyConfiguration", CpuProfilerType.ATRACE,
                                                     CpuProfilerMode.SAMPLED)
    assertThat(ProfilingTechnology.fromConfig(atraceConfiguration)).isEqualTo(ProfilingTechnology.ATRACE)
  }

  @Test(expected = IllegalStateException::class)
  fun fromConfigUnexpectedConfig() {
    val unexpectedConfiguration = ProfilingConfiguration("MyConfiguration", CpuProfilerType.UNSPECIFIED_PROFILER,
                                                         CpuProfilerMode.SAMPLED)
    assertThat(ProfilingTechnology.fromConfig(unexpectedConfiguration)).isEqualTo("any config. it should fail before.")
  }

  @Test
  fun getType() {
    assertThat(ProfilingTechnology.SIMPLEPERF.type).isEqualTo(CpuProfilerType.SIMPLEPERF)
    assertThat(ProfilingTechnology.ATRACE.type).isEqualTo(CpuProfilerType.ATRACE)

    assertThat(ProfilingTechnology.ART_INSTRUMENTED.type).isEqualTo(CpuProfilerType.ART)
    assertThat(ProfilingTechnology.ART_SAMPLED.type).isEqualTo(CpuProfilerType.ART)
    assertThat(ProfilingTechnology.ART_UNSPECIFIED.type).isEqualTo(CpuProfilerType.ART)
  }

  @Test
  fun getMode() {
    assertThat(ProfilingTechnology.SIMPLEPERF.mode).isEqualTo(CpuProfilerMode.SAMPLED)
    assertThat(ProfilingTechnology.ATRACE.mode).isEqualTo(CpuProfilerMode.INSTRUMENTED)

    assertThat(ProfilingTechnology.ART_INSTRUMENTED.mode).isEqualTo(CpuProfilerMode.INSTRUMENTED)
    assertThat(ProfilingTechnology.ART_SAMPLED.mode).isEqualTo(CpuProfilerMode.SAMPLED)
    assertThat(ProfilingTechnology.ART_UNSPECIFIED.mode).isEqualTo(CpuProfilerMode.UNSPECIFIED_MODE)
  }
}