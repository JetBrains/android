/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.adtui.model.options.Dropdown
import com.android.tools.adtui.model.options.OptionsProperty
import com.android.tools.profiler.proto.Commands.StartLeakCanaryTaskData
import com.android.tools.profiler.proto.Trace

enum class LeakCanaryMode(private val description: String) {
  STUDIO("Use Android Studio Settings"),
  NATIVE("Use configuration defined in app code");

  override fun toString() = description
}

class LeakCanaryConfiguration(name: String) : ProfilingConfiguration(name) {

  val mode: StartLeakCanaryTaskData.LeakCanaryMode
    get() =
      when (source) {
        LeakCanaryMode.STUDIO -> StartLeakCanaryTaskData.LeakCanaryMode.ON_HOST
        LeakCanaryMode.NATIVE -> StartLeakCanaryTaskData.LeakCanaryMode.ON_DEVICE
      }

  // Group "LC settings" creates the bold header
  @OptionsProperty(name = "Customization", group = "LC settings", order = 100) var source: LeakCanaryMode = LeakCanaryMode.STUDIO

  @OptionsProperty(
    name = "Trigger Heap Dump after",
    group = "LC settings",
    order = 101,
    unit = "Retained Objects",
    indent = true,
    description = "The minimum number of retained objects required to trigger a heap dump.",
    parent = "source",
    parentValue = "STUDIO",
  )
  @Dropdown(values = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20, 25, 30, 35, 40, 45, 50])
  var threshold: Int = 5

  override fun getDescription(propertyName: String, enumValue: Any): String? {
    if (propertyName == "source" && enumValue is LeakCanaryMode) {
      return when (enumValue) {
        LeakCanaryMode.STUDIO -> "Ignores your apps LeakCanary.Config or Shark customization logic."
        LeakCanaryMode.NATIVE -> "Uses the LeakCanary.Config or Shark customization logic currently running on the device."
      }
    }
    return null
  }

  override fun getTraceType(): TraceType {
    return TraceType.LEAKCANARY
  }

  override fun getRequiredDeviceLevel(): Int {
    return 0
  }

  override fun getOptions(): Trace.LeakCanaryOptions {
    val protoMode =
      when (source) {
        LeakCanaryMode.STUDIO -> Trace.LeakCanaryOptions.LeakCanaryMode.ON_HOST
        LeakCanaryMode.NATIVE -> Trace.LeakCanaryOptions.LeakCanaryMode.ON_DEVICE
      }

    return Trace.LeakCanaryOptions.newBuilder().setMode(protoMode).build()
  }

  override fun addOptions(configBuilder: Trace.TraceConfiguration.Builder, additionalOptions: Map<AdditionalOptions, Any>) {
    configBuilder.leakcanaryOptions = options
  }
}
