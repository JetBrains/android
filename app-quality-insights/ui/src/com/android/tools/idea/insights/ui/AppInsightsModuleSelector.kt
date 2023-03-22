/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.VariantConnection
import com.android.tools.idea.insights.anyIsConfigured
import com.android.tools.idea.insights.ui.actions.AppInsightsDropDownAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.util.ui.LafIconLookup
import javax.swing.Icon
import kotlinx.coroutines.flow.StateFlow

private val checkmarkIcon
  get() = LafIconLookup.getIcon("checkmark")

class AppInsightsModuleSelector(
  text: String?,
  description: String?,
  icon: Icon?,
  private val flow: StateFlow<Selection<VariantConnection>>,
  private val onSelect: (VariantConnection) -> Unit
) : AppInsightsDropDownAction<VariantConnection>(text, description, icon, flow, null, onSelect) {
  private val notLinkedLabelAction =
    object : AnAction("Not linked to Firebase") {
      override fun actionPerformed(e: AnActionEvent) = Unit
      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = false
      }
    }

  override fun updateActions(context: DataContext): Boolean {
    removeAll()
    val selection = flow.value

    val (configuredModules, unconfiguredModules) =
      selection.items
        .groupBy { it.displayName }
        .toSortedMap()
        .asIterable()
        .partition { it.value.anyIsConfigured() }

    // Add Firebase configured modules in the dropdown.
    configuredModules.forEach { (moduleName, connections) ->
      val isGroupPreviouslySelected = flow.value.selected in connections
      val icon = if (isGroupPreviouslySelected) checkmarkIcon else null

      val subMenu =
        object : DefaultActionGroup(moduleName, null, icon) {}.apply {
          // If there's 1 configured module, we will save user actions by showing submenu items in
          // the main menu.
          isPopup = configuredModules.size > 1
          addAll(
            connections.map {
              SelectableVariantConnectionAction(
                title = if (isPopup) "${it.variantName}: ${it.connection}" else it.toString(),
                contextConnection = it,
                flow,
                onSelect
              )
            }
          )
        }

      add(subMenu)
    }

    // Add the rest modules in the dropdown, and they are separated from the above configured
    // modules.
    if (unconfiguredModules.isNotEmpty()) {
      addSeparator()

      // `addSeparator("...")` has a styling issue: the foreground color is not typical
      // separator color but menu item color. And unfortunately the styling control is
      // not exposed to us, so we introduce a disabled look action as a workaround.
      add(notLinkedLabelAction)

      unconfiguredModules.forEach { (_, connections) ->
        val placeholderConnection = connections.single()

        add(
          SelectableVariantConnectionAction(
            title = placeholderConnection.displayName,
            contextConnection = placeholderConnection,
            flow,
            onSelect
          )
        )
      }
    }

    return true
  }
}

private class SelectableVariantConnectionAction(
  title: String,
  private val contextConnection: VariantConnection,
  private val flow: StateFlow<Selection<VariantConnection>>,
  private val onSelect: (VariantConnection) -> Unit
) : ToggleAction(title) {
  override fun isSelected(e: AnActionEvent): Boolean {
    return flow.value.selected == contextConnection
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    onSelect(contextConnection)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun actionPerformed(e: AnActionEvent) {
    val isSelected = isSelected(e)

    if (isSelected) return // no-op if already selected

    setSelected(e, true)
    val presentation = e.presentation
    Toggleable.setSelected(presentation, true)
  }
}
