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
package com.android.tools.profilers.cpu

import com.android.tools.profilers.FeatureConfig
import com.android.tools.profilers.tasks.ProfilerTaskType

/** Utility functions for determining whether specific profiler tasks or trace types should open in the editor window. */
object ProfilerInEditorUtils {
  /**
   * Returns whether editor-based presentation is enabled for the specified [taskType].
   *
   * For [ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS], differentiates between historical legacy `.alloc` traces (governed by
   * [FeatureConfig.isJavaKotlinAllocationsLegacyTraceInEditorEnabled]) and live streaming allocations (governed by
   * [FeatureConfig.isJavaKotlinAllocationsInEditorEnabled]).
   */
  @JvmStatic
  @JvmOverloads
  fun isEditorEnabled(featureConfig: FeatureConfig, taskType: ProfilerTaskType, isLegacyAllocations: Boolean = false): Boolean {
    // Check if the editor feature is enabled for the current task type.
    return when (taskType) {
      ProfilerTaskType.SYSTEM_TRACE -> featureConfig.isSystemTraceInEditorEnabled
      ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING -> featureConfig.isMethodTraceInEditorEnabled
      ProfilerTaskType.CALLSTACK_SAMPLE -> featureConfig.isCallstackSampleTraceInEditorEnabled
      ProfilerTaskType.HEAP_DUMP -> featureConfig.isHeapDumpTraceInEditorEnabled
      ProfilerTaskType.NATIVE_ALLOCATIONS -> featureConfig.isNativeAllocationsTraceInEditorEnabled
      ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS ->
        if (isLegacyAllocations) featureConfig.isJavaKotlinAllocationsLegacyTraceInEditorEnabled
        else featureConfig.isJavaKotlinAllocationsInEditorEnabled
      ProfilerTaskType.LIVE_VIEW -> featureConfig.isLiveTelemetryInEditorEnabled
      ProfilerTaskType.LEAKCANARY -> featureConfig.isLeakCanaryInEditorEnabled
      else -> false
    }
  }

  /** Returns whether the given [taskType] is a live, streaming task that should open in the editor via `ProfilerVirtualFile`. */
  @JvmStatic
  @JvmOverloads
  fun isLiveTaskInEditorEnabled(featureConfig: FeatureConfig, taskType: ProfilerTaskType, isLegacyAllocations: Boolean = false): Boolean {
    return when (taskType) {
      ProfilerTaskType.LIVE_VIEW -> featureConfig.isLiveTelemetryInEditorEnabled
      ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS -> if (!isLegacyAllocations) featureConfig.isJavaKotlinAllocationsInEditorEnabled else false
      ProfilerTaskType.LEAKCANARY -> featureConfig.isLeakCanaryInEditorEnabled
      else -> false
    }
  }
}
