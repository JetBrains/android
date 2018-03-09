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
package com.android.tools.idea.profilers.profilingconfig

import com.android.tools.idea.run.profiler.CpuProfilerConfig
import com.android.tools.profiler.proto.CpuProfiler
import com.google.common.truth.Truth
import com.google.common.truth.Truth.*
import org.junit.Test

import org.junit.Assert.*

class CpuProfilerConfigConverterTest {

  @Test
  fun fromProtoSampledJava() {
    val proto = CpuProfiler.CpuProfilerConfiguration
      .newBuilder()
      .setName("MySampledJava")
      .setProfilerType(CpuProfiler.CpuProfilerType.ART)
      .setMode(CpuProfiler.CpuProfilerConfiguration.Mode.SAMPLED)
      .setSamplingIntervalUs(1234)
      .setBufferSizeInMb(12)
      .build()

    val config = CpuProfilerConfigConverter.fromProto(proto)
    assertThat(config.name).isEqualTo("MySampledJava")
    assertThat(config.technology).isEqualTo(CpuProfilerConfig.Technology.SAMPLED_JAVA)
    assertThat(config.samplingIntervalUs).isEqualTo(1234)
    assertThat(config.bufferSizeMb).isEqualTo(12)
  }

  @Test
  fun fromProtoSampledNative() {
    val proto = CpuProfiler.CpuProfilerConfiguration
      .newBuilder()
      .setProfilerType(CpuProfiler.CpuProfilerType.SIMPLEPERF)
      .setMode(CpuProfiler.CpuProfilerConfiguration.Mode.SAMPLED)
      .build()
    val config = CpuProfilerConfigConverter.fromProto(proto)
    assertThat(config.technology).isEqualTo(CpuProfilerConfig.Technology.SAMPLED_NATIVE)
  }

  @Test
  fun fromProtoInstrumentedJava() {
    val proto = CpuProfiler.CpuProfilerConfiguration
      .newBuilder()
      .setProfilerType(CpuProfiler.CpuProfilerType.ART)
      .setMode(CpuProfiler.CpuProfilerConfiguration.Mode.INSTRUMENTED)
      .build()
    val config = CpuProfilerConfigConverter.fromProto(proto)
    assertThat(config.technology).isEqualTo(CpuProfilerConfig.Technology.INSTRUMENTED_JAVA)
  }

  @Test
  fun fromProtoAtrace() {
    val proto = CpuProfiler.CpuProfilerConfiguration
      .newBuilder()
      .setProfilerType(CpuProfiler.CpuProfilerType.ATRACE)
      .setMode(CpuProfiler.CpuProfilerConfiguration.Mode.SAMPLED)
      .build()
    val config = CpuProfilerConfigConverter.fromProto(proto)
    assertThat(config.technology).isEqualTo(CpuProfilerConfig.Technology.ATRACE)
  }

  @Test
  fun toProtoSampledJava() {
    val config = CpuProfilerConfig("MySampledJava", CpuProfilerConfig.Technology.SAMPLED_JAVA).apply {
      samplingIntervalUs = 1234
      bufferSizeMb = 12
    }

    val proto = CpuProfilerConfigConverter.toProto(config)
    assertThat(proto.name).isEqualTo("MySampledJava")
    assertThat(proto.profilerType).isEqualTo(CpuProfiler.CpuProfilerType.ART)
    assertThat(proto.mode).isEqualTo(CpuProfiler.CpuProfilerConfiguration.Mode.SAMPLED)
    assertThat(proto.samplingIntervalUs).isEqualTo(1234)
    assertThat(proto.bufferSizeInMb).isEqualTo(12)
  }

  @Test
  fun toProtoInstrumentedJava() {
    val config = CpuProfilerConfig().apply {
      technology = CpuProfilerConfig.Technology.INSTRUMENTED_JAVA
    }

    val proto = CpuProfilerConfigConverter.toProto(config)
    assertThat(proto.profilerType).isEqualTo(CpuProfiler.CpuProfilerType.ART)
    assertThat(proto.mode).isEqualTo(CpuProfiler.CpuProfilerConfiguration.Mode.INSTRUMENTED)
  }


  @Test
  fun toProtoSampledNative() {
    val config = CpuProfilerConfig().apply {
      technology = CpuProfilerConfig.Technology.SAMPLED_NATIVE
    }

    val proto = CpuProfilerConfigConverter.toProto(config)
    assertThat(proto.profilerType).isEqualTo(CpuProfiler.CpuProfilerType.SIMPLEPERF)
    assertThat(proto.mode).isEqualTo(CpuProfiler.CpuProfilerConfiguration.Mode.SAMPLED)
  }

  @Test
  fun toProtoAtrace() {
    val config = CpuProfilerConfig().apply {
      technology = CpuProfilerConfig.Technology.ATRACE
    }

    val proto = CpuProfilerConfigConverter.toProto(config)
    assertThat(proto.profilerType).isEqualTo(CpuProfiler.CpuProfilerType.ATRACE)
    assertThat(proto.mode).isEqualTo(CpuProfiler.CpuProfilerConfiguration.Mode.SAMPLED)
  }
}