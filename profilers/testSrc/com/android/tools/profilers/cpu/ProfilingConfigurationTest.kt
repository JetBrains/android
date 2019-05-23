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

import com.android.tools.profiler.proto.Cpu
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class ProfilingConfigurationTest {

  @get:Rule
  val myThrown = ExpectedException.none()

  @Test
  fun fromProto() {
    val proto = Cpu.CpuTraceConfiguration.UserOptions.newBuilder()
      .setName("MyConfiguration")
      .setTraceMode(Cpu.CpuTraceMode.INSTRUMENTED)
      .setTraceType(Cpu.CpuTraceType.ART)
      .setSamplingIntervalUs(123)
      .setBufferSizeInMb(12)
      .setDisableLiveAllocation(true)
      .build()
    val config = ProfilingConfiguration.fromProto(proto)

    assertThat(config.name).isEqualTo("MyConfiguration")
    assertThat(config.mode).isEqualTo(Cpu.CpuTraceMode.INSTRUMENTED)
    assertThat(config.traceType).isEqualTo(Cpu.CpuTraceType.ART)
    assertThat(config.profilingSamplingIntervalUs).isEqualTo(123)
    assertThat(config.profilingBufferSizeInMb).isEqualTo(12)
    assertThat(config.isDisableLiveAllocation).isTrue()
  }

  @Test
  fun toProto() {
    val configuration = ProfilingConfiguration("MyConfiguration", Cpu.CpuTraceType.SIMPLEPERF,
                                               Cpu.CpuTraceMode.SAMPLED).apply {
      profilingBufferSizeInMb = 12
      profilingSamplingIntervalUs = 1234
      isDisableLiveAllocation = true
    }
    val proto = configuration.toProto()

    assertThat(proto.name).isEqualTo("MyConfiguration")
    assertThat(proto.traceMode).isEqualTo(Cpu.CpuTraceMode.SAMPLED)
    assertThat(proto.traceType).isEqualTo(Cpu.CpuTraceType.SIMPLEPERF)
    assertThat(proto.samplingIntervalUs).isEqualTo(1234)
    assertThat(proto.bufferSizeInMb).isEqualTo(12)
    assertThat(proto.disableLiveAllocation).isTrue()
  }
}