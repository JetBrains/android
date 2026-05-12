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
package com.android.tools.profilers.taskbased.common.constants.strings

import com.android.tools.profilers.tasks.ProfilerTaskType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class StringUtilsTest(private val taskType: ProfilerTaskType) {

  companion object {
    @JvmStatic @Parameterized.Parameters fun data() = ProfilerTaskType.values()
  }

  @Test
  fun testGettingTaskTabTitle() {
    val taskTabTitle = StringUtils.getTaskTabTitle(taskType, false)
    when (taskType) {
      ProfilerTaskType.SYSTEM_TRACE -> assertEquals(taskTabTitle, "Capture System Activities (System Trace)")
      ProfilerTaskType.HEAP_DUMP -> assertEquals(taskTabTitle, "Analyze Memory Usage (Heap Dump)")
      ProfilerTaskType.CALLSTACK_SAMPLE -> assertEquals(taskTabTitle, "Find CPU Hotspots (Callstack Sample)")
      ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS -> assertEquals(taskTabTitle, "Track Memory Consumption (Java/Kotlin Allocations)")
      ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING -> assertEquals(taskTabTitle, "Find CPU Hotspots (Java/Kotlin Method Recording)")
      ProfilerTaskType.NATIVE_ALLOCATIONS -> assertEquals(taskTabTitle, "Track Memory Consumption (Native Allocations)")
      ProfilerTaskType.LIVE_VIEW -> assertEquals(taskTabTitle, "View Live Telemetry")
      ProfilerTaskType.LEAKCANARY -> assertEquals(taskTabTitle, "Find Memory Leaks with LeakCanary")
      ProfilerTaskType.UNSPECIFIED -> assertEquals(taskTabTitle, "Task not supported yet")
    }
  }

  @Test
  fun testGettingTaskTabTitleV2() {
    val taskTabTitle = StringUtils.getTaskTabTitle(taskType, true)
    when (taskType) {
      ProfilerTaskType.SYSTEM_TRACE -> assertEquals("System Trace", taskTabTitle)
      ProfilerTaskType.HEAP_DUMP -> assertEquals("Heap Dump", taskTabTitle)
      ProfilerTaskType.CALLSTACK_SAMPLE -> assertEquals("Stack Sampling", taskTabTitle)
      ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS -> assertEquals("Java/Kotlin Allocations", taskTabTitle)
      ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING -> assertEquals("Method Trace", taskTabTitle)
      ProfilerTaskType.NATIVE_ALLOCATIONS -> assertEquals("Native Allocations", taskTabTitle)
      ProfilerTaskType.LIVE_VIEW -> assertEquals("Live Telemetry", taskTabTitle)
      ProfilerTaskType.LEAKCANARY -> assertEquals("LeakCanary", taskTabTitle)
      ProfilerTaskType.UNSPECIFIED -> assertEquals("", taskTabTitle)
    }
  }
}
