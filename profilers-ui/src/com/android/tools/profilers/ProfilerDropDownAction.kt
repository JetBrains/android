/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.profilers

import com.android.tools.adtui.actions.DropDownAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.ui.awt.RelativePoint
import java.awt.Point
import javax.swing.Icon
import javax.swing.JComponent
import kotlinx.coroutines.flow.StateFlow

/** A simple, private action for items within the dropdown menu. */
private class SimpleAction(title: String, icon: Icon?, private val action: () -> Unit) :
  AnAction(title, null, icon) {
  override fun actionPerformed(e: AnActionEvent) = action()
}
/**
 * A dropdown action driven by a [StateFlow] of [Selection]s, used for profiler toolbars.
 * This is a modified version of `com.android.tools.idea.insights.ui.actions.AppInsightsDropDownAction`.
 */
open class ProfilerDropDownAction<T>(
  text: String?,
  description: String?,
  icon: Icon?,
  private val flow: StateFlow<Selection<T>>,
  private val getIconForValue: ((T) -> Icon?)?,
  private val onSelect: (T) -> Unit,
  private val getDisplayTitle: (T?) -> String = { it.toString() },
) : DropDownAction(text, description, icon) {
  /** Rebuilds the dropdown's items from the [flow]'s contents. */
  public override fun updateActions(context: DataContext): Boolean {
    removeAll()
    addAll(flow.value.items.map(::createActionForItem))
    return true
  }

  /** Ensures UI updates for this action happen on a background thread. */
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  /** Updates the dropdown's button text to reflect the current selection. */
  override fun update(e: AnActionEvent) {
    e.presentation.setText(getDisplayTitle(flow.value.selected), false)
    e.presentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
  }

  /** Repaints the component to ensure text/icon updates are reflected. */
  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    component.repaint()
  }

  /** Customizes the popup menu's position to appear correctly below the dropdown button. */
  override fun showPopupMenu(eve: AnActionEvent, button: ActionButton) {
    val popUpMenu = ActionManager.getInstance().createActionPopupMenu(eve.place, this)

    val buttonHeight = button.height / 2 + button.border.getBorderInsets(button).bottom
    val toolbarComponent: JComponent? = button.parent as? JComponent
    val toolbarHeight = if (toolbarComponent != null) {
      toolbarComponent.height + toolbarComponent.insets.top + toolbarComponent.insets.bottom
    }
    else {
      0
    }

    JBPopupMenu.showAt(
      RelativePoint(button, Point(0, buttonHeight + toolbarHeight)),
      popUpMenu.component,
    )
  }

  /** Creates a selection action for a given item. */
  private fun createActionForItem(item: T): AnAction {
    return SimpleAction(getDisplayTitle(item), getIconForValue?.invoke(item)) {
      if (item != flow.value.selected) {
        onSelect(item)
      }
    }
  }
}
