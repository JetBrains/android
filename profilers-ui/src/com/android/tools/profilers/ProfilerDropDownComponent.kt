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

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.google.common.annotations.VisibleForTesting
import com.intellij.util.ui.JBUI
import javax.swing.Icon
import javax.swing.JPanel
import kotlinx.coroutines.flow.StateFlow

/** A component that wraps a dropdown action in a toolbar for easy use in Swing panels. */
class ProfilerDropDownComponent<T>(
  text: String?,
  description: String?,
  icon: Icon?,
  flow: StateFlow<Selection<T>>,
  getIconForValue: ((T) -> Icon?)?,
  onSelect: (T) -> Unit,
  getDisplayTitle: (T?) -> String = { it.toString() },
) : JPanel()
{
  @get:VisibleForTesting
  internal val dropDownAction: ProfilerDropDownAction<T>

  init {
    layout = java.awt.BorderLayout()
    dropDownAction = ProfilerDropDownAction(
      text,
      description,
      icon,
      flow,
      getIconForValue,
      onSelect,
      getDisplayTitle
    )

    val group = DefaultActionGroup()
    group.add(dropDownAction)

    val toolbar = ActionManager.getInstance().createActionToolbar("ProfilerDropDownComponent", group, true)
    toolbar.targetComponent = this
    toolbar.component.isOpaque = false
    toolbar.component.border = JBUI.Borders.empty()

    add(toolbar.component, java.awt.BorderLayout.CENTER)
  }
}