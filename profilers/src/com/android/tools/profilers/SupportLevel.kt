/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.profiler.proto.Common.Process.ExposureLevel
import com.android.tools.profilers.SupportLevel.Feature.*
import com.android.tools.profilers.cpu.CpuProfiler
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.memory.MemoryProfiler
import com.android.tools.profilers.memory.MainMemoryProfilerStage

/**
 * Profiler's support level depending on a process's exposure level.
 * For example, support is `DEBUGGABLE` for debuggable processes, and `PROFILEABLE` for profileable processes.
 */
enum class SupportLevel(private val enablesMonitor: (Class<out StudioProfiler>) -> Boolean,
                        private val enablesStage: (Class<out Stage<*>>) -> Boolean,
                        private val enablesFeature: (Feature) -> Boolean) {
  NONE(none(), none(), none()),
  PROFILEABLE(only(CpuProfiler::class.java,
                   MemoryProfiler::class.java),
              only(CpuProfilerStage::class.java,
                   MainMemoryProfilerStage::class.java),
              except(MEMORY_HEAP_DUMP,
                     MEMORY_JVM_RECORDING,
                     MEMORY_GC,
                     EVENT_MONITOR)),
  DEBUGGABLE(all(), all(), all());

  fun isMonitorSupported(profiler: StudioProfiler) = enablesMonitor(profiler::class.java)
  fun isStageSupported(stage: Class<out Stage<*>>) = enablesStage(stage)
  fun isFeatureSupported(feature: Feature) = enablesFeature(feature)

  /**
   * Features that may or may not be supported depending on exposure level
   *
   * API-initiated CPU tracing doesn't need to be listed here because its entry point
   * is in the app code, not Studio UI
   */
  enum class Feature(val title: String) {
    MEMORY_HEAP_DUMP("Heap dump capturing"),
    MEMORY_NATIVE_RECORDING("Native allocation recording"),
    MEMORY_JVM_RECORDING("Java / Kotlin allocation recording"),
    MEMORY_GC("Forcing garbage collection"),
    EVENT_MONITOR("Event monitor"),
  }

  companion object {
    const val DOC_LINK = "https://d.android.com/r/studio-ui/profiler/profileable"

    @JvmStatic
    fun of(level: ExposureLevel) = when (level) {
      ExposureLevel.UNKNOWN, ExposureLevel.RELEASE, ExposureLevel.UNRECOGNIZED -> NONE
      ExposureLevel.PROFILEABLE -> PROFILEABLE
      ExposureLevel.DEBUGGABLE -> DEBUGGABLE
    }
  }
}

private fun<T> all(): (T) -> Boolean = { true }
private fun<T> none(): (T) -> Boolean = { false }
private fun<T> only(vararg included: T): (T) -> Boolean = included.toSet().let { included -> { it in included } }
private fun<T> except(vararg excluded: T): (T) -> Boolean = excluded.toSet().let { excluded -> { it !in excluded } }