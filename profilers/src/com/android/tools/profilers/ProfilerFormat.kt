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

import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType
import com.android.tools.profilers.tasks.ProfilerTaskType

sealed interface ProfilerFormat {
  val extensions: List<String>
  val taskType: ProfilerTaskType?

  fun matches(extension: String?, traceTypeProvider: Lazy<TraceType?>): Boolean

  object Hprof : ProfilerFormat {
    override val extensions = listOf("hprof", "prof", "perfetto-java-heap-dump")
    override val taskType = ProfilerTaskType.HEAP_DUMP

    override fun matches(extension: String?, traceTypeProvider: Lazy<TraceType?>) = extension?.lowercase() in extensions
  }

  object NativeAllocations : ProfilerFormat {
    override val extensions = listOf("heapprofd")
    override val taskType = ProfilerTaskType.NATIVE_ALLOCATIONS

    override fun matches(extension: String?, traceTypeProvider: Lazy<TraceType?>) = extension?.lowercase() in extensions
  }

  object JavaKotlinLegacyAllocations : ProfilerFormat {
    override val extensions = listOf("alloc")
    override val taskType = ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS

    override fun matches(extension: String?, traceTypeProvider: Lazy<TraceType?>) = extension?.lowercase() in extensions
  }

  object ArtTrace : ProfilerFormat {
    override val extensions = listOf("trace")
    override val taskType = ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING

    override fun matches(extension: String?, traceTypeProvider: Lazy<TraceType?>) =
      extension?.lowercase() in extensions && traceTypeProvider.value == TraceType.ART
  }

  object SimplePerfTrace : ProfilerFormat {
    override val extensions = listOf("trace")
    override val taskType = ProfilerTaskType.CALLSTACK_SAMPLE

    override fun matches(extension: String?, traceTypeProvider: Lazy<TraceType?>) =
      extension?.lowercase() in extensions && traceTypeProvider.value == TraceType.SIMPLEPERF
  }

  object PerfettoTrace : ProfilerFormat {
    override val extensions = listOf("trace", "pftrace", "perfetto-trace", "perfetto")
    override val taskType = ProfilerTaskType.SYSTEM_TRACE

    override fun matches(extension: String?, traceTypeProvider: Lazy<TraceType?>): Boolean {
      val ext = extension?.lowercase()
      if (ext in listOf("pftrace", "perfetto-trace", "perfetto")) {
        return true
      }
      return ext in extensions && (traceTypeProvider.value == TraceType.PERFETTO || traceTypeProvider.value == TraceType.ATRACE)
    }
  }

  companion object {
    private val ALL_FORMATS = listOf(Hprof, NativeAllocations, JavaKotlinLegacyAllocations, ArtTrace, SimplePerfTrace, PerfettoTrace)

    fun find(extension: String?, traceTypeProvider: Lazy<TraceType?>): ProfilerFormat? {
      return ALL_FORMATS.firstOrNull { it.matches(extension, traceTypeProvider) }
    }

    fun isMemoryFormat(format: ProfilerFormat?): Boolean {
      return format == Hprof || format == NativeAllocations || format == JavaKotlinLegacyAllocations
    }
  }
}
