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
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import java.awt.Component
import java.awt.Point
import javax.swing.JComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.annotations.TestOnly

private fun allSelected(name: String) = "All $name"

private fun noneSelected(name: String) = "No $name selected"

class TreeDropDownAction<ValueT, ValueGroupT : GroupAware<ValueGroupT>>(
  private val name: String,
  flow: Flow<MultiSelection<WithCount<ValueT>>>,
  private val scope: CoroutineScope,
  private val groupNameSupplier: (ValueT) -> String,
  private val nameSupplier: (ValueT) -> String,
  private val secondaryGroupSupplier: (ValueT) -> Set<ValueGroupT> = { emptySet() },
  private val onSelected: (Set<ValueT>) -> Unit,
  private val secondaryTitleSupplier: () -> JComponent? = { null },
  @TestOnly private val getLocationOnScreen: Component.() -> Point = Component::getLocationOnScreen
) : DropDownAction(null, null, null) {
  @VisibleForTesting
  val selectionState = flow.stateIn(scope, SharingStarted.Eagerly, MultiSelection.emptySelection())

  @VisibleForTesting
  val isDisabled =
    flow
      .map { selection -> selection.items.isEmpty() }
      .stateIn(scope, SharingStarted.Eagerly, false)

  @VisibleForTesting
  val titleState =
    flow
      .map { selection ->
        val groupedValues = selection.items.groupBy { groupNameSupplier(it.value) }
        val groupedSelection = selection.selected.groupBy { groupNameSupplier(it.value) }
        generateTitle(selection, groupedSelection, groupedValues)
      }
      .stateIn(scope, SharingStarted.Eagerly, allSelected(name))

  private fun generateTitle(
    selection: MultiSelection<WithCount<ValueT>>,
    groupedSelection: Map<String, List<WithCount<ValueT>>>,
    groupedValues: Map<String, List<WithCount<ValueT>>>
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
    val dropdown =
      TreeDropDownPopup(
        selectionState.value,
        scope,
        groupNameSupplier,
        nameSupplier,
        secondaryGroupSupplier,
        secondaryTitleSupplier
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

    val owner = eve.inputEvent.component
    val location = getLocationOnScreen(owner)
    location.translate(0, owner.height)
    popup.showInScreenCoordinates(owner, location)
  }

  override fun displayTextInToolbar() = true

  override fun update(e: AnActionEvent) {
    e.presentation.text = titleState.value
    e.presentation.isEnabled = !isDisabled.value
  }
}
