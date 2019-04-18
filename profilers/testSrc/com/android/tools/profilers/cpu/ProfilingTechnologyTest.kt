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

import com.android.tools.profiler.proto.Cpu.CpuTraceMode
import com.android.tools.profiler.proto.Cpu.CpuTraceType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProfilingTechnologyTest {
  @Test
  fun fromConfigArtSampled() {
    val artSampledConfiguration = ProfilingConfiguration("MyConfiguration", CpuTraceType.ART,
                                                         CpuTraceMode.SAMPLED)
    assertThat(ProfilingTechnology.fromConfig(artSampledConfiguration)).isEqualTo(ProfilingTechnology.ART_SAMPLED)
  }

  @Test
  fun fromConfigArtInstrumented() {
    val artInstrumentedConfiguration = ProfilingConfiguration("MyConfiguration", CpuTraceType.ART,
                                                              CpuTraceMode.INSTRUMENTED)
    assertThat(ProfilingTechnology.fromConfig(artInstrumentedConfiguration))
      .isEqualTo(ProfilingTechnology.ART_INSTRUMENTED)
  }

  @Test
  fun fromConfigArtUnspecified() {
    val artUnspecifiedConfiguration = ProfilingConfiguration("MyConfiguration", CpuTraceType.ART,
                                                             CpuTraceMode.UNSPECIFIED_MODE)
    assertThat(ProfilingTechnology.fromConfig(artUnspecifiedConfiguration)).isEqualTo(ProfilingTechnology.ART_UNSPECIFIED)
  }

  @Test
  fun fromConfigSimpleperf() {
    val simpleperfConfiguration = ProfilingConfiguration("MyConfiguration", CpuTraceType.SIMPLEPERF,
                                                         CpuTraceMode.SAMPLED)
    assertThat(ProfilingTechnology.fromConfig(simpleperfConfiguration)).isEqualTo(ProfilingTechnology.SIMPLEPERF)
  }

  @Test
  fun fromConfigAtrace() {
    val atraceConfiguration = ProfilingConfiguration("MyConfiguration", CpuTraceType.ATRACE,
                                                     CpuTraceMode.SAMPLED)
    assertThat(ProfilingTechnology.fromConfig(atraceConfiguration)).isEqualTo(ProfilingTechnology.ATRACE)
  }

  @Test(expected = IllegalStateException::class)
  fun fromConfigUnexpectedConfig() {
    val unexpectedConfiguration = ProfilingConfiguration("MyConfiguration", CpuTraceType.UNSPECIFIED_TYPE,
                                                         CpuTraceMode.SAMPLED)
    assertThat(ProfilingTechnology.fromConfig(unexpectedConfiguration)).isEqualTo("any config. it should fail before.")
  }

  @Test
  fun getType() {
    assertThat(ProfilingTechnology.SIMPLEPERF.type).isEqualTo(CpuTraceType.SIMPLEPERF)
    assertThat(ProfilingTechnology.ATRACE.type).isEqualTo(CpuTraceType.ATRACE)

    assertThat(ProfilingTechnology.ART_INSTRUMENTED.type).isEqualTo(CpuTraceType.ART)
    assertThat(ProfilingTechnology.ART_SAMPLED.type).isEqualTo(CpuTraceType.ART)
    assertThat(ProfilingTechnology.ART_UNSPECIFIED.type).isEqualTo(CpuTraceType.ART)
  }

  @Test
  fun getMode() {
    assertThat(ProfilingTechnology.SIMPLEPERF.mode).isEqualTo(CpuTraceMode.SAMPLED)
    assertThat(ProfilingTechnology.ATRACE.mode).isEqualTo(CpuTraceMode.INSTRUMENTED)

    assertThat(ProfilingTechnology.ART_INSTRUMENTED.mode).isEqualTo(CpuTraceMode.INSTRUMENTED)
    assertThat(ProfilingTechnology.ART_SAMPLED.mode).isEqualTo(CpuTraceMode.SAMPLED)
    assertThat(ProfilingTechnology.ART_UNSPECIFIED.mode).isEqualTo(CpuTraceMode.UNSPECIFIED_MODE)
  }
}