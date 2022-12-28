/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.insights.ui.actions

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.insights.Selection
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import javax.swing.Icon
import javax.swing.JComponent
import kotlinx.coroutines.flow.StateFlow

private class SimpleAction(title: String, icon: Icon?, private val action: () -> Unit) :
  AnAction(title, null, icon) {
  override fun actionPerformed(e: AnActionEvent) = action()
}

/** A dropdown whose state depends entirely on the provided [flow]. */
open class AppInsightsDropDownAction<T>(
  text: String?,
  description: String?,
  icon: Icon?,
  private val flow: StateFlow<Selection<T>>,
  private val getIconForValue: ((T) -> Icon?)?,
  private val onSelect: (T) -> Unit
) : DropDownAction(text, description, icon) {
  override fun updateActions(context: DataContext): Boolean {
    removeAll()
    val selection = flow.value
    selection.items.forEach { item ->
      add(SimpleAction(item.toString(), getIconForValue?.invoke(item)) { onSelect(item) })
    }
    return true
  }

  override fun update(e: AnActionEvent) {
    e.presentation.text = flow.value.selected.toString()
  }

  override fun displayTextInToolbar() = true

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    component.repaint()
  }
}
