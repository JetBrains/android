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

import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.property.panel.impl.model.TextFieldWithLeftButtonEditorModel
import com.android.tools.property.panel.impl.support.HelpSupportBinding
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.event.MouseListener
import javax.swing.JComponent

private const val ICON_LEFT_BORDER = 2

/**
 * A text editor with a [leftButton] shown on the left.
 *
 * The [leftButton] can optionally be a custom component e.g. a checkbox.
 */
open class PropertyTextFieldWithLeftButton(
  private val editorModel: TextFieldWithLeftButtonEditorModel,
  component: JComponent? = null
) : AdtSecondaryPanel(BorderLayout()), DataProvider {
  protected open val buttonAction = editorModel.buttonAction
  protected val leftComponent = component ?: IconWithFocusBorder { buttonAction }
  protected val leftButton = leftComponent as? IconWithFocusBorder
  private val textField = PropertyTextField(editorModel)

  init {
    background = UIUtil.TRANSPARENT_COLOR
    isOpaque = false
    border = DarculaTextBorder()
    putClientProperty(DarculaUIUtil.COMPACT_PROPERTY, true)
    leftButton?.border = JBUI.Borders.empty(0, ICON_LEFT_BORDER, 0, 0)
    textField.border = JBUI.Borders.empty()
    textField.isOpaque = false
    super.add(leftComponent, BorderLayout.WEST)
    super.add(textField, BorderLayout.CENTER)
    if (leftButton != null) {
      HelpSupportBinding.registerHelpKeyActions(leftButton, { editorModel.property })
    }

    editorModel.addListener { updateFromModel() }
    setFromModel()
  }

  override fun addMouseListener(listener: MouseListener) {
    textField.addMouseListener(listener)
  }

  override fun isFocusable(): Boolean {
    return leftComponent.isFocusable || textField.isFocusable
  }

  override fun requestFocus() {
    requestFocusInWindow()
  }

  override fun requestFocusInWindow(): Boolean {
    return leftComponent.requestFocusInWindow() || textField.requestFocusInWindow()
  }

  override fun hasFocus(): Boolean {
    return textField.hasFocus()
  }

  open fun updateFromModel() {
    setFromModel()
  }

  private fun setFromModel() {
    isVisible = editorModel.visible
    leftButton?.icon = editorModel.displayedIcon(editorModel.leftButtonIcon)
    leftButton?.readOnly = editorModel.readOnly
    background = editorModel.displayedBackground(UIUtil.TRANSPARENT_COLOR)
    isOpaque = editorModel.isUsedInRendererWithSelection
    toolTipText = editorModel.tooltip
  }

  override fun getData(dataId: String): Any? {
    return editorModel.getData(dataId)
  }
}
