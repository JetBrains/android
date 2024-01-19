/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.property.panel.impl.ui

import com.android.tools.adtui.stdui.CommonTextField
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.property.panel.impl.model.TextFieldPropertyEditorModel
import com.android.tools.property.panel.impl.support.HelpSupportBinding
import com.android.tools.property.panel.impl.support.TextEditorFocusListener
import com.intellij.ide.actions.UndoRedoAction
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.util.ui.UIUtil
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JComponent

/** A standard control for editing a text property. */
class PropertyTextField(editorModel: TextFieldPropertyEditorModel) :
  CommonTextField<TextFieldPropertyEditorModel>(editorModel), DataProvider {

  init {
    background = UIUtil.TRANSPARENT_COLOR
    isOpaque = false
    registerActionKey({ enter() }, KeyStrokes.ENTER, "enter")
    registerActionKey({ tab() }, KeyStrokes.TAB, "tab")
    registerActionKey({ backTab() }, KeyStrokes.BACKTAB, "backTab")
    registerActionKey({}, KeyStrokes.ESCAPE, "escape", { escape() })
    HelpSupportBinding.registerHelpKeyActions(this, { editorModel.property })
    addFocusListener(TextEditorFocusListener(this, this, editorModel))
    putClientProperty(DarculaUIUtil.COMPACT_PROPERTY, true)
    putClientProperty(UndoRedoAction.IGNORE_SWING_UNDO_MANAGER, true)
    focusTraversalKeysEnabled = false // handle tab and shift-tab ourselves
  }

  override fun updateFromModel() {
    super.updateFromModel()
    isVisible = editorModel.visible
    isFocusable = !editorModel.readOnly
    foreground = editorModel.displayedForeground(UIUtil.getLabelForeground())
    background = editorModel.displayedBackground(UIUtil.TRANSPARENT_COLOR)
    isOpaque = editorModel.isUsedInRendererWithSelection
    if (editorModel.focusRequest && !isFocusOwner) {
      requestFocusInWindow()
    }
  }

  override fun requestFocus() {
    requestFocusInWindow()
  }

  override fun requestFocusInWindow(): Boolean {
    if (isFocusable) {
      return super.requestFocusInWindow()
    } else {
      var parent = parent ?: return false
      while (!parent.isFocusable) {
        parent = parent.parent ?: return false
      }
      return parent.requestFocusInWindow()
    }
  }

  override fun getToolTipText(event: MouseEvent): String? {
    // Trick: Use the component from the event.source for tooltip in tables. See
    // TableEditor.getToolTip().
    val component = event.source as? JComponent ?: this
    PropertyTooltip.setToolTip(
      component,
      editorModel.property,
      editorModel.editingValue,
      text = text.orEmpty(),
    )
    return null
  }

  override fun getData(dataId: String): Any? {
    return editorModel.getData(dataId)
  }

  private fun enter() {
    enterInLookup()
    commit()
  }

  private fun tab() {
    enterInLookup()
    if (commit()) {
      transferFocus()
    }
    // TODO: b/121043039 Add some kind of notification that the commit failed e.g shake the edit
    // control
  }

  private fun backTab() {
    enterInLookup()
    if (commit()) {
      transferFocusBackward()
    }
    // TODO: b/121043039 Add some kind of notification that the commit failed e.g shake the edit
    // control
  }

  private fun commit(): Boolean {
    if (!editorModel.commit()) {
      return false
    }
    selectAll()
    return true
  }

  /** Returns true if it should consume the Escape key event */
  private fun escape(): Boolean {
    val inLookup = escapeInLookup()
    if (!inLookup) {
      editorModel.escape()
    }
    return inLookup
  }

  companion object {

    @JvmStatic
    fun addBorderAtTextFieldBorderSize(component: JComponent) {
      val insets = DarculaTextBorder().getBorderInsets(component)
      // The insets are already scaled: do not use JBUI.Borders.emptyBorder(...)
      component.border = BorderFactory.createEmptyBorder(0, insets.left, 0, insets.right)
    }
  }
}
