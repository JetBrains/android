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
package com.android.tools.profilers.cpu.config

import com.android.tools.profiler.proto.Trace
import com.android.tools.profiler.proto.Trace.TraceConfiguration
import com.android.tools.profiler.proto.Trace.TraceMode
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.AdditionalOptions
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.SYSTEM_TRACE_BUFFER_SIZE_MB
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType

class ProfilingConfigurationTest {

  @get:Rule
  val myThrown = ExpectedException.none()

  @Test
  fun fromProto() {
    val proto = TraceConfiguration.newBuilder()
      .setArtOptions(Trace.ArtOptions.newBuilder().setTraceMode(TraceMode.SAMPLED).setSamplingIntervalUs(123).setBufferSizeInMb(12))
      .build()
    val config = ProfilingConfiguration.fromProto(proto)
    assertThat(config).isInstanceOf(ArtSampledConfiguration::class.java)
    config as ArtSampledConfiguration
    assertThat(config.name).isEqualTo("")
    assertThat(config).isInstanceOf(ArtSampledConfiguration::class.java)
    assertThat(config.traceType).isEqualTo(TraceType.ART)
    assertThat(config.profilingSamplingIntervalUs).isEqualTo(123)
    assertThat(config.profilingBufferSizeInMb).isEqualTo(12)
  }

  @Test
  fun fromProtoOptionsNotSet() {
    val proto = TraceConfiguration.getDefaultInstance()
    val config = ProfilingConfiguration.fromProto(proto)
    assertThat(config).isInstanceOf(UnspecifiedConfiguration::class.java)
    config as UnspecifiedConfiguration
    assertThat(config.name).isEqualTo("Unnamed")
    assertThat(config).isInstanceOf(UnspecifiedConfiguration::class.java)
    assertThat(config.traceType).isEqualTo(TraceType.UNSPECIFIED)
  }

  @Test
  fun addOptionsArtSampledConfigAddsSuccessfully() {
    val configBuilder = TraceConfiguration.getDefaultInstance().toBuilder()
    val artSampledConfiguration = ArtSampledConfiguration("MyConfiguration").apply {
      profilingSamplingIntervalUs = 1234
      profilingBufferSizeInMb = 5678
    }


    artSampledConfiguration.addOptions(configBuilder, emptyMap())
    val config = configBuilder.build()

    assertThat(config.hasArtOptions()).isTrue()
    assertThat(config.artOptions.traceMode).isEqualTo(TraceMode.SAMPLED)
    assertThat(config.artOptions.samplingIntervalUs).isEqualTo(1234)
    assertThat(config.artOptions.bufferSizeInMb).isEqualTo(5678)
  }

  @Test
  fun addOptionsArtInstrumentedConfigAddsSuccessfully() {
    val configBuilder = TraceConfiguration.getDefaultInstance().toBuilder()
    val artInstrumentedConfiguration = ArtInstrumentedConfiguration("MyConfiguration").apply {
      profilingBufferSizeInMb = 1234
    }

    artInstrumentedConfiguration.addOptions(configBuilder, emptyMap())
    val config = configBuilder.build()

    assertThat(config.hasArtOptions()).isTrue()
    assertThat(config.artOptions.traceMode).isEqualTo(TraceMode.INSTRUMENTED)
    assertThat(config.artOptions.samplingIntervalUs).isEqualTo(0)
    assertThat(config.artOptions.bufferSizeInMb).isEqualTo(1234)
  }

  @Test
  fun addOptionsAtraceConfigAddsSuccessfully() {
    val configBuilder = TraceConfiguration.getDefaultInstance().toBuilder()
    val atraceConfiguration = AtraceConfiguration("MyConfiguration")

    atraceConfiguration.addOptions(configBuilder, emptyMap())
    val config = configBuilder.build()

    assertThat(config.hasAtraceOptions()).isTrue()
    assertThat(config.atraceOptions.bufferSizeInMb).isEqualTo(SYSTEM_TRACE_BUFFER_SIZE_MB)
  }

  @Test
  fun addOptionsSimpleperfConfigAddsSuccessfully() {
    val configBuilder = TraceConfiguration.getDefaultInstance().toBuilder()
    val simpleperfConfiguration = SimpleperfConfiguration("MyConfiguration").apply {
      profilingSamplingIntervalUs = 1234
    }

    simpleperfConfiguration.addOptions(configBuilder, mapOf(AdditionalOptions.SYMBOL_DIRS to listOf("foo", "bar")))
    val config = configBuilder.build()

    assertThat(config.hasSimpleperfOptions()).isTrue()
    assertThat(config.simpleperfOptions.samplingIntervalUs).isEqualTo(1234)
    assertThat(config.simpleperfOptions.symbolDirsList).isEqualTo(listOf("foo", "bar"))
  }

  @Test
  fun addOptionsPerfettoConfigAddsSuccessfully() {
    val configBuilder = TraceConfiguration.getDefaultInstance().toBuilder()
    val perfettoConfiguration = PerfettoConfiguration("MyConfiguration")

    perfettoConfiguration.addOptions(configBuilder, mapOf(AdditionalOptions.APP_PKG_NAME to "foo"))
    val config = configBuilder.build()

    assertThat(config.hasPerfettoOptions()).isTrue()

    // Check that right amount of buffers and data sources were added.
    // More robust testing for the actual construction of the TraceConfig is found in {@link PerfettoTraceConfigBuildersTest}
    assertThat(config.perfettoOptions.buffersCount).isEqualTo(2)
    assertThat(config.perfettoOptions.dataSourcesCount).isEqualTo(8)

    // Ensure the additional options were added.
    val actualDataSources = config.perfettoOptions.dataSourcesList
    assertThat(actualDataSources[0].config.perfEventConfig.targetCmdlineList).containsExactly("foo")
  }

  @Test
  fun addOptionsUnspecifiedConfigAddsNothing() {
    val configBuilder = TraceConfiguration.getDefaultInstance().toBuilder()
    val unspecifiedConfiguration = UnspecifiedConfiguration("MyConfiguration");

    unspecifiedConfiguration.addOptions(configBuilder, emptyMap())
    val config = configBuilder.build()

    assertThat(config.hasArtOptions()).isFalse()
    assertThat(config.hasAtraceOptions()).isFalse()
    assertThat(config.hasSimpleperfOptions()).isFalse()
    assertThat(config.hasPerfettoOptions()).isFalse()
  }

  @Test
  fun addOptionsImportedConfigAddsNothing() {
    val configBuilder = TraceConfiguration.getDefaultInstance().toBuilder()
    val importedConfiguration = ImportedConfiguration();

    importedConfiguration.addOptions(configBuilder, emptyMap())
    val config = configBuilder.build()

    assertThat(config.hasArtOptions()).isFalse()
    assertThat(config.hasAtraceOptions()).isFalse()
    assertThat(config.hasSimpleperfOptions()).isFalse()
    assertThat(config.hasPerfettoOptions()).isFalse()
  }
}