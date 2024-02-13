/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.liveedit

import com.android.tools.idea.editors.liveedit.LiveEditService.Companion.LiveEditTriggerMode.AUTOMATIC

import com.android.tools.idea.editors.liveedit.LiveEditService.Companion.LiveEditTriggerMode.LE_TRIGGER_MANUAL
import com.android.tools.idea.editors.liveedit.LiveEditService.Companion.LiveEditTriggerMode.LE_TRIGGER_AUTOMATIC
import com.android.tools.idea.editors.liveedit.LiveEditService.Companion.LiveEditTriggerMode.ON_HOTKEY
import com.android.tools.idea.editors.liveedit.LiveEditService.Companion.LiveEditTriggerMode.ON_SAVE
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration.LiveEditMode.DISABLED
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration.LiveEditMode.LIVE_EDIT
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration.LiveEditMode.LIVE_LITERALS
import com.android.tools.idea.flags.StudioFlags
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.ProjectManager

@com.intellij.openapi.components.State(name = "LiveEditConfiguration", storages = [(Storage(StoragePathMacros.NON_ROAMABLE_FILE))])
@Service
class LiveEditApplicationConfiguration : SimplePersistentStateComponent<LiveEditApplicationConfiguration.State>(State()) {
  enum class LiveEditMode {
    DISABLED,
    LIVE_LITERALS, // Legacy do not use.
    LIVE_EDIT
  }

  class State : BaseState() {
    // Enabled by default only in Dev and Canary.
    var mode by enum(if (StudioFlags.LIVE_EDIT_ENABLE_BY_DEFAULT.get()) LIVE_EDIT else DISABLED)
    var leTriggerMode by enum(ON_HOTKEY)
  }

  var mode
    get() = if (state.mode == LIVE_LITERALS) DISABLED else state.mode
    set(value) {
      var patchedValue = value
      if (patchedValue == LIVE_LITERALS) {
        patchedValue = DISABLED
      }
      if (state.mode != patchedValue) {
        ProjectManager.getInstance().openProjects
          .forEach {
            LiveEditService.getInstance(it).toggleLiveEdit(state.mode, patchedValue)
          }
        state.mode = patchedValue

        // Force the UI to redraw with the new status. See com.intellij.openapi.actionSystem.AnAction#update().
        ActivityTracker.getInstance().inc()
      }
    }

  // Live Edit Trigger Mode
  var leTriggerMode
    get() = when (state.leTriggerMode) {
      // Patch up the legacy settings.
      LE_TRIGGER_MANUAL -> ON_SAVE // The LE_TRIGGER_MANUAL in G will behaves like ON_SAVE
      LE_TRIGGER_AUTOMATIC -> AUTOMATIC // LE_TRIGGER_AUTOMATIC will just be AUTOMATIC
      else -> state.leTriggerMode
    }

    set(value) {
        ProjectManager.getInstance().openProjects
          .forEach {
            LiveEditService.getInstance(it).toggleLiveEditMode(state.leTriggerMode, value)
          }
        state.leTriggerMode = value
    }

  val isLiveEdit
    get() = mode == LIVE_EDIT

  companion object {
    @JvmStatic
    fun getInstance(): LiveEditApplicationConfiguration = ApplicationManager.getApplication().getService(
      LiveEditApplicationConfiguration::class.java)
  }
}