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
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.Trace

/**
 * Perfetto configuration. This is used internally to differentiate the perfetto config from the atrace config.
 * Exposed properties only from {@link AtraceConfiguration}
 */
class PerfettoConfiguration(name: String) : AtraceConfiguration(name) {
  override fun getTraceType(): Trace.UserOptions.TraceType {
    return Trace.UserOptions.TraceType.PERFETTO
  }

  override fun getRequiredDeviceLevel(): Int {
    return AndroidVersion.VersionCodes.P;
  }
}