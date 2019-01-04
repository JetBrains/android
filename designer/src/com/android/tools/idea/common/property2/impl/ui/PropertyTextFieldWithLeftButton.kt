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

import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.idea.common.property2.impl.model.KeyStrokes
import com.android.tools.idea.common.property2.impl.model.TextFieldWithLeftButtonEditorModel
import com.android.tools.idea.common.property2.impl.support.HelpSupportBinding
import com.android.tools.idea.common.property2.impl.support.ImageFocusListener
import com.intellij.ide.DataManager
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

private const val ICON_LEFT_BORDER = 2

/**
 * A text editor with a [leftButton] shown on the left.
 *
 * The [leftButton] can optionally be a custom component e.g. a checkbox.
 */
open class PropertyTextFieldWithLeftButton(private val editorModel: TextFieldWithLeftButtonEditorModel,
                                           component: JComponent? = null) : AdtSecondaryPanel(BorderLayout()), DataProvider {
  protected val leftComponent = component ?: IconWithFocusBorder()
  protected val leftButton = leftComponent as? IconWithFocusBorder
  protected val textField = PropertyTextField(editorModel)

  init {
    border = DarculaTextBorder()
    leftButton?.border = JBUI.Borders.empty(0, ICON_LEFT_BORDER, 0, 0)
    leftButton?.isFocusable = true
    textField.border = JBUI.Borders.empty()
    textField.isOpaque = false
    super.add(leftComponent, BorderLayout.WEST)
    super.add(textField, BorderLayout.CENTER)
    leftButton?.registerActionKey({ buttonPressed(null) }, KeyStrokes.space, "space")
    leftButton?.registerActionKey({ buttonPressed(null) }, KeyStrokes.enter, "enter")
    if (leftButton != null) {
      HelpSupportBinding.registerHelpKeyActions(leftButton, { editorModel.property })
    }

    editorModel.addListener(ValueChangedListener { updateFromModel() })
    leftButton?.addMouseListener(object: MouseAdapter() {
      override fun mousePressed(event: MouseEvent) {
        buttonPressed(event)
      }
    })
    leftButton?.addFocusListener(ImageFocusListener(leftButton) { setFromModel() })
    setFromModel()
  }

  override fun requestFocus() {
    leftComponent.requestFocusInWindow() || textField.requestFocusInWindow()
  }

  override fun hasFocus(): Boolean {
    return textField.hasFocus()
  }

  open fun updateFromModel() {
    setFromModel()
  }

  private fun setFromModel() {
    isVisible = editorModel.visible
    leftButton?.icon = editorModel.getLeftButtonIcon(leftButton?.hasFocus() == true)
    toolTipText = editorModel.tooltip
  }

  open fun buttonPressed(mouseEvent: MouseEvent?) {
    var action = editorModel.buttonAction ?: return
    var event = AnActionEvent.createFromAnAction(action, mouseEvent, ActionPlaces.UNKNOWN, DataManager.getInstance().getDataContext(this))
    if (action is ActionGroup) {
      action = action.getChildren(event).firstOrNull() ?: return
      event = AnActionEvent.createFromAnAction(action, mouseEvent, ActionPlaces.UNKNOWN, DataManager.getInstance().getDataContext(this))
    }
    action.actionPerformed(event)
    editorModel.refresh()
  }

  override fun getData(dataId: String): Any? {
    return editorModel.getData(dataId)
  }
}

/**
 * A component to show an icon with a focus border.
 */
class IconWithFocusBorder : JBLabel(), DataProvider {
  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    if (hasFocus() && g is Graphics2D) {
      DarculaUIUtil.paintFocusBorder(g, width, height, 0f, true)
    }
  }

  override fun getData(dataId: String): Any? {
    return (parent as? DataProvider)?.getData(dataId)
  }
}
