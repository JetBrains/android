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

import com.android.tools.adtui.model.options.OptionsProperty
import com.android.tools.adtui.model.options.Slider
import com.android.tools.profiler.proto.Trace
import com.android.tools.profiler.proto.Trace.TraceMode

/**
 * Configuration for art traces.
 */

class ArtInstrumentedConfiguration(name: String) : ProfilingConfiguration(name) {
  @OptionsProperty(name = "Enable dual clock (warning: slower performance)", group = TRACE_CONFIG_GROUP, order = 100,
                   description = "<html>When enabled, both thread-CPU and wall clock time are recorded, otherwise, only wall clock time is recorded. On Android 13 (API level 33) and below, this is always enabled regardless of selection.</html>")
  var dualClock = DEFAULT_DUAL_CLOCK_VALUE

  @Slider(min = 1, max = 32, step = 1)
  @OptionsProperty(group = TRACE_CONFIG_GROUP, order = 101, name = "File size limit:", unit = "Mb",
                description = "Maximum recording output file size. On Android 8.0 (API level 26) and higher, this value is ignored.")
  var profilingBufferSizeInMb = DEFAULT_BUFFER_SIZE_MB

  override fun getOptions(): Trace.ArtOptions {
    return Trace.ArtOptions.newBuilder()
      .setTraceMode(TraceMode.INSTRUMENTED)
      .setBufferSizeInMb(profilingBufferSizeInMb)
      .setDualClock(dualClock)
      .build()
  }

  override fun addOptions(configBuilder: Trace.TraceConfiguration.Builder, additionalOptions: Map<AdditionalOptions, Any>) {
    configBuilder.artOptions = options
  }

  override fun getTraceType(): TraceType {
    return TraceType.ART
  }

  override fun getRequiredDeviceLevel(): Int {
    return 0
  }
}