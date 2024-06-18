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

import com.android.tools.adtui.model.stdui.CommonTextFieldModel
import com.android.tools.adtui.model.stdui.EDITOR_NO_COMPLETIONS
import com.intellij.openapi.ui.ErrorBorderCapable
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.util.ui.SwingUndoUtil
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.util.function.Predicate
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.plaf.UIResource
import javax.swing.text.JTextComponent

const val OUTLINE_PROPERTY = "JComponent.outline"
const val ERROR_VALUE = "error"
const val WARNING_VALUE = "warning"

/**
 * TextField controlled by an [editorModel].
 */
open class CommonTextField<out M: CommonTextFieldModel>(val editorModel: M) : JBTextField() {

  private var _lookup: Lookup<M>? = null
  private var updatingFromModel = false
  private var documentChangeFromSetText = false

  /**
   * If false ignore all calls to scrollRectToVisible.
   * Do this to disable scrolling in table renderer that use this component.
   */
  var enableScrollInView = true

  val lookup: Lookup<M>?
    get() = _lookup

  init {
    if (editorModel.editingSupport.completion != EDITOR_NO_COMPLETIONS) {
      @Suppress("LeakingThis")
      val myLookup = Lookup(this)
      registerActionKey({ enterInLookup() }, KeyStrokes.ENTER, "enter")
      registerActionKey({ escapeInLookup() }, KeyStrokes.ESCAPE, "escape")
      registerActionKey({ tab() }, KeyStrokes.TAB, "tab")
      registerActionKey({ backTab() }, KeyStrokes.BACKTAB, "backTab")
      registerActionKey({ showLookupCompletions(text) }, KeyStrokes.CTRL_SPACE, "showCompletions")
      registerActionKey({ myLookup.selectNext() }, KeyStrokes.DOWN, "selectNext", { myLookup.enabled })
      registerActionKey({ myLookup.selectPrevious() }, KeyStrokes.UP, "selectPrevious", { myLookup.enabled })
      registerActionKey({ myLookup.selectNextPage() }, KeyStrokes.PAGE_DOWN, "selectNextPage", { myLookup.enabled })
      registerActionKey({ myLookup.selectPreviousPage() }, KeyStrokes.PAGE_UP, "selectPreviousPage", { myLookup.enabled })
      registerActionKey({ myLookup.selectFirst() }, KeyStrokes.CMD_HOME, "selectFirst", { myLookup.enabled })
      registerActionKey({ myLookup.selectLast() }, KeyStrokes.CMD_END, "selectLast", { myLookup.enabled })
      focusTraversalKeysEnabled = false // handle tab and shift-tab ourselves
      super.addFocusListener(object: FocusAdapter() {
        override fun focusLost(event: FocusEvent) {
          myLookup.close()
        }
      })
      _lookup = myLookup
    }
    putClientProperty(TextComponentEmptyText.STATUS_VISIBLE_FUNCTION, Predicate<JTextComponent> { text.isEmpty() })
    isFocusable = true
    setFromModel()

    editorModel.addListener { updateFromModel() }
    document.addDocumentListener(object: DocumentAdapter() {
      override fun textChanged(event: DocumentEvent) {
        if (!updatingFromModel) {
          val newText = text
          editorModel.text = newText

          // setText is usually initial setup. Don't show completions here:
          if (!documentChangeFromSetText && (newText.isNotEmpty() || lookup?.isVisible == true)) {
            showLookupCompletions(newText)
          }
          updateOutline()
        }
      }
    })
  }

  protected open fun showLookupCompletions(forText: String) {
    lookup?.showLookup(forText)
  }

  protected open fun updateFromModel() {
    setFromModel()
  }

  fun enterInLookup(): Boolean {
    return _lookup?.enter() ?: false
  }

  fun escapeInLookup(): Boolean {
    return _lookup?.escape() ?: false
  }

  fun isLookupEnabled(): Boolean {
    return _lookup?.enabled ?: false
  }

  private fun setFromModel() {
    updatingFromModel = true
    try {
      text = editorModel.value
      isEnabled = editorModel.enabled
      isEditable = editorModel.editable
      emptyText.text = editorModel.placeHolderValue
      updateOutline()
    }
    finally {
      updatingFromModel = false
    }
  }

  override fun updateUI() {
    super.updateUI()
    if (border == null || border is UIResource) {
      border = CommonTextBorder()
    }
  }

  override fun paintComponent(g: Graphics) {
    // Workaround for: JDK-4194023 : JTextField presents selection problems when anti-aliasing is turned on
    // If some code has turned antialiasing (or fractionalMetrics) on for this graphics instance, turn these off before painting the text.
    // The JDK is currently unable to paint text with selection when these are turned on. The user will see black on black.

    // Also allow display of text selections in CommonTextField.
    // This allows text selections in the layout inspector where these fields do not gain focus.
    val g2 = g.create() as Graphics2D
    val selectionVisible = caret.isSelectionVisible
    caret.isSelectionVisible = true

    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF)
    g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF)

    super.paintComponent(g2)

    caret.isSelectionVisible = selectionVisible
    g2.dispose()
  }

  override fun setText(text: String?) {
    documentChangeFromSetText = true
    try {
      // Avoid flickering: Only update if value is different from current value
      if (!text.equals(super.getText())) {
        super.setText(text)
      }
      SwingUndoUtil.resetUndoRedoActions(this)
    }
    finally {
      documentChangeFromSetText = false
    }
  }

  override fun scrollRectToVisible(r: Rectangle) {
    if (enableScrollInView) {
      super.scrollRectToVisible(r)
    }
  }

  private fun tab() {
    enterInLookup()
    transferFocus()
  }

  private fun backTab() {
    enterInLookup()
    transferFocusBackward()
  }

  // Update the outline property on component such that the Darcula border will
  // be able to indicate an error by painting a red border.
  private fun updateOutline() {
    // If this text field is an editor in a ComboBox or another complex edit control,
    // set the property on the nearest parent that has an error border (which may be this TextField).
    val component = getComponentWithErrorBorder() ?: return
    val current = component.getClientProperty(OUTLINE_PROPERTY)
    val (code, _) = editorModel.editingSupport.validation(text)
    val newOutline = code.outline
    if (current != newOutline) {
      component.putClientProperty(OUTLINE_PROPERTY, newOutline)
      component.repaint()
    }
  }

  private fun getComponentWithErrorBorder(): JComponent? {
    var component: JComponent? = this
    while (component != null && component.border !is ErrorBorderCapable) {
      component = component.parent as? JComponent
    }
    return component
  }
}
