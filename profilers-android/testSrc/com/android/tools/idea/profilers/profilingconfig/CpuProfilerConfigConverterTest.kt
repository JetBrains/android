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

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.run.profiler.CpuProfilerConfig
import com.android.tools.profiler.proto.Cpu
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CpuProfilerConfigConverterTest {

  @Test
  fun fromProtoSampledJava() {
    val proto = Cpu.CpuTraceConfiguration.UserOptions
      .newBuilder()
      .setName("MySampledJava")
      .setTraceType(Cpu.CpuTraceType.ART)
      .setTraceMode(Cpu.CpuTraceMode.SAMPLED)
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
    val proto = Cpu.CpuTraceConfiguration.UserOptions
      .newBuilder()
      .setTraceType(Cpu.CpuTraceType.SIMPLEPERF)
      .setTraceMode(Cpu.CpuTraceMode.SAMPLED)
      .build()
    val config = CpuProfilerConfigConverter.fromProto(proto)
    assertThat(config.technology).isEqualTo(CpuProfilerConfig.Technology.SAMPLED_NATIVE)
  }

  @Test
  fun fromProtoInstrumentedJava() {
    val proto = Cpu.CpuTraceConfiguration.UserOptions
      .newBuilder()
      .setTraceType(Cpu.CpuTraceType.ART)
      .setTraceMode(Cpu.CpuTraceMode.INSTRUMENTED)
      .build()
    val config = CpuProfilerConfigConverter.fromProto(proto)
    assertThat(config.technology).isEqualTo(CpuProfilerConfig.Technology.INSTRUMENTED_JAVA)
  }

  @Test
  fun fromProtoAtrace() {
    val proto = Cpu.CpuTraceConfiguration.UserOptions
      .newBuilder()
      .setTraceType(Cpu.CpuTraceType.ATRACE)
      .setTraceMode(Cpu.CpuTraceMode.INSTRUMENTED)
      .build()
    val config = CpuProfilerConfigConverter.fromProto(proto)
    assertThat(config.technology).isEqualTo(CpuProfilerConfig.Technology.SYSTEM_TRACE)
  }

  @Test
  fun fromProtoPerfetto() {
    val proto = Cpu.CpuTraceConfiguration.UserOptions
      .newBuilder()
      .setTraceType(Cpu.CpuTraceType.PERFETTO)
      .setTraceMode(Cpu.CpuTraceMode.INSTRUMENTED)
      .build()
    val config = CpuProfilerConfigConverter.fromProto(proto)
    assertThat(config.technology).isEqualTo(CpuProfilerConfig.Technology.SYSTEM_TRACE)
  }

  @Test
  fun toProtoSampledJava() {
    val config = CpuProfilerConfig("MySampledJava", CpuProfilerConfig.Technology.SAMPLED_JAVA).apply {
      samplingIntervalUs = 1234
      bufferSizeMb = 12
    }

    val proto = CpuProfilerConfigConverter.toProto(config, AndroidVersion.VersionCodes.N)
    assertThat(proto.name).isEqualTo("MySampledJava")
    assertThat(proto.traceType).isEqualTo(Cpu.CpuTraceType.ART)
    assertThat(proto.traceMode).isEqualTo(Cpu.CpuTraceMode.SAMPLED)
    assertThat(proto.samplingIntervalUs).isEqualTo(1234)
    assertThat(proto.bufferSizeInMb).isEqualTo(12)
  }

  @Test
  fun toProtoInstrumentedJava() {
    val config = CpuProfilerConfig().apply {
      technology = CpuProfilerConfig.Technology.INSTRUMENTED_JAVA
    }

    val proto = CpuProfilerConfigConverter.toProto(config, AndroidVersion.VersionCodes.N)
    assertThat(proto.traceType).isEqualTo(Cpu.CpuTraceType.ART)
    assertThat(proto.traceMode).isEqualTo(Cpu.CpuTraceMode.INSTRUMENTED)
  }


  @Test
  fun toProtoSampledNative() {
    val config = CpuProfilerConfig().apply {
      technology = CpuProfilerConfig.Technology.SAMPLED_NATIVE
    }

    val proto = CpuProfilerConfigConverter.toProto(config, AndroidVersion.VersionCodes.N)
    assertThat(proto.traceType).isEqualTo(Cpu.CpuTraceType.SIMPLEPERF)
    assertThat(proto.traceMode).isEqualTo(Cpu.CpuTraceMode.SAMPLED)
  }

  @Test
  fun toProtoSystemTraceOnN() {
    val config = CpuProfilerConfig().apply {
      technology = CpuProfilerConfig.Technology.SYSTEM_TRACE
    }

    val proto = CpuProfilerConfigConverter.toProto(config, AndroidVersion.VersionCodes.N)
    assertThat(proto.traceType).isEqualTo(Cpu.CpuTraceType.ATRACE)
    assertThat(proto.traceMode).isEqualTo(Cpu.CpuTraceMode.INSTRUMENTED)
  }

  @Test
  fun toProtoSystemTraceOnO() {
    val config = CpuProfilerConfig().apply {
      technology = CpuProfilerConfig.Technology.SYSTEM_TRACE
    }

    val proto = CpuProfilerConfigConverter.toProto(config, AndroidVersion.VersionCodes.O)
    assertThat(proto.traceType).isEqualTo(Cpu.CpuTraceType.ATRACE)
    assertThat(proto.traceMode).isEqualTo(Cpu.CpuTraceMode.INSTRUMENTED)
  }

  @Test
  fun toProtoSystemTraceOnP() {
    val config = CpuProfilerConfig().apply {
      technology = CpuProfilerConfig.Technology.SYSTEM_TRACE
    }

    val proto = CpuProfilerConfigConverter.toProto(config, AndroidVersion.VersionCodes.P)
    assertThat(proto.traceType).isEqualTo(Cpu.CpuTraceType.PERFETTO)
    assertThat(proto.traceMode).isEqualTo(Cpu.CpuTraceMode.INSTRUMENTED)
  }

  @Test
  fun toProtoSystemTraceOnQ() {
    val config = CpuProfilerConfig().apply {
      technology = CpuProfilerConfig.Technology.SYSTEM_TRACE
    }

    val proto = CpuProfilerConfigConverter.toProto(config, AndroidVersion.VersionCodes.Q)
    assertThat(proto.traceType).isEqualTo(Cpu.CpuTraceType.PERFETTO)
    assertThat(proto.traceMode).isEqualTo(Cpu.CpuTraceMode.INSTRUMENTED)
  }
}