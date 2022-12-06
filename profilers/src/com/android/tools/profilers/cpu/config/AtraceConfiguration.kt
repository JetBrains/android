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
import com.android.tools.profiler.proto.Trace

/**
 * Configuration for ATrace traces.
 */
class AtraceConfiguration(name: String) : ProfilingConfiguration(name) {
  override fun getOptions(): Trace.AtraceOptions {
    return Trace.AtraceOptions.newBuilder()
      .setBufferSizeInMb(SYSTEM_TRACE_BUFFER_SIZE_MB)
      .build()
  }

  override fun addOptions(configBuilder: Trace.TraceConfiguration.Builder, additionalOptions: Map<AdditionalOptions, Any>) {
    configBuilder.atraceOptions = options
  }

  override fun getTraceType(): TraceType {
    return TraceType.ATRACE
  }

  override fun getRequiredDeviceLevel(): Int {
    return AndroidVersion.VersionCodes.N;
  }
}