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
package com.android.tools.idea.compose

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.ProjectManager

@com.intellij.openapi.components.State(name = "ComposeExperimentalConfiguration", storages = [(Storage(StoragePathMacros.NON_ROAMABLE_FILE))])
@Service
class ComposeExperimentalConfiguration : SimplePersistentStateComponent<ComposeExperimentalConfiguration.State>(State()) {
  class State: BaseState() {
    var isAnimationPreviewEnabled by property(true)
    var isPreviewPickerEnabled by property(true)
    var isBuildOnSaveEnabled by property(true)
  }

  /**
   * True if animation preview is enabled.
   */
  var isAnimationPreviewEnabled
    get() = state.isAnimationPreviewEnabled
    set(value) {
      state.isAnimationPreviewEnabled = value
      // Force update of the actions to hide/show start animation preview icons
      ActivityTracker.getInstance().inc()
    }

  /**
   * True if the @Preview picker from the Gutter is enabled.
   */
  var isPreviewPickerEnabled
    get() = state.isPreviewPickerEnabled
    set(value) {
      state.isPreviewPickerEnabled = value
      updateGutterIcons()
    }

  /**
   * True if the @Preview build on save is enabled. This settings only applies when Live Edit is enabled.
   */
  var isBuildOnSaveEnabled
    get() = state.isBuildOnSaveEnabled
    set(value) {
      state.isBuildOnSaveEnabled = value
    }

  companion object {
    @JvmStatic
    fun getInstance(): ComposeExperimentalConfiguration = ApplicationManager.getApplication().getService(ComposeExperimentalConfiguration::class.java)
  }
}

/**
 * Force update of the highlights so the gutter icons are updated
 */
private fun updateGutterIcons() {
  ProjectManager.getInstance().openProjects.forEach {
    DaemonCodeAnalyzer.getInstance(it).restart()
  }
}