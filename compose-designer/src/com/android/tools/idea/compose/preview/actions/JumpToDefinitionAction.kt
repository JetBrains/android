/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.compose.preview.navigation.ComposePreviewNavigationHandler
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import java.awt.MouseInfo
import javax.swing.SwingUtilities
import kotlin.properties.Delegates
import kotlinx.coroutines.launch

/**
 * [AnAction] that navigates to the deepest Composable that is part of the project, and that is
 * visible at the position where the mouse is located, at the moment the action is created.
 */
class JumpToDefinitionAction(
  surface: DesignSurface<LayoutlibSceneManager>,
  private val sceneManagerProvider: () -> LayoutlibSceneManager?,
  private val composePreviewNavigationHandler: ComposePreviewNavigationHandler,
  private val sceneView: SceneView,
  title: String = "Jump to Definition"
) : AnAction(title) {

  private var x by Delegates.notNull<Int>()
  private var y by Delegates.notNull<Int>()

  init {
    // Extract the information relative to the mouse position when creating the action,
    // not when clicking on "jump to definition"
    val mousePosition = MouseInfo.getPointerInfo().location
    SwingUtilities.convertPointFromScreen(mousePosition, surface.interactionPane)
    x = mousePosition.x
    y = mousePosition.y
  }

  private val scope = AndroidCoroutineScope(surface)

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = sceneManagerProvider()?.renderResult != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    scope.launch {
      composePreviewNavigationHandler.handleNavigate(sceneView, x, y, requestFocus = true)
    }
  }
}
