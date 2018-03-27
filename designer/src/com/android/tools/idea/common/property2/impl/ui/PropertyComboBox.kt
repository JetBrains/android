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

import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.idea.common.property2.api.EnumValue
import com.android.tools.idea.common.property2.impl.model.ComboBoxPropertyEditorModel
import com.android.tools.idea.common.property2.impl.support.EditorFocusListener
import java.awt.EventQueue
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.plaf.basic.ComboPopup

/**
 * A standard control for editing property values with a popup list.
 *
 * This control will act as a ComboBox or a DropDown depending on the model.
 */
class PropertyComboBox(model: ComboBoxPropertyEditorModel): CommonComboBox<EnumValue, ComboBoxPropertyEditorModel>(model) {

  init {
    registerKeyAction({ model.enterKeyPressed() }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter")
    registerKeyAction({ model.escapeKeyPressed() }, KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "escape")

    val editor = editor.editorComponent as JTextField
    editor.registerKeyAction({ model.enterKeyPressed(currentValue) }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter")
    editor.registerKeyAction({ model.escapeKeyPressed() }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape")
    editor.registerKeyAction({ model.f1KeyPressed() }, KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "help")
    editor.registerKeyAction({ model.shiftF1KeyPressed() }, KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.SHIFT_DOWN_MASK), "help2")

    addFocusListener(EditorFocusListener(model, { currentValue }))
    addPopupMenuListener(
      object : PopupMenuListener {
        override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) {
          // Hack: We would like to determine WHY the popup is being closed.
          // If it happened because of either:
          //  (1) The user pressed the escape key to close the popup
          //  (2) The user clicked somewhere other than the JList inside the popup
          val currentEvent = EventQueue.getCurrentEvent()
          val fromEscapeKey = (currentEvent as? KeyEvent)?.keyCode == KeyEvent.VK_ESCAPE
          val clickOutsideList = currentEvent is MouseEvent && !isClickOnItemInPopup(currentEvent)
          model.popupMenuWillBecomeInvisible(fromEscapeKey || clickOutsideList)
        }

        override fun popupMenuCanceled(event: PopupMenuEvent) {
        }

        override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) {
          model.popupMenuWillBecomeVisible()
        }

        private fun isClickOnItemInPopup(event: MouseEvent): Boolean {
          val source = event.source as? JList<*> ?: return false
          val popup = SwingUtilities.getAncestorOfClass(ComboPopup::class.java, source)
          return popup != null
        }
      })
  }

  private val currentValue: String
    get() = if (isEditable) editor.item.toString() else model.selectedItem?.toString() ?: ""

  override fun updateFromModel() {
    super.updateFromModel()
    isVisible = model.visible
    if (model.focusRequest && !isFocusOwner) {
      requestFocusInWindow()
    }
    if (model.isPopupVisible != isPopupVisible) {
      isPopupVisible = model.isPopupVisible
    }
  }

  override fun getToolTipText(): String? = model.tooltip

  // Hack: This method is called to update the text editor with the content of the
  // selected item in the dropdown. We do not want that, since the editor text can
  // be different from any of the values in the dropdown. Instead control the text
  // editor value with the [CommonTextFieldModel] part of the comboBox model.
  override fun configureEditor(anEditor: ComboBoxEditor, anItem: Any?) = Unit

  // Hack: JavaDoc specifies that this method should not be overridden.
  // However this causes a value that is not represented in the popup to be
  // overwritten when the control looses focus. This is a workaround for now.
  override fun actionPerformed(event: ActionEvent) = Unit
}
