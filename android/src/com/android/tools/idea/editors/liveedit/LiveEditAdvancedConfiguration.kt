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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros

@com.intellij.openapi.components.State(name = "LiveEditAdvancedConfiguration", storages = [(Storage(StoragePathMacros.NON_ROAMABLE_FILE))])
@Service
class LiveEditAdvancedConfiguration : SimplePersistentStateComponent<LiveEditAdvancedConfiguration.State>(State()) {
  class State: BaseState() {
    var useDebugMode by property(false)
    var allowClassStructuralRedefinition by property(false)
    var refreshRateMs by property(MIN_REFRESH_RATE_MS)
  }

  var useDebugMode
    get() = state.useDebugMode
    set(value) {
      state.useDebugMode= value
    }

  var allowClassStructuralRedefinition
    get() = state.allowClassStructuralRedefinition
    set(value) {
      state.allowClassStructuralRedefinition = value
    }

  var refreshRateMs
    get() = state.refreshRateMs
    set(value) {
      var newValue = value.coerceIn(REFRESH_RATE_RANGE)
      state.refreshRateMs = newValue
    }

  companion object {
    const val MIN_REFRESH_RATE_MS = 1
    val REFRESH_RATE_RANGE = MIN_REFRESH_RATE_MS .. 9999
    @JvmStatic fun getInstance(): LiveEditAdvancedConfiguration = ApplicationManager.getApplication().getService(LiveEditAdvancedConfiguration::class.java)
  }
}
