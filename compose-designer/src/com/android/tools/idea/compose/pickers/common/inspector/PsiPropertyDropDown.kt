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
package com.android.tools.idea.compose.pickers.common.inspector

import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.adtui.stdui.CommonTextField
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.impl.support.HelpSupportBinding
import com.intellij.ide.actions.UndoRedoAction
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaJBPopupComboPopup.USE_LIVE_UPDATE_MODEL
import com.intellij.ui.PopupMenuListenerAdapter
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer
import javax.swing.event.PopupMenuEvent

/**
 * Dropdown component for PsiProperties.
 *
 * Uses Intellij's implementation for the popup to support speed search.
 */
internal class PsiPropertyDropDown(
  model: PsiDropDownModel,
  asTableCellEditor: Boolean,
  listCellRenderer: ListCellRenderer<EnumValue>
) : JPanel(BorderLayout()) {
  private val comboBox = WrappedComboBox(model, asTableCellEditor, listCellRenderer)

  init {
    background = UIUtil.TRANSPARENT_COLOR
    isOpaque = false
    comboBox.actionOnKeyNavigation = false
    add(comboBox, BorderLayout.CENTER)
  }
}

private class WrappedComboBox(
  model: PsiDropDownModel,
  asTableCellEditor: Boolean,
  renderer: ListCellRenderer<EnumValue>
) : CommonComboBox<EnumValue, PsiDropDownModel>(model) {
  private val textField = editor.editorComponent as CommonTextField<*>
  private var inSetup = false

  init {
    putClientProperty(DarculaUIUtil.COMPACT_PROPERTY, true)
    @Suppress("UnstableApiUsage")
    putClientProperty(
      USE_LIVE_UPDATE_MODEL,
      true
    ) // Ask Intellij's popup list model to update automatically
    setRenderer(renderer)
    background = secondaryPanelBackground
    isSwingPopup = false // Use Intellij's popup component
    preferredSize =
      preferredSize // Make sure the size cannot be modified by layout managers, otherwise the popup
    // may close unexpectedly
    isOpaque = false

    // Register key stroke navigation for dropdowns
    unregisterKeyboardAction(KeyStrokes.ESCAPE) // Remove existing bindings
    registerActionKey(this::escape, KeyStrokes.ESCAPE, "escape", { wouldConsumeEscape() })
    registerActionKey(
      this::transferFocus,
      KeyStrokes.TAB,
      "tab",
      condition = JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
    )
    registerActionKey(
      this::transferFocusBackward,
      KeyStrokes.BACKTAB,
      "backtab",
      condition = JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
    )

    // We consume the shift, otherwise the popup will show when backtab-ing
    registerActionKey(
      {},
      KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT, KeyEvent.SHIFT_DOWN_MASK),
      "shift",
      condition = JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
    )
    focusTraversalKeysEnabled = false // handle tab and shift-tab ourselves

    HelpSupportBinding.registerHelpKeyActions(
      this,
      { model.property },
      JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
    )
    if (asTableCellEditor) {
      putClientProperty("JComboBox.isTableCellEditor", true)
    }

    textField.background = UIUtil.TRANSPARENT_COLOR
    textField.isOpaque = false
    textField.putClientProperty(UndoRedoAction.IGNORE_SWING_UNDO_MANAGER, true)

    setFromModel()

    // This action is fired when changes to the selectedIndex is made, which includes mouse clicks
    // and certain keystrokes
    addActionListener {
      if (!inSetup) {
        model.selectEnumValue()
      }
    }
    addPopupMenuListener(
      // Popup contents should load as needed, so we ask the model to do so when the popup will be
      // shown
      object : PopupMenuListenerAdapter() {
        override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) {
          model.popupMenuWillBecomeVisible updatePopup@{
            // This callback means the model has an update for the list in the popup.
            //
            // At this point the List in the popup has already resized to the new elements
            // in the popup, but the popup itself must be resized somehow.
            // Do this by calling JPopupMenu.show(Component,x,y) which could be overridden
            // in a LAF implementation of PopupMenuUI.
            val popupMenu = popup ?: return@updatePopup
            if (!popupMenu.isVisible) {
              return@updatePopup
            }
            popupMenu.show()
          }
        }
      }
    )
  }

  private fun wouldConsumeEscape(): Boolean = isPopupVisible

  private fun escape() {
    hidePopup()
  }

  override fun updateFromModel() {
    super.updateFromModel()
    setFromModel()
  }

  private fun setFromModel() {
    isVisible = model.visible
    foreground = model.displayedForeground(UIUtil.getLabelForeground())
    if (model.focusRequest && !isFocusOwner) {
      requestFocusInWindow()
    }
    if (!model.editable) {
      inSetup = true
      try {
        val currentIndex = model.getIndexOfCurrentValue()
        selectedIndex = currentIndex
        if (currentIndex < 0) {
          model.updateValueFromProperty()
        }
      } finally {
        inSetup = false
      }
    }
  }

  override fun setForeground(color: Color?) {
    super.setForeground(color)

    // This method may be called in constructor of super class. Don't use textField here:
    editor?.editorComponent?.foreground = color
  }

  override fun setBackground(color: Color?) {
    super.setBackground(color)

    // This method may be called in constructor of super class. Don't use textField here:
    editor?.editorComponent?.background = color
  }
}
