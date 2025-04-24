/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.preview.actions

import com.android.tools.idea.preview.PreviewViewFilter
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** The action to show the query history in preview. */
class PreviewFilterShowHistoryAction : AnAction(null, null, StudioIcons.Common.FILTER) {
  override fun update(e: AnActionEvent) {
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
class PreviewFilterTextAction(private val filter: PreviewViewFilter) :
  AnAction(), CustomComponentAction {
  override fun update(e: AnActionEvent) {
    // Do nothing. This action is for showing text field.
  }

  override fun actionPerformed(e: AnActionEvent) {
    // Do nothing. This action is for showing text field. It cannot be performed.
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val field = JBTextField()
    val dataContext = DataManager.getInstance().getDataContext(field)
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
          filter.filter(text, dataContext)
        }
      }
    )
    field.border = JBUI.Borders.empty()

    field.minimumSize = JBDimension(200, 20)
    field.preferredSize = JBDimension(300, 20)
    return field
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
