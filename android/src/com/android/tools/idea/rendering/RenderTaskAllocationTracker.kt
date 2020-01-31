/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.rendering

import com.android.tools.idea.ui.GuiTestingService
import com.intellij.openapi.application.ApplicationManager
import java.util.WeakHashMap

// We only track allocations in testing mode
private val shouldTrackAllocations = GuiTestingService.getInstance()?.isGuiTestingMode == true ||
                                     ApplicationManager.getApplication()?.isUnitTestMode == true
private val allocations = WeakHashMap<RenderTask, AllocationStackTrace>()

/**
 * Data class that represents a [RenderTask] allocation point.
 */
data class AllocationStackTrace(internal val stackTrace: List<StackTraceElement>) {
  fun bind(renderTask: RenderTask) {
    if (!shouldTrackAllocations) return
    allocations[renderTask] = this
  }
}

/**
 * Singleton empty stack trace used when allocation tracking is disabled to avoid unnecessary allocations.
 */
private val NULL_STACK_TRACE = AllocationStackTrace(listOf())

/**
 * Resets the existing tracked allocation
 */
fun clearTrackedAllocations() {
  if (shouldTrackAllocations) allocations.clear()
}

fun notDisposedRenderTasks(): Sequence<List<StackTraceElement>> {
  if (!shouldTrackAllocations) emptySequence<StackTraceElement>()

  return allocations.asSequence()
    .filter { (task, _) ->
      task != null && !task.isDisposed
    }.map { (_, trace) ->
      trace.stackTrace
    }
}

/**
 * Captures a [RenderTask] allocation point to be used later in the constructor.
 */
fun captureAllocationStackTrace(): AllocationStackTrace =
  if (shouldTrackAllocations) {
    // Capture the current stack trace dropping the allocation point stack frame, one for captureAllocationStackTrace and one for getTrace
    AllocationStackTrace(Thread.currentThread()!!.stackTrace!!.asList().drop(2))
  }
  else {
    NULL_STACK_TRACE
  }
