/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType
import perfetto.protos.PerfettoConfig

object TraceConfigOptionsUtils {

  /**
   * Helper function to add default trace options to the TraceConfiguration
   * based on the type of trace/trace technology being configured.
   */
  fun addDefaultTraceOptions(configBuilder: Trace.TraceConfiguration.Builder, traceType: TraceType) {
    when (traceType) {
      TraceType.ART -> {
        configBuilder.artOptions = Trace.ArtOptions.getDefaultInstance()
      }

      TraceType.ATRACE -> {
        configBuilder.atraceOptions = Trace.AtraceOptions.getDefaultInstance()
      }

      TraceType.SIMPLEPERF -> {
        configBuilder.simpleperfOptions = Trace.SimpleperfOptions.getDefaultInstance()
      }

      TraceType.PERFETTO -> {
        configBuilder.perfettoOptions = PerfettoConfig.TraceConfig.getDefaultInstance()
      }
    }
  }
}