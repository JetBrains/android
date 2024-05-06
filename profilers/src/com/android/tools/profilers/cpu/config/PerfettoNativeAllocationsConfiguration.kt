/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.tools.profilers.perfetto.config.PerfettoTraceConfigBuilders
import perfetto.protos.PerfettoConfig

class PerfettoNativeAllocationsConfiguration(name: String) : PerfettoConfiguration(name) {
  @OptionsProperty(name = "Sample interval: ", group = TRACE_CONFIG_GROUP, order = 100, unit = "Bytes")
  var memorySamplingIntervalBytes = DEFAULT_MEMORY_SAMPLING_INTERVAL_BYTES

  override fun getOptions(): PerfettoConfig.TraceConfig {
    return PerfettoTraceConfigBuilders.getMemoryTraceConfig(name, memorySamplingIntervalBytes.toLong())
  }

  override fun getRequiredDeviceLevel(): Int {
    return AndroidVersion.VersionCodes.Q
  }
}