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
import com.android.tools.idea.insights.GroupAware
import com.android.tools.idea.insights.MultiSelection
import com.android.tools.idea.insights.WithCount
import com.android.tools.idea.insights.ui.TreeDropDownPopup
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.annotations.TestOnly

private fun allSelected(name: String) = "All $name"

private fun noneSelected(name: String) = "No $name selected"

/** The maximum number of items the dropdown can show before it refuses to display anything. */
val MAX_DROPDOWN_ITEMS = 3000

class TreeDropDownAction<ValueT, ValueGroupT : GroupAware<ValueGroupT>>(
  private val name: String,
  flow: Flow<MultiSelection<WithCount<ValueT>>>,
  private val scope: CoroutineScope,
  private val enabledFlow: StateFlow<Boolean>,
  private val groupNameSupplier: (ValueT) -> String,
  private val nameSupplier: (ValueT) -> String,
  private val secondaryGroupSupplier: (ValueT) -> Set<ValueGroupT> = { emptySet() },
  private val onSelected: (Set<ValueT>) -> Unit,
  private val secondaryTitleSupplier: () -> JComponent? = { null },
  @TestOnly private val getLocationOnScreen: Component.() -> Point = Component::getLocationOnScreen,
) : DropDownAction(null, null, null) {
  @VisibleForTesting
  val selectionState = flow.stateIn(scope, SharingStarted.Eagerly, MultiSelection.emptySelection())

  @VisibleForTesting
  val titleState =
    flow
      .map { selection ->
        val groupedValues = selection.items.groupBy { groupNameSupplier(it.value) }
        val groupedSelection = selection.selected.groupBy { groupNameSupplier(it.value) }
        generateTitle(selection, groupedSelection, groupedValues)
      }
      .stateIn(scope, SharingStarted.Eagerly, allSelected(name))

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  private fun generateTitle(
    selection: MultiSelection<WithCount<ValueT>>,
    groupedSelection: Map<String, List<WithCount<ValueT>>>,
    groupedValues: Map<String, List<WithCount<ValueT>>>,
  ) =
    if (selection.allSelected()) {
      allSelected(name)
    } else if (selection.selected.isEmpty()) {
      noneSelected(name)
    } else if (groupedSelection.size == 1) {
      val selectedCodes = groupedSelection.values.first()
      val allCodes = groupedValues.getValue(groupedSelection.keys.first())
      if (selectedCodes.size == allCodes.size) {
        groupedSelection.keys.first()
      } else if (selectedCodes.size == 1) {
        val firstCode = selectedCodes.first()
        val name = nameSupplier(firstCode.value)
        val group = groupNameSupplier(firstCode.value)
        if (name == group) name else "$group ($name)"
      } else {
        "${groupedSelection.keys.first()} (${selectedCodes.size} $name)"
      }
    } else {
      "${selection.selected.size} $name"
    }

  override fun actionPerformed(eve: AnActionEvent) {
    val popup =
      if (selectionState.value.items.size > MAX_DROPDOWN_ITEMS) {
        createFilterUnavailablePopup()
      } else {
        createFilterPopup()
      }
    val owner = eve.inputEvent!!.component
    val location = getLocationOnScreen(owner)
    location.translate(0, owner.height)
    popup.showInScreenCoordinates(owner, location)
  }

  // This is a workaround for the performance issue caused by having
  // too many items in the tree drop down.
  private fun createFilterUnavailablePopup(): JBPopup {
    val panel =
      JPanel(BorderLayout()).apply {
        isOpaque = true
        border = JBUI.Borders.empty(6)
      }
    val label = JBLabel("Filter unavailable: too many items.")
    panel.add(label)
    return JBPopupFactory.getInstance()
      .createComponentPopupBuilder(panel, null)
      .setCancelOnClickOutside(true)
      .setCancelOnOtherWindowOpen(true)
      .setFocusable(true)
      .setRequestFocus(true)
      .createPopup()
  }

  private fun createFilterPopup(): JBPopup {
    val dropdown =
      TreeDropDownPopup(
        selectionState.value,
        scope,
        groupNameSupplier,
        nameSupplier,
        secondaryGroupSupplier,
        secondaryTitleSupplier,
      )
    val popup = dropdown.asPopup()

    popup.addListener(
      object : JBPopupListener {
        override fun onClosed(event: LightweightWindowEvent) {
          Logger.getInstance(TreeDropDownPopup::class.java)
            .info("Requesting ${dropdown.selection.selected}")
          onSelected(dropdown.selection.selected.map { it.value }.toSet())
        }
      }
    )
    return popup
  }

  override fun displayTextInToolbar() = true

  override fun update(e: AnActionEvent) {
    e.presentation.text = titleState.value
    e.presentation.isEnabled = enabledFlow.value
  }
}
