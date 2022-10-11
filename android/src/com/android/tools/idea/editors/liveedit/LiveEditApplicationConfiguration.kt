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

import com.android.tools.idea.editors.literals.LiveEditService
import com.android.tools.idea.editors.literals.internal.LiveLiteralsDiagnosticsManager
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration.LiveEditMode.LIVE_EDIT
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration.LiveEditMode.LIVE_LITERALS
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import org.jetbrains.annotations.TestOnly

@com.intellij.openapi.components.State(name = "LiveEditConfiguration", storages = [(Storage(StoragePathMacros.NON_ROAMABLE_FILE))])
@Service
class LiveEditApplicationConfiguration : SimplePersistentStateComponent<LiveEditApplicationConfiguration.State>(State()) {
  enum class LiveEditMode {
    DISABLED,
    LIVE_LITERALS,
    LIVE_EDIT
  }

  class State : BaseState() {
    var mode by enum(LIVE_LITERALS)
    var leTriggerMode by enum(LiveEditService.Companion.LiveEditTriggerMode.LE_TRIGGER_MANUAL)
  }

  var mode
    get() = if (state.mode == LIVE_EDIT && !StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT.get()) LIVE_LITERALS else state.mode
    set(value) {
      var patchedValue = value
      if (patchedValue == LIVE_EDIT && !StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT.get()) {
        patchedValue = LIVE_LITERALS
      }
      if (state.mode != patchedValue) {
        state.mode = patchedValue
        LiveLiteralsDiagnosticsManager.getApplicationWriteInstance().userChangedLiveLiteralsState(patchedValue == LIVE_LITERALS)
      }
    }

  // Live Edit Trigger Mode
  var leTriggerMode
    get() = state.leTriggerMode
    set(value) {
        state.leTriggerMode = value
        LiveEditService.bindKeyMapShortcut(value)
    }

  /**
   * True if the Live Literals feature is enabled. This is an application wide property that allows the user to check if the feature
   * is enabled. To set the underlying setting to enable Live Literals feature, change [mode] to [LIVE_LITERALS].
   * This does not indicate if the current project has Live Literals available. For that, check [LiveLiteralsService.isAvailable].
   */
  val isLiveLiterals
    get() = mode == LIVE_LITERALS

  val isLiveEdit
    get() = mode == LIVE_EDIT

  @TestOnly
  fun resetDefault() {
    state.mode = LIVE_LITERALS
  }

  companion object {
    @JvmStatic
    fun getInstance(): LiveEditApplicationConfiguration = ApplicationManager.getApplication().getService(
      LiveEditApplicationConfiguration::class.java)
  }
}