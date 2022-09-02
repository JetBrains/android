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

import com.android.tools.idea.common.actions.ActionButtonWithToolTipDescription
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.ui.AnActionButton
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import kotlinx.coroutines.launch

internal class CloseAnimationInspectorAction(private val dataContextProvider: () -> DataContext) :
  AnActionButton(message("action.stop.animation.inspector.title"), "", StudioIcons.Common.CLOSE),
  CustomComponentAction {

  override fun updateButton(e: AnActionEvent) {
    super.updateButton(e)
    e.presentation.isEnabled = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val modelDataContext = dataContextProvider()
    val manager = modelDataContext.getData(COMPOSE_PREVIEW_MANAGER) ?: return

    val instance = manager.animationInspectionPreviewElementInstance
    AndroidCoroutineScope(manager).launch {
      if (instance != null) manager.startInteractivePreview(instance)
      else manager.stopInteractivePreview()
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String) =
    ActionButtonWithToolTipDescription(this, presentation, place).apply {
      border = JBUI.Borders.empty(1, 2)
    }
}
