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
package com.android.tools.idea.run.profiler

import com.google.wireless.android.sdk.stats.RunWithProfilingMetadata
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.executors.RunExecutorSettings
import com.intellij.execution.impl.DefaultExecutorGroup

/**
 * Abstract class for the Profiler executor group, useful for looking up its registered settings by executor ID, without knowledge of
 * profiler implementation details.
 */
abstract class AbstractProfilerExecutorGroup<T : AbstractProfilerExecutorGroup.AbstractProfilerSetting> : DefaultExecutorGroup<T>() {
  /**
   * Setting for individual executors in this group. Subclasses should provide concrete implementation of the [RunExecutorSettings] methods.
   */
  abstract class AbstractProfilerSetting(val profilingMode: ProfilingMode) : RunExecutorSettings

  companion object {
    const val EXECUTOR_ID = "Android Profiler Group"

    /**
     * Flag name for setting profiling mode in the build system.
     */
    const val PROFILING_MODE_PROPERTY_NAME = "android.profilingMode"

    fun getInstance(): AbstractProfilerExecutorGroup<*>? {
      return ExecutorRegistry.getInstance().getExecutorById(EXECUTOR_ID) as? AbstractProfilerExecutorGroup<*>
    }
  }
}

/**
 * When set, the profiling mode is passed to Android Gradle Plugin when building the app. Supported on AGP 7.3.0+.
 *
 * @param shouldInjectProjectProperty true if the mode should inject project property override to the build system.
 * @param analyticsProtoType metadata for analytics tracking.
 */
enum class ProfilingMode(val value: String,
                         val shouldInjectProjectProperty: Boolean,
                         val analyticsProtoType: RunWithProfilingMetadata.ProfilingMode) {
  NOT_SET("", false, RunWithProfilingMetadata.ProfilingMode.UNKNOWN_PROFILING_MODE),
  DEBUGGABLE("debuggable", true, RunWithProfilingMetadata.ProfilingMode.DEBUGGABLE),
  PROFILEABLE("profileable", true, RunWithProfilingMetadata.ProfilingMode.PROFILEABLE);
}
