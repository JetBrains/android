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

import com.android.tools.idea.editors.literals.internal.LiveLiteralsDiagnosticsManager
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration.LiveEditMode.DISABLED
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
    var mode by enum(if (StudioFlags.COMPOSE_LIVE_LITERALS.get()) LIVE_LITERALS else DISABLED)
    var liveEditDeviceEnabled by property(true)
    var liveEditPreviewEnabled by property(StudioFlags.COMPOSE_FAST_PREVIEW.get())
  }

  var mode
    get() = state.mode
    set(value) {
      if (state.mode != value) {
        state.mode = value
        LiveLiteralsDiagnosticsManager.getApplicationWriteInstance().userChangedLiveLiteralsState(value == LIVE_LITERALS)
        if (value == LIVE_EDIT) {
          liveEditDeviceEnabled = state.liveEditDeviceEnabled
          liveEditPreviewEnabled = state.liveEditPreviewEnabled
        }
      }
    }

  /**
   * True if the Live Literals feature is enabled. This is an application wide property that allows the user to check if the feature
   * is enabled. To set the underlying setting to enable Live Literals feature, change [mode] to [LIVE_LITERALS].
   * This does not indicate if the current project has Live Literals available. For that, check [LiveLiteralsService.isAvailable].
   */
  val isLiveLiterals
    get() = mode == LIVE_LITERALS

  var liveEditDeviceEnabled
    get() = state.liveEditDeviceEnabled
    set(value) {
      if (state.liveEditDeviceEnabled != value) {
        state.liveEditDeviceEnabled = value
      }
    }

  val isLiveEdit
    get() = mode == LIVE_EDIT

  val isLiveEditDevice
    get() = mode == LIVE_EDIT && liveEditDeviceEnabled

  /**
   * True if the Live Literals feature is enabled. This is an application wide setting that allows the user to disable the feature.
   * This does not indicate if the current project has Live Literals available. For that, check [LiveLiteralsService.isAvailable].
   */
  var liveEditPreviewEnabled
    get() = StudioFlags.COMPOSE_FAST_PREVIEW.get() && state.liveEditPreviewEnabled
    set(value) {
      if (state.liveEditPreviewEnabled != value) {
        state.liveEditPreviewEnabled = value
      }
    }

  val isLiveEditPreview
    get() = mode == LIVE_EDIT && liveEditPreviewEnabled

  @TestOnly
  fun resetDefault() {
    state.mode = if (StudioFlags.COMPOSE_LIVE_LITERALS.get()) LIVE_LITERALS else LIVE_EDIT
    state.liveEditDeviceEnabled = true
    state.liveEditPreviewEnabled = StudioFlags.COMPOSE_FAST_PREVIEW.get()
  }

  companion object {
    fun getInstance(): LiveEditApplicationConfiguration = ApplicationManager.getApplication().getService(
      LiveEditApplicationConfiguration::class.java)
  }
}