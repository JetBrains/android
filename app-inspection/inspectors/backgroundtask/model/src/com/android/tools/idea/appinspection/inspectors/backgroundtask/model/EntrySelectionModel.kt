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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.model

import androidx.work.inspection.WorkManagerInspectorProtocol
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.BackgroundTaskEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WorkEntry

typealias EntrySelectionListener = (BackgroundTaskEntry?) -> Unit

/**
 * This class represents the status of current selected entry and dispatches events to its listeners
 * accordingly.
 */
class EntrySelectionModel {
  var selectedEntry: BackgroundTaskEntry? = null
    set(entry) {
      if (entry != field) {
        field = entry
        listeners.forEach { it(entry) }
      }
    }

  private var listeners = mutableListOf<EntrySelectionListener>()

  /** Registers a [EntrySelectionListener] for [selectedEntry] updates. */
  fun registerEntrySelectionListener(listener: EntrySelectionListener) {
    listeners.add(listener)
  }

  val selectedWork: WorkManagerInspectorProtocol.WorkInfo?
    get() = (selectedEntry as? WorkEntry)?.getWorkInfo()
}
