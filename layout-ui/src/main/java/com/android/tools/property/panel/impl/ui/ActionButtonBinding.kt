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

import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.property.panel.api.HelpSupport
import com.android.tools.property.panel.api.TableExpansionState
import com.android.tools.property.panel.impl.model.BasePropertyEditorModel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataProvider
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A standard class for implementing a browse button for an editor.
 *
 * The editor component is wrapped in panel with a possible icon to the right displaying of the
 * editor.
 */
class ActionButtonBinding(
  private val model: BasePropertyEditorModel,
  private val editor: JComponent,
) : JPanel(BorderLayout()), DataProvider {

  private val actionButtonModel
    get() = model.property.browseButton

  private val actionButton = ButtonWithCustomTooltip(actionButtonModel?.action)
  private var initialized = false

  init {
    add(editor, BorderLayout.CENTER)
    add(actionButton, BorderLayout.EAST)
    updateFromModel()

    model.addListener(ValueChangedListener { updateFromModel() })
    background = secondaryPanelBackground
    initialized = true
  }

  override fun requestFocus() {
    editor.requestFocus()
  }

  private fun updateFromModel() {
    actionButton.icon = model.displayedIcon(actionButtonModel?.actionIcon)
    // Hide the action button if this is for an expanded table cell renderer.
    // Since the user cannot click on the popup, it doesn't make sense to show it.
    actionButton.isVisible = model.tableExpansionState == TableExpansionState.NORMAL
    background = model.displayedBackground(secondaryPanelBackground)
    isVisible = model.visible
  }

  override fun getData(dataId: String): Any? {
    if (HelpSupport.PROPERTY_ITEM.`is`(dataId)) {
      return model.property
    }
    return null
  }

  override fun updateUI() {
    super.updateUI()
    if (initialized) {
      // We allow the action icon to change during a LaF change:
      actionButton.icon = actionButtonModel?.actionIcon
    }
  }

  private inner class ButtonWithCustomTooltip(action: AnAction?) : IconWithFocusBorder({ action }) {

    override fun getToolTipText(event: MouseEvent): String? {
      // Trick: Use the component from the event.source for tooltip in tables. See
      // TableEditor.getToolTip().
      val component = event.source as? JComponent ?: this
      PropertyTooltip.setToolTip(
        component,
        actionButtonModel?.action?.templatePresentation?.description,
      )
      return null
    }
  }
}
