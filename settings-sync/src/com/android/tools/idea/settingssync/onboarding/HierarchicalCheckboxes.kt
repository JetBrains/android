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
package com.android.tools.idea.settingssync.onboarding

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.intellij.openapi.components.SettingsCategory
import com.intellij.settingsSync.core.SettingsSyncState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DropdownLink
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TriStateCheckboxRow
import org.jetbrains.jewel.ui.component.items

/**
 * Represents a node in a hierarchical checkbox structure.
 *
 * Each node can have multiple children, forming a tree. The state of a checkbox can be
 * [ToggleableState.On], [ToggleableState.Off], or [ToggleableState.Indeterminate] (if it has
 * children with mixed states).
 *
 * @property id A unique identifier for this checkbox node.
 * @property label The user-facing label text for the checkbox.
 * @property description Additional descriptive text providing more context about the checkbox.
 * @property isChildrenComplete Indicates whether [children] contains all the possible children for
 *   this node. This affects how the parent's indeterminate state is calculated. If true, the
 *   parent's state will be indeterminate only if some children are checked. If false, the parent's
 *   state will be indeterminate if no children are checked, assuming there might be more checked
 *   children not present in the list.
 * @property children A list of child [CheckboxNode]s.
 */
internal class CheckboxNode(
  val id: String,
  val label: String,
  val description: String,
  val isChildrenComplete: Boolean,
  val children: List<CheckboxNode> = emptyList(),
) {
  /**
   * The current checked state of this checkbox. Indeterminate state is typically derived from the
   * state of its children.
   */
  var isCheckedState by mutableStateOf(ToggleableState.On)

  fun toggle() {
    isCheckedState =
      when (isCheckedState) {
        ToggleableState.On -> ToggleableState.Off
        ToggleableState.Off -> ToggleableState.On
        ToggleableState.Indeterminate -> ToggleableState.On
      }
  }

  fun refreshChildrenStates() {
    children.forEach { child ->
      when (isCheckedState) {
        ToggleableState.On,
        ToggleableState.Off -> child.isCheckedState = isCheckedState
        ToggleableState.Indeterminate -> Unit
      }
    }
  }

  fun refreshFromChildren() {
    val allChildrenChecked = children.all { it.isCheckedState == ToggleableState.On }
    val someChildrenChecked =
      !isChildrenComplete || children.any { it.isCheckedState == ToggleableState.On }

    isCheckedState =
      when {
        allChildrenChecked -> ToggleableState.On
        someChildrenChecked -> ToggleableState.Indeterminate
        else -> ToggleableState.Off
      }
  }
}

/**
 * Converts an IJ defined [Category] object into a [CheckboxNode] for the following rendering
 * purposes.
 */
internal fun Category.toCheckboxNode(state: SettingsSyncState): CheckboxNode {
  return CheckboxNode(
      id = name,
      label = name,
      description = description,
      isChildrenComplete = secondaryGroup?.isComplete() != false,
      children =
        secondaryGroup?.getDescriptors()?.map {
          it.toSubCheckboxNode(parentCategory = category, state)
        } ?: emptyList(),
    )
    .apply {
      isCheckedState =
        if (state.isCategoryEnabled(category)) ToggleableState.On else ToggleableState.Off
    }
}

/**
 * Converts an IJ defined [SettingsSyncSubcategoryDescriptor] object into a sub-[CheckboxNode] for
 * the following rendering purposes.
 */
internal fun SettingsSyncSubcategoryDescriptor.toSubCheckboxNode(
  parentCategory: SettingsCategory,
  state: SettingsSyncState,
): CheckboxNode {
  return CheckboxNode(id = id, label = name, description = "", isChildrenComplete = true).apply {
    isCheckedState =
      if (state.isSubcategoryEnabled(parentCategory, id)) ToggleableState.On
      else ToggleableState.Off
  }
}

@Composable
internal fun HierarchicalCheckboxes(checkboxNodes: List<CheckboxNode>) {
  LazyColumn(modifier = Modifier.fillMaxSize()) {
    items(checkboxNodes.size) { index ->
      CheckboxItem(checkboxNodes.elementAt(index))
      Spacer(Modifier.height(8.dp))
    }
  }
}

@Composable
internal fun CheckboxItem(topLevelNode: CheckboxNode, indentation: Int = 0) {
  val interactionSource = remember { MutableInteractionSource() }

  TriStateCheckboxRow(
    modifier = Modifier.padding(start = (indentation * 16).dp),
    state = topLevelNode.isCheckedState,
    onClick = {
      topLevelNode.toggle()
      topLevelNode.refreshChildrenStates()
    },
    interactionSource = interactionSource,
  ) {
    Text(text = topLevelNode.label)
    Spacer(Modifier.width(8.dp))
    Text(text = topLevelNode.description, color = JewelTheme.globalColors.text.info)

    Spacer(Modifier.width(8.dp))

    // Configure dropdown link
    if (
      topLevelNode.children.isNotEmpty() &&
        (topLevelNode.isChildrenComplete || topLevelNode.isCheckedState != ToggleableState.Off)
    ) {
      val allChildNodes = topLevelNode.children

      fun CheckboxNode.onChildNodeClick() {
        toggle()
        topLevelNode.refreshFromChildren()
      }

      DropdownLink(text = "Configure") {
        items(
          count = allChildNodes.count(),
          isSelected = { index -> allChildNodes[index].isCheckedState == ToggleableState.On },
          onItemClick = { index -> allChildNodes[index].onChildNodeClick() },
        ) { index ->
          CheckboxRow(
            checked = allChildNodes[index].isCheckedState == ToggleableState.On,
            onCheckedChange = { allChildNodes[index].onChildNodeClick() },
          ) {
            Text(allChildNodes[index].label)
          }
        }
      }
    }
  }
}
