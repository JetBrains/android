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
package com.android.tools.idea.preview.pagination.actions

import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.pagination.PreviewPaginationManager
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel

// Editable text field width, approx 3 chars wide
private const val CURRENT_PAGE_FIELD_WIDTH = 3

/**
 * Action to show the current page number and the total number of pages available in a
 * [PreviewRepresentation] that supports pagination ([PreviewPaginationManager]).
 *
 * Users can manually modify the current page value by simply editing the text field.
 */
class CurrentPageEditorAction : AnAction(), CustomComponentAction {

  // These components are stateful and reused
  private var pageField: JBTextField? = null
  private var totalPagesLabel: JBLabel? = null
  private var panel: JPanel? = null

  // Cache PreviewPaginationManager for use in createCustomComponent
  private var paginationManager: PreviewPaginationManager? = null

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val manager = e.dataContext.getData(PreviewPaginationManager.Companion.KEY) ?: return
    paginationManager = manager

    // Update the component's visual state if it has been created
    panel?.let { p ->
      if (pageField?.hasFocus() == false) { // Don't update text if user is typing
        updateFieldTextFromManager()
      }
      updateTotalPagesTextFromManager()
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    if (panel == null) {
      panel =
        JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(2), 0)).apply {
          isOpaque = false // Allow toolbar background to show through
        }

      pageField =
        JBTextField(CURRENT_PAGE_FIELD_WIDTH).apply {
          toolTipText = message("action.preview.pagination.page.editor.description")
          font = JBFont.small()

          addKeyListener(
            object : KeyAdapter() {
              override fun keyPressed(e: KeyEvent) {
                // TODO(b/417693700): validate whether this key is the one we want to use, or even
                //  if we want to dynamically change it according to some user configs.
                if (e.keyCode == KeyEvent.VK_ENTER) {
                  applyPageChangeFromField()
                  // Try to move focus away to finalize or prevent re-triggering
                  this@CurrentPageEditorAction.panel?.requestFocusInWindow()
                }
              }
            }
          )
          addFocusListener(
            object : FocusAdapter() {
              override fun focusLost(e: FocusEvent?) {
                applyPageChangeFromField()
              }
            }
          )
        }

      totalPagesLabel = JBLabel().apply { isOpaque = false }
      panel?.add(pageField)
      panel?.add(totalPagesLabel)
    }

    // Initialize text based on the current (or last known) manager state.
    // This is important because update might be called before createCustomComponent
    // or vice versa in different scenarios.
    updateFieldTextFromManager()
    updateTotalPagesTextFromManager()
    return panel!!
  }

  private fun applyPageChangeFromField() {
    val manager = paginationManager ?: return
    val totalPages = manager.getTotalPages() ?: return

    pageField?.let { field ->
      try {
        val newPageInput = field.text.toIntOrNull()
        if (newPageInput != null && newPageInput >= 1 && newPageInput <= totalPages) {
          manager.selectedPage = newPageInput - 1 // Convert to 0-indexed
        } else {
          // Revert to current page if input is invalid
          updateFieldTextFromManager()
        }
      } catch (ex: NumberFormatException) {
        // Revert to current page exception found
        updateFieldTextFromManager()
      }
    }
  }

  private fun updateFieldTextFromManager() {
    paginationManager?.let { pageField?.text = (it.selectedPage + 1).toString() }
  }

  private fun updateTotalPagesTextFromManager() {
    val totalPages = paginationManager?.getTotalPages() ?: return
    paginationManager?.let {
      totalPagesLabel?.text = message("action.preview.pagination.page.total.pages", totalPages)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    // Try to give focus to the editable page field on click
    pageField?.requestFocusInWindow()
  }
}
