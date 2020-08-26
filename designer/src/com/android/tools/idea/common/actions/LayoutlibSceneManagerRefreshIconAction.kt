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
package com.android.tools.idea.common.actions

import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.RenderListener
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.AnimatedIcon

/**
 * [AnAction] that can be added to toolbars to show a progress icon while the [LayoutlibSceneManager] is rendering.
 */
@Suppress("ComponentNotRegistered") // This action is just an icon and does not need to be registered
class LayoutlibSceneManagerRefreshIconAction(sceneManager: LayoutlibSceneManager): AnAction() {
  private var isRendering = false
    set(value) {
      if (field != value) {
        field = value
        // Force action update
        ActivityTracker.getInstance().inc()
      }
    }

  init {
    templatePresentation.disabledIcon = AnimatedIcon.Default()

    sceneManager.addRenderListener(object : RenderListener {
      override fun onRenderStarted() {
        isRendering = true
      }

      override fun onRenderCompleted() {
        isRendering = false
      }
    })
  }

  override fun update(e: AnActionEvent) {
    e.presentation.apply {
      isEnabled = false
      isVisible = isRendering
    }
  }

  override fun actionPerformed(e: AnActionEvent) {}
}