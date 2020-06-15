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
package com.android.tools.adtui.stdui

import com.android.tools.adtui.model.stdui.CommonComboBoxModel
import com.android.tools.adtui.model.stdui.CommonElementSelectability
import com.android.tools.adtui.model.stdui.CommonTextFieldModel
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.JBUI
import java.awt.event.MouseEvent
import javax.swing.DefaultListSelectionModel
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.ListModel
import javax.swing.plaf.UIResource
import javax.swing.plaf.basic.BasicComboBoxEditor

/**
 * ComboBox controlled by a [CommonComboBoxModel].
 */
open class CommonComboBox<E, out M : CommonComboBoxModel<E>>(model: M) : ComboBox<E>(model) {

  private var textField: CommonTextField<*>? = null

  init {
    // Override the editor with a CommonTextField such that the text is also controlled by the model.
    @Suppress("LeakingThis")
    super.setEditor(CommonComboBoxEditor(model, this))
    textField = editor.editorComponent as CommonTextField<*>
    textField?.registerActionKey({ moveNext() }, KeyStrokes.DOWN, "moveNext", { consumeKeyNavigation })
    textField?.registerActionKey({ movePrevious() }, KeyStrokes.UP, "movePrevious", { consumeKeyNavigation })
    textField?.registerActionKey({ moveNextPage() }, KeyStrokes.PAGE_DOWN, "moveNextPage", { consumeKeyNavigation })
    textField?.registerActionKey({ movePreviousPage() }, KeyStrokes.PAGE_UP, "movePreviousPage", { consumeKeyNavigation })
    textField?.registerActionKey({ togglePopup() }, KeyStrokes.ALT_DOWN, "toggle")
    registerActionKey({}, KeyStrokes.PAGE_DOWN, "noop", { false }, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    registerActionKey({}, KeyStrokes.PAGE_UP, "noop", { false }, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)

    setFromModel()

    model.addListener(ValueChangedListener {
      updateFromModel()
      repaint()
    })
  }

  protected open fun updateFromModel() {
    setFromModel()
  }

  private fun setFromModel() {
    isEnabled = model.enabled
    if (isEditable != model.editable) {
      super.setEditable(model.editable)
    }
  }
  private val consumeKeyNavigation: Boolean
    get() = isPopupVisible || (textField?.lookup?.isVisible == true)

  private fun togglePopup() {
    if (textField?.lookup?.isVisible == true) {
      return
    }
    if (!isPopupVisible) {
      showPopup()
    }
    else {
      hidePopup()
    }
  }

  private fun moveNext() {
    if (textField?.lookup?.isVisible == true) {
      textField?.lookup?.selectNext()
    }
    else if (!isPopupVisible) {
      showPopup()
    }
    else {
      val size = dataModel.size
      val index = selectedIndex
      selectedIndex = Integer.min(index + 1, size - 1)
    }
  }

  private fun movePrevious() {
    if (textField?.lookup?.isVisible == true) {
      textField?.lookup?.selectPrevious()
    }
    else if (isPopupVisible) {
      val minValue = if (dataModel.size == 0) -1 else 0
      val index = selectedIndex
      selectedIndex = Integer.max(index - 1, minValue)
    }
  }

  private fun moveNextPage() {
    if (textField?.lookup?.isVisible == true) {
      textField?.lookup?.selectNextPage()
    }
    else if (isPopupVisible) {
      val size = dataModel.size
      val index = selectedIndex
      selectedIndex = Integer.min(index + maximumRowCount, size - 1)
    }
  }

  private fun movePreviousPage() {
    if (textField?.lookup?.isVisible == true) {
      textField?.lookup?.selectPreviousPage()
    }
    else if (isPopupVisible) {
      val minValue = if (dataModel.size == 0) -1 else 0
      val index = selectedIndex
      selectedIndex = Integer.max(index - maximumRowCount, minValue)
    }
  }

  override fun updateUI() {
    super.updateUI()
    popup?.list?.selectionModel = MyPopupListSelectionModel(model)
    installDefaultRenderer()
  }

  override fun getModel(): M {
    @Suppress("UNCHECKED_CAST")
    return super.getModel() as M
  }

  // Install a default renderer in order to adjust the left margin of the popup.
  private fun installDefaultRenderer() {
    val renderer = getRenderer()
    if (renderer == null || renderer is UIResource) {
      setRenderer(CommonComboBoxRenderer.UIResource())
    }
  }

  private class CommonComboBoxEditor<out M : CommonTextFieldModel>(model: M, comboBox: JComponent) : BasicComboBoxEditor() {
    init {
      editor = TextFieldForComboBox(model, comboBox)
      editor.border = JBUI.Borders.empty()
    }

    override fun createEditorComponent(): JTextField? {
      // Hack: return null here, and override the editor in init.
      // We do this because this method is called by the constructor of the super class,
      // and the model parameter is not available at this point.
      return null
    }
  }

  private class TextFieldForComboBox<out M : CommonTextFieldModel>(model: M, private val comboBox: JComponent) : CommonTextField<M>(model) {
    override fun getToolTipText(): String? {
      return comboBox.toolTipText
    }

    override fun getToolTipText(event: MouseEvent?): String? {
      return comboBox.getToolTipText(event)
    }
  }

  /**
   * A list selection model that prevent the selection of non selectable elements.
   */
  private class MyPopupListSelectionModel<E>(private val model: ListModel<E>) : DefaultListSelectionModel() {

    override fun setSelectionInterval(index0: Int, index1: Int) {
      val index = findFirstSelectableIndex(index0)
      super.setSelectionInterval(index, index)
    }

    private fun findFirstSelectableIndex(start: Int): Int {
      var index = start
      if (index < 0) {
        return -1
      }

      while (index < model.size) {
        val element = model.getElementAt(index)
        val selectable = element as? CommonElementSelectability ?: return index
        if (selectable.isSelectable) {
          return index
        }
        index++
      }
      return -1
    }
  }
}
