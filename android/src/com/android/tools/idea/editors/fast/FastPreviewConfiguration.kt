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
package com.android.tools.idea.editors.fast

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import org.jetbrains.annotations.TestOnly

/**
 * Service that handles and persists the enabled state of the Fast Preview.
 */
@com.intellij.openapi.components.State(name = "FastPreviewConfiguration", storages = [(Storage(StoragePathMacros.NON_ROAMABLE_FILE))])
@Service
class FastPreviewConfiguration : SimplePersistentStateComponent<FastPreviewConfiguration.State>(State()) {
  class State : BaseState() {
    var fastPreviewEnabled by property(true)
  }

  var isEnabled
    get() = state.fastPreviewEnabled
    set(value) {
      if (state.fastPreviewEnabled != value) {
        state.fastPreviewEnabled = value
      }
    }

  @TestOnly
  fun resetDefault() {
    state.fastPreviewEnabled = true
  }

  companion object {
    fun getInstance(): FastPreviewConfiguration = ApplicationManager.getApplication().getService(
      FastPreviewConfiguration::class.java
    )
  }
}