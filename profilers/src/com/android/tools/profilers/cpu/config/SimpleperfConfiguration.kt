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

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.model.options.OptionsProperty
import com.android.tools.profiler.proto.Trace

/**
 * Simple perf configuration
 */
class SimpleperfConfiguration(name: String) : ProfilingConfiguration(name) {
  /**
   * Sampling interval (for sample-based profiling) in microseconds.
   */
  @OptionsProperty(name = "Sample interval: ", group = TRACE_CONFIG_GROUP, order = 100, unit = "Us (Microseconds)")
  var profilingSamplingIntervalUs = DEFAULT_SAMPLING_INTERVAL_US

  var symbolDirs: List<String> = listOf()

  override fun buildUserOptions(): Trace.UserOptions.Builder {
    return Trace.UserOptions.newBuilder()
      .setTraceMode(Trace.TraceMode.SAMPLED)
      .setSamplingIntervalUs(profilingSamplingIntervalUs)
  }

  override fun getOptions(): Trace.SimpleperfOptions {
    return Trace.SimpleperfOptions.newBuilder()
      .setSamplingIntervalUs(profilingSamplingIntervalUs)
      .addAllSymbolDirs(symbolDirs)
      .build()
  }

  override fun addOptions(configBuilder: Trace.TraceConfiguration.Builder) {
    configBuilder.simpleperfOptions = options
  }

  override fun getTraceType(): Trace.UserOptions.TraceType {
    return Trace.UserOptions.TraceType.SIMPLEPERF
  }

  override fun getRequiredDeviceLevel(): Int {
    return AndroidVersion.VersionCodes.O;
  }
}