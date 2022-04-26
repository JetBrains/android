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
package com.android.tools.idea.editors.literals

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import org.jetbrains.annotations.TestOnly

private const val ENABLED_SETTING_DEFAULT = false

@com.intellij.openapi.components.State(name = "FastPreviewConfiguration", storages = [(Storage(StoragePathMacros.NON_ROAMABLE_FILE))])
@Service
class FastPreviewApplicationConfiguration : SimplePersistentStateComponent<FastPreviewApplicationConfiguration.State>(State()) {
  class State : BaseState() {
    var isEnabled by property(ENABLED_SETTING_DEFAULT)
  }

  /**
   * True if the Live Literals feature is enabled. This is an application wide setting that allows the user to disable the feature.
   * This does not indicate if the current project has Live Literals available. For that, check [LiveLiteralsService.isAvailable]
   */
  var isEnabled
    get() = StudioFlags.COMPOSE_FAST_PREVIEW.get()
            && state.isEnabled
    set(value) {
      state.isEnabled = value
    }

  @TestOnly
  fun resetDefault() {
    state.isEnabled = ENABLED_SETTING_DEFAULT
  }

  companion object {
    fun getInstance(): FastPreviewApplicationConfiguration = ApplicationManager.getApplication().getService(
      FastPreviewApplicationConfiguration::class.java)
  }
}