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

import com.android.tools.profiler.proto.CpuProfiler
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProfilingConfigurationTest {
  @Test
  fun fromProto() {
    val proto = CpuProfiler.CpuProfilerConfiguration.newBuilder()
      .setName("MyConfiguration")
      .setMode(CpuProfiler.CpuProfilerConfiguration.Mode.INSTRUMENTED)
      .setProfilerType(CpuProfiler.CpuProfilerType.ART)
      .setSamplingIntervalUs(123)
      .setBufferSizeInMb(12)
      .build()
    val config = ProfilingConfiguration.fromProto(proto)

    assertThat(config.name).isEqualTo("MyConfiguration")
    assertThat(config.mode).isEqualTo(CpuProfiler.CpuProfilerConfiguration.Mode.INSTRUMENTED)
    assertThat(config.profilerType).isEqualTo(CpuProfiler.CpuProfilerType.ART)
    assertThat(config.profilingSamplingIntervalUs).isEqualTo(123)
    assertThat(config.profilingBufferSizeInMb).isEqualTo(12)
  }

  @Test
  fun toProto() {
    val configuration = ProfilingConfiguration("MyConfiguration", CpuProfiler.CpuProfilerType.SIMPLEPERF,
        CpuProfiler.CpuProfilerConfiguration.Mode.SAMPLED).apply {
      profilingBufferSizeInMb = 12
      profilingSamplingIntervalUs = 1234
    }
    val proto = configuration.toProto()

    assertThat(proto.name).isEqualTo("MyConfiguration")
    assertThat(proto.mode).isEqualTo(CpuProfiler.CpuProfilerConfiguration.Mode.SAMPLED)
    assertThat(proto.profilerType).isEqualTo(CpuProfiler.CpuProfilerType.SIMPLEPERF)
    assertThat(proto.samplingIntervalUs).isEqualTo(1234)
    assertThat(proto.bufferSizeInMb).isEqualTo(12)
  }

  @Test
  fun defaultConfigName() {
    var profilerType = CpuProfiler.CpuProfilerType.ART
    assertThat(ProfilingConfiguration.getDefaultConfigName(profilerType)).isEqualTo(ProfilingConfiguration.ART)

    profilerType = CpuProfiler.CpuProfilerType.SIMPLEPERF
    assertThat(ProfilingConfiguration.getDefaultConfigName(profilerType)).isEqualTo(ProfilingConfiguration.SIMPLEPERF)

    profilerType = CpuProfiler.CpuProfilerType.ATRACE
    assertThat(ProfilingConfiguration.getDefaultConfigName(profilerType)).isEqualTo(ProfilingConfiguration.ATRACE)

    profilerType = CpuProfiler.CpuProfilerType.UNSPECIFIED_PROFILER
    assertThat(ProfilingConfiguration.getDefaultConfigName(profilerType)).isEqualTo("Unknown Configuration")
  }
}