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
package com.android.tools.idea.uibuilder.visual.visuallint

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

@State(name = "VisualLint", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
class VisualLintSettings : SimplePersistentStateComponent<VisualLintSettings.State>(State()) {

  class State: BaseState() {
    var isVisualLintFilterSelected by property(true)
  }

  /**
   * Record if the toggle action of visual lint filter is selected in
   * [com.android.tools.idea.common.error.IssuePanelViewOptionActionGroup]. When it is **NOT** selected, the visual lint issues/problems
   * are not displayed in the [com.android.tools.idea.common.error.DesignerCommonIssuePanel].
   */
  var isVisualLintFilterSelected: Boolean
    get() = state.isVisualLintFilterSelected
    set(value) {
      state.isVisualLintFilterSelected = value
      state.intIncrementModificationCount()
    }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): VisualLintSettings = project.getService(VisualLintSettings::class.java)
  }
}
