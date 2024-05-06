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
package com.android.tools.profilers.tasks

import com.android.tools.profiler.proto.Common
import com.intellij.util.containers.reverse

object TaskTypeMappingUtils {
  private val taskTypeMapping = mapOf(
    Common.ProfilerTaskType.UNSPECIFIED_TASK to ProfilerTaskType.UNSPECIFIED,
    Common.ProfilerTaskType.CALLSTACK_SAMPLE to ProfilerTaskType.CALLSTACK_SAMPLE,
    Common.ProfilerTaskType.SYSTEM_TRACE to ProfilerTaskType.SYSTEM_TRACE,
    Common.ProfilerTaskType.JAVA_KOTLIN_METHOD_TRACE to ProfilerTaskType.JAVA_KOTLIN_METHOD_TRACE,
    Common.ProfilerTaskType.JAVA_KOTLIN_METHOD_SAMPLE to ProfilerTaskType.JAVA_KOTLIN_METHOD_SAMPLE,
    Common.ProfilerTaskType.HEAP_DUMP to ProfilerTaskType.HEAP_DUMP,
    Common.ProfilerTaskType.NATIVE_ALLOCATIONS to ProfilerTaskType.NATIVE_ALLOCATIONS,
    Common.ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS to ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS,
    Common.ProfilerTaskType.LIVE_VIEW to ProfilerTaskType.LIVE_VIEW,
  )

  private val reverseTaskTypeMapping = taskTypeMapping.reverse()

  /**
   * Converts the proto-based ProfilerTaskType enum to the class-based ProfilerTaskType enum.
   */
  @JvmStatic
  fun convertTaskType(taskType: Common.ProfilerTaskType): ProfilerTaskType {
    return taskTypeMapping.getOrDefault(taskType, ProfilerTaskType.UNSPECIFIED)
  }

  /**
   * Converts the class-based ProfilerTaskType enum to the proto-based ProfilerTaskType enum.
   */
  @JvmStatic
  fun convertTaskType(taskType: ProfilerTaskType): Common.ProfilerTaskType {
    return reverseTaskTypeMapping.getOrDefault(taskType, Common.ProfilerTaskType.UNSPECIFIED_TASK)
  }
}