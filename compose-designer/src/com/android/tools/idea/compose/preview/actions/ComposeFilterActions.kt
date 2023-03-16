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

import com.android.tools.adtui.compose.ComposeStatus
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.updateSceneViewVisibilities
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.isPreviewFilterEnabled
import com.android.tools.idea.compose.preview.message
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** The action to start the query mode in compose preview. */
class ComposeShowFilterAction(private val surface: DesignSurface<*>) :
  AnAction(message("action.scene.view.control.start.filter.preview.mode")) {
  override fun actionPerformed(e: AnActionEvent) {
    val manager = COMPOSE_PREVIEW_MANAGER.getData(surface) ?: return
    manager.isFilterEnabled = true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/** The action to exit the query mode in compose preview. */
class ComposeHideFilterAction(private val surface: DesignSurface<*>) :
  AnAction(null, null, AllIcons.Actions.Close), CustomComponentAction, RightAlignedToolbarAction {
  init {
    templatePresentation.putClientProperty(ComposeStatus.TEXT_POSITION, SwingConstants.LEADING)
  }

  @Suppress("DialogTitleCapitalization")
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible =
      COMPOSE_PREVIEW_MANAGER.getData(surface)?.isFilterEnabled ?: false
    val views = surface.sceneManagers.flatMap { it.sceneViews }
    val visibleCount = views.count { it.isVisible }
    e.presentation.text =
      when (visibleCount) {
        0 -> message("action.scene.view.control.no.matched.result")
        1 -> message("action.scene.view.control.one.matched.result")
        else -> message("action.scene.view.control.multiple.matched.results", visibleCount)
      }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val manager = COMPOSE_PREVIEW_MANAGER.getData(surface) ?: return
    manager.isFilterEnabled = false

    surface.updateSceneViewVisibilities { true }
  }

  override fun createCustomComponent(presentation: Presentation, place: String) =
    ActionButtonWithText(this, presentation, place, Dimension())

  override fun useSmallerFontForTextInToolbar() = true

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/** The action to show the query history in compose preview. */
class ComposeFilterShowHistoryAction : AnAction(null, null, StudioIcons.Common.FILTER) {
  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = isPreviewFilterEnabled(e.dataContext)
    // TODO(b/266080992): Showing filter history is not implemented yet.
    e.presentation.isEnabled = false
  }

  override fun actionPerformed(e: AnActionEvent) {
    // TODO(b/266080992): Implement showing search history
    throw Exception(
      "Showing searching history is not implemented and this action should never be performed."
    )
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/** A text field for enter the keyword of filter. */
class ComposeFilterTextAction(private val filter: ComposeViewFilter) :
  AnAction(), CustomComponentAction {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isPreviewFilterEnabled(e.dataContext)
  }

  override fun actionPerformed(e: AnActionEvent) {
    // Do nothing. This action is for showing text field. It cannot be performed.
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val field = JBTextField()
    field.document.addDocumentListener(
      object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) {
          filterSceneViews(field.text)
        }

        override fun removeUpdate(e: DocumentEvent) {
          filterSceneViews(field.text)
        }

        override fun changedUpdate(e: DocumentEvent) {
          filterSceneViews(field.text)
        }

        private fun filterSceneViews(text: String?) {
          filter.filter(text)
        }
      }
    )
    field.border = JBUI.Borders.empty()

    field.minimumSize = JBDimension(200, 20)
    field.preferredSize = JBDimension(300, 20)
    return field
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
