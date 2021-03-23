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
package com.android.tools.idea.appinspection.inspectors.workmanager.model

import androidx.work.inspection.WorkManagerInspectorProtocol.WorkInfo
import javax.swing.SwingUtilities

typealias WorkSelectionListener = (work: WorkInfo?, context: WorkSelectionModel.Context) -> Unit

/**
 * This class represents the status of current selected work and dispatches events
 * to its listeners accordingly.
 */
class WorkSelectionModel {
  enum class Context {
    DEVICE, // a work is selected with updates from the device e.g. state updates for the selected work and other works in the chain.
    TABLE,
    GRAPH,
    DETAILS,
    TOOLBAR
  }

  var selectedWork: WorkInfo? = null
    private set

  private var listeners = mutableListOf<WorkSelectionListener>()
  private var valueIsAdjusting = false

  /**
   * Selects a new [WorkInfo] with [Context].
   *
   * This method always triggers all [listeners] even if the selected work hasn't changed
   * because there could be chaining updates for the work with DEVICE context.
   * For example, another work in the same chain with [selectedWork] could modify its state
   * from RUNNING to FAILED and update the details panel for [selectedWork].
   */
  fun setSelectedWork(work: WorkInfo?, context: Context) {
    check(SwingUtilities.isEventDispatchThread())
    check(!valueIsAdjusting) { "Don't call setSelectedWork from a listener callback" }
    try {
      valueIsAdjusting = true // Prevent listeners from calling us recursively
      selectedWork = work
      listeners.forEach { it(work, context) }
    }
    finally {
      valueIsAdjusting = false
    }
  }

  /**
   * Registers a [WorkSelectionListener] when [setSelectedWork] is called.
   */
  fun registerWorkSelectionListener(listener: WorkSelectionListener) {
    listeners.add(listener)
  }
}
