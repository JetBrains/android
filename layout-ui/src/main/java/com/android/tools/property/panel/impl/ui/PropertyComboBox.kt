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

import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.adtui.stdui.CommonTextField
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.impl.model.ComboBoxPropertyEditorModel
import com.android.tools.property.panel.impl.support.HelpSupportBinding
import com.android.tools.property.panel.impl.support.TextEditorFocusListener
import com.intellij.ide.actions.UndoRedoAction
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import javax.swing.ComboBoxEditor
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.ListCellRenderer
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

/**
 * A standard control for editing property values with a popup list.
 *
 * This control will act as a ComboBox or a DropDown depending on the model.
 */
class PropertyComboBox(model: ComboBoxPropertyEditorModel, asTableCellEditor: Boolean): JPanel(BorderLayout()) {
  private val comboBox = WrappedComboBox(model, asTableCellEditor)

  init {
    background = UIUtil.TRANSPARENT_COLOR
    isOpaque = false
    comboBox.actionOnKeyNavigation = false
    add(comboBox, BorderLayout.CENTER)
  }

  var renderer: ListCellRenderer<in EnumValue>
    get() = comboBox.renderer
    set(value) { comboBox.renderer = value }

  val editor: CommonTextField<*>
    get() = comboBox.editor.editorComponent as CommonTextField<*>
}

private class WrappedComboBox(model: ComboBoxPropertyEditorModel, asTableCellEditor: Boolean)
  : CommonComboBox<EnumValue, ComboBoxPropertyEditorModel>(model), DataProvider {
  private val textField = editor.editorComponent as CommonTextField<*>
  private var inSetup = false

  init {
    putClientProperty(DarculaUIUtil.COMPACT_PROPERTY, true)

    // Register key stroke navigation for dropdowns (textField is not editable)
    registerActionKey({ enterInPopup() }, KeyStrokes.ENTER, "enter")
    registerActionKey({ enterInPopup() }, KeyStrokes.SPACE, "enter")
    registerActionKey({ model.escapeKeyPressed() }, KeyStrokes.ESCAPE, "escape")
    registerActionKey({ tab { enterInPopup() } }, KeyStrokes.TAB, "tab", condition = JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    registerActionKey({ backtab { enterInPopup() } }, KeyStrokes.BACKTAB, "backtab")
    focusTraversalKeysEnabled = false // handle tab and shift-tab ourselves
    background = UIUtil.TRANSPARENT_COLOR
    isOpaque = false
    HelpSupportBinding.registerHelpKeyActions(this, { model.property }, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    if (asTableCellEditor) {
      putClientProperty("JComboBox.isTableCellEditor", true)
    }

    // Register key stroke navigation for dropdowns (textField is editable)
    textField.registerActionKey({ enter() }, KeyStrokes.ENTER, "enter")
    textField.registerActionKey({ space() }, KeyStrokes.TYPED_SPACE, "space")
    textField.registerActionKey({ escape() }, KeyStrokes.ESCAPE, "escape")
    textField.registerActionKey({ tab { enter() } }, KeyStrokes.TAB, "tab")
    textField.registerActionKey({ backtab { enter() } }, KeyStrokes.BACKTAB, "backtab")
    textField.focusTraversalKeysEnabled = false // handle tab and shift-tab ourselves
    textField.background = UIUtil.TRANSPARENT_COLOR
    textField.isOpaque = false
    textField.putClientProperty(UndoRedoAction.IGNORE_SWING_UNDO_MANAGER, true)

    val focusListener = TextEditorFocusListener(textField, this, model)
    addFocusListener(focusListener)
    textField.addFocusListener(focusListener)
    setFromModel()

    // This action is fired when changes to the selectedIndex is made, which includes mouse clicks and certain keystrokes
    addActionListener {
      if (!inSetup) {
        model.selectEnumValue()
      }
    }

    addPopupMenuListener(
      object : PopupMenuListener {
        override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) =
          model.popupMenuWillBecomeInvisible()

        override fun popupMenuCanceled(event: PopupMenuEvent) {
        }

        override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) {
          model.popupMenuWillBecomeVisible updatePopup@{
            // This callback means the model has an update for the list in the popup.
            //
            // At this point the List in the popup has already resized to the new elements
            // in the popup, but the popup itself must be resized somehow.
            // Do this by calling JPopupMenu.show(Component,x,y) which could be overridden
            // in a LAF implementation of PopupMenuUI.
            val popupMenu = popup as? JPopupMenu ?: return@updatePopup
            if (!isPopupVisible) {
              return@updatePopup
            }
            val location = popupMenu.locationOnScreen
            val comboLocation = this@WrappedComboBox.locationOnScreen
            location.translate(-comboLocation.x, -comboLocation.y)
            popupMenu.show(this@WrappedComboBox, location.x, location.y)
            popupMenu.pack()
          }
        }
      })
  }

  private fun enterInPopup() {
    // This will cause the firing of an action event:
    popup?.list?.selectedIndex?.let { selectedIndex = it }
    hidePopup()
    textField.selectAll()
  }

  private fun enter() {
    if (isPopupVisible) {
      enterInPopup()
      return
    }
    textField.enterInLookup()
    model.enterKeyPressed()
    textField.selectAll()
  }

  private fun escape() {
    if (!textField.escapeInLookup()) {
      model.escapeKeyPressed()
    }
  }

  // Override the handling of the space key for the text editor.
  // If the popup is visible we want to commit the selected value in the popup list,
  // if the popup is not visible emulate normal typing in the text editor.
  private fun space() {
    if (isPopupVisible) {
      enterInPopup()
      return
    }
    textField.replaceSelection(" ")
  }

  private fun tab(action: () -> Unit) {
    action()
    textField.transferFocus()
  }

  private fun backtab(action: () -> Unit) {
    action()
    transferFocusBackward()
  }

  override fun updateFromModel() {
    super.updateFromModel()
    setFromModel()
  }

  private fun setFromModel() {
    isVisible = model.visible
    foreground = model.displayedForeground(UIUtil.getLabelForeground())
    background = model.displayedBackground(UIUtil.TRANSPARENT_COLOR)
    isOpaque = model.isUsedInRendererWithSelection
    if (model.focusRequest && !isFocusOwner) {
      requestFocusInWindow()
    }
    if (model.isPopupVisible != isPopupVisible) {
      isPopupVisible = model.isPopupVisible
    }
    if (!model.editable) {
      inSetup = true
      try {
        selectedIndex = model.getIndexOfCurrentValue()
      }
      finally {
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

  override fun getToolTipText(event: MouseEvent): String? {
    // Trick: Use the component from the event.source for tooltip in tables. See TableEditor.getToolTip().
    val component = event.source as? JComponent ?: textField
    return PropertyTooltip.setToolTip(component, event, model.property, forValue = true, text = textField.text)
  }

  override fun getData(dataId: String): Any? {
    return model.getData(dataId)
  }

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
