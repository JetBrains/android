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
package com.android.tools.profilers

import com.android.tools.adtui.model.DataSeries
import com.android.tools.adtui.model.updater.Updater
import com.android.tools.profiler.proto.ProfilerTaskMetadataProto.ProfilerTaskMetadata
import com.android.tools.profilers.cpu.CpuCapture

/**
 * Defines the explicit bounds of dependencies that Stages and components are permitted to use. This abstracts away the heavy
 * [com.android.tools.profilers.StudioProfilers] object, allowing the stage to be instantiated in isolated environments like the Unified
 * Profiler File Editor.
 */
interface ProfilerContext {
  val taskMetadata: ProfilerTaskMetadata?
    get() = null

  val ideProfilerServices: IdeProfilerServices

  val updater: Updater

  val isJvmtiEnabled: Boolean

  /** Optionally provide a specific parent stage for UI navigation. */
  val parentStage: Stage<*>?
    get() = null

  /** Optionally provide a specific home stage class for UI navigation. */
  val homeStageClass: Class<out Stage<*>>?
    get() = null

  /** Provides usage data series. Extracted here to avoid needing the gRPC TransportService. */
  fun getDataSeries(capture: CpuCapture?): DataSeries<Long>

  /** Called when a capture fails to parse. Implementations handle UI navigation/error reporting. */
  fun onParseFailure(message: String)

  /**
   * Called to report that an imported trace has been parsed. Implementations handle database event injection or other tracking as needed.
   */
  fun reportParsedTrace(capture: CpuCapture)
}
