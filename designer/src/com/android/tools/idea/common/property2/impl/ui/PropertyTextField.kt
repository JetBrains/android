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
package com.android.tools.idea.common.property2.impl.ui

import com.android.tools.adtui.stdui.CommonTextField
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.idea.common.property2.impl.model.TextFieldPropertyEditorModel
import com.android.tools.idea.common.property2.impl.support.HelpSupportBinding
import com.android.tools.idea.common.property2.impl.support.TextEditorFocusListener
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder
import com.intellij.openapi.actionSystem.DataProvider
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JComponent

/**
 * A standard control for editing a text property.
 */
class PropertyTextField(editorModel: TextFieldPropertyEditorModel) : CommonTextField<TextFieldPropertyEditorModel>(editorModel),
                                                                     DataProvider {
  init {
    registerActionKey({ enter() }, KeyStrokes.enter, "enter")
    registerActionKey({ tab() }, KeyStrokes.tab, "tab")
    registerActionKey({ backTab() }, KeyStrokes.backtab, "backTab")
    registerActionKey({ escape() }, KeyStrokes.escape, "escape")
    HelpSupportBinding.registerHelpKeyActions(this, { editorModel.property })
    addFocusListener(TextEditorFocusListener(this, this, editorModel))
    putClientProperty(DarculaUIUtil.COMPACT_PROPERTY, true)
    focusTraversalKeysEnabled = false // handle tab and shift-tab ourselves
  }

  override fun updateFromModel() {
    super.updateFromModel()
    isVisible = editorModel.visible
    if (editorModel.focusRequest && !isFocusOwner) {
      requestFocusInWindow()
    }
  }

  override fun getToolTipText(event: MouseEvent): String? {
    return PropertyTooltip.setToolTip(this, event, editorModel.property, forValue = true, text = text.orEmpty())
  }

  override fun getData(dataId: String): Any? {
    return editorModel.getData(dataId)
  }

  private fun enter() {
    enterInLookup()
    commit()
  }

  private fun tab() {
    if (commit()) {
      transferFocus()
    }
    // TODO: b/121043039 Add some kind of notification that the commit failed e.g shake the edit control
  }

  private fun backTab() {
    if (commit()) {
      transferFocusBackward()
    }
    // TODO: b/121043039 Add some kind of notification that the commit failed e.g shake the edit control
  }

  private fun commit(): Boolean {
    if (!editorModel.commit()) {
      return false
    }
    selectAll()
    return true
  }

  private fun escape() {
    if (escapeInLookup()) {
      return
    }
    editorModel.escape()
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
