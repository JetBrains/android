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
package com.android.tools.property.panel.impl.ui

import com.android.tools.adtui.stdui.CommonHyperLinkLabel
import com.android.tools.adtui.stdui.StandardDimensions.HORIZONTAL_PADDING
import com.android.tools.property.panel.impl.model.LinkPropertyEditorModel
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Editor for a property link consisting of a text field and a link
 */
class PropertyLink(private val model: LinkPropertyEditorModel): JPanel(BorderLayout()) {
  private val label = JBLabel(model.value)
  private val link = CommonHyperLinkLabel(showAsLink = true, strikeout = false)

  init {
    background = UIUtil.TRANSPARENT_COLOR
    isOpaque = false
    border = JBUI.Borders.empty(0, HORIZONTAL_PADDING)
    add(label, BorderLayout.WEST)
    add(link, BorderLayout.CENTER)
    link.hyperLinkListeners.add { linkActivated() }
    link.border = JBUI.Borders.empty(0, 2)
    model.addListener { updateFromModel() }
  }

  private fun updateFromModel() {
    label.text = model.value
    link.text = model.linkProperty.link.templateText
    link.isUsedInTableRendererWithSelection = model.isUsedInRendererWithSelection
    link.foreground = model.displayedForeground(UIUtil.getLabelForeground())
    background = model.displayedBackground(UIUtil.TRANSPARENT_COLOR)
  }

  private fun linkActivated() {
    val action = model.linkProperty.link
    val presentation = action.templatePresentation.clone()
    val dataContext = DataManager.getInstance().getDataContext(this)
    val manager = ActionManager.getInstance()
    val actionEvent = AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, presentation, manager, 0)
    action.actionPerformed(actionEvent)
  }
}
