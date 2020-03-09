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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_ELEMENT
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.message
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ToggleAction
import icons.StudioIcons.Compose.INSPECT_PREVIEW
import icons.StudioIcons.Compose.INTERACTIVE_PREVIEW

/**
 * Action that controls when to enable the Interactive mode.
 *
 * @param dataContextProvider returns the [DataContext] containing the Compose Preview associated information.
 */
internal class EnableInteractiveAction(private val dataContextProvider: () -> DataContext) :
  ToggleAction(message("action.interactive.title"), message("action.interactive.description"), INTERACTIVE_PREVIEW) {

  private val isSelected: Boolean
    get() {
      val modelDataContext = dataContextProvider()
      val manager = modelDataContext.getData(COMPOSE_PREVIEW_MANAGER) ?: return false

      return manager.singlePreviewElementFqnFocus != null
    }

  override fun isSelected(e: AnActionEvent): Boolean = isSelected

  override fun setSelected(e: AnActionEvent, isSelected: Boolean) {
    val modelDataContext = dataContextProvider()
    val manager = modelDataContext.getData(COMPOSE_PREVIEW_MANAGER) ?: return
    val composableFqn = modelDataContext.getData(COMPOSE_PREVIEW_ELEMENT)?.composableMethodFqn ?: return

    manager.singlePreviewElementFqnFocus = if (isSelected) {
      composableFqn
    }
    else {
      null
    }
    manager.isInteractive = isSelected
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.icon = if (isSelected) INSPECT_PREVIEW else INTERACTIVE_PREVIEW
  }
}