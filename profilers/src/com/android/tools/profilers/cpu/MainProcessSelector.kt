/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.profilers.IdeProfilerServices
import com.android.tools.profilers.cpu.atrace.CpuThreadSliceInfo
import java.util.function.Function
import kotlin.RuntimeException as RuntimeException1

/**
 * Selects the main process from a list of process, using a number of optional methods: name, id, IDE dialog for the user.
 * Used on capture/tracing technologies that supports multiple processes, in order to select the one which data will be based on.
 *
 * <p>Can return null if not process were found with the methods/hints passed.
 */
class MainProcessSelector(
  val nameHint: String?,
  val idHint: Int?,
  private val profilerServices: IdeProfilerServices?): Function<List<CpuThreadSliceInfo>, Int?> {

  override fun apply(processList: List<CpuThreadSliceInfo>): Int? {
    // 1) Use name hint if available.
    if (nameHint != null) {
      processList.find { nameHint.endsWith(it.processName) }?.let { return it.processId }
    }

    // 2) If we don't have a process based on named find one based on id.
    if (idHint != null && idHint > 0) {
      processList.find { idHint == it.processId }?.let { return it.processId }
    }

    // 3) Ask the user for input.
    if (profilerServices != null) {
      val selection = profilerServices.openListBoxChooserDialog("Select a process",
                                                "Select the process you want to analyze.",
                                                processList,
                                                Function { t: CpuThreadSliceInfo -> t.processName })
      if (selection != null) {
        return selection.processId
      } else {
        throw ProcessSelectorDialogAbortedException()
      }
    }

    // 4) Fallback to the first of the list, we know it has at least one.
    return processList.firstOrNull()?.processId
  }
}

/**
 * Exception thrown when the user aborts the process selection dialog.
 */
class ProcessSelectorDialogAbortedException : RuntimeException()