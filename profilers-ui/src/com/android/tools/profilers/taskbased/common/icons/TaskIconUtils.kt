/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.profilers.taskbased.common.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.android.tools.profilers.tasks.ProfilerTaskType
import icons.StudioIcons
import icons.StudioIconsCompose
import javax.swing.Icon

object TaskIconUtils {
  /**
   * Return the corresponding large task icon painter for a task type. To be used in the task selection grid.
   */
  @Composable
  fun getLargeTaskIconPainter(taskType: ProfilerTaskType): Painter {
    val resourcePainterProvider = when (taskType) {
      ProfilerTaskType.UNSPECIFIED -> throw IllegalStateException("No task icon is available for the UNSPECIFIED task type.")
      ProfilerTaskType.CALLSTACK_SAMPLE -> StudioIconsCompose.Profiler.Taskslarge.CallstackSampleLarge()
      ProfilerTaskType.SYSTEM_TRACE -> StudioIconsCompose.Profiler.Taskslarge.SystemTraceLarge()
      ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING -> StudioIconsCompose.Profiler.Taskslarge.JavaKotlinMethodTraceLarge()
      ProfilerTaskType.HEAP_DUMP -> StudioIconsCompose.Profiler.Taskslarge.HeapDumpLarge()
      ProfilerTaskType.NATIVE_ALLOCATIONS -> StudioIconsCompose.Profiler.Taskslarge.NativeAllocationsLarge()
      ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS -> StudioIconsCompose.Profiler.Taskslarge.JavaKotlinAllocationsLarge()
      ProfilerTaskType.LIVE_VIEW -> StudioIconsCompose.Profiler.Taskslarge.LiveViewLarge()
    }
    return resourcePainterProvider.getPainter().value
  }

  /**
   * Utility to fetch the corresponding task icon for a task type. To be used for the task tab icon.
   */
  fun getTaskIcon(taskType: ProfilerTaskType): Icon {
    return when (taskType) {
      ProfilerTaskType.UNSPECIFIED -> throw IllegalStateException("No task icon is available for the UNSPECIFIED task type.")
      ProfilerTaskType.CALLSTACK_SAMPLE -> StudioIcons.Profiler.Tasks.CALLSTACK_SAMPLE
      ProfilerTaskType.SYSTEM_TRACE -> StudioIcons.Profiler.Tasks.SYSTEM_TRACE
      ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING -> StudioIcons.Profiler.Tasks.JAVA_KOTLIN_METHOD_TRACE
      ProfilerTaskType.HEAP_DUMP -> StudioIcons.Profiler.Tasks.HEAP_DUMP
      ProfilerTaskType.NATIVE_ALLOCATIONS -> StudioIcons.Profiler.Tasks.NATIVE_ALLOCATIONS
      ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS -> StudioIcons.Profiler.Tasks.JAVA_KOTLIN_ALLOCATIONS
      ProfilerTaskType.LIVE_VIEW -> StudioIcons.Profiler.Tasks.LIVE_VIEW
    }
  }
}