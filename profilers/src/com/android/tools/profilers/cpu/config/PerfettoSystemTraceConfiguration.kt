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
import com.android.tools.profilers.perfetto.config.PerfettoTraceConfigBuilders
import perfetto.protos.PerfettoConfig.TraceConfig

class PerfettoSystemTraceConfiguration(name: String, private val isTraceboxEnabled: Boolean) : PerfettoConfiguration(name) {
  override fun getOptions(): TraceConfig {
    return PerfettoTraceConfigBuilders.getCpuTraceConfig(SYSTEM_TRACE_BUFFER_SIZE_MB)
  }

  override fun getRequiredDeviceLevel(): Int = if (isTraceboxEnabled) AndroidVersion.VersionCodes.M else AndroidVersion.VersionCodes.P

  override fun isEditable(): Boolean = false
}