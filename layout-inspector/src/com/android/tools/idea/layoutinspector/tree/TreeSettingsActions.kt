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
package com.android.tools.idea.layoutinspector.tree

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.ui.DEVICE_VIEW_MODEL_KEY
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import icons.StudioIcons

/**
 * This file contains view options for the component tree.
 */

/**
 * Filter menu group
 */
object FilterGroupAction : DropDownAction("Filter", null, StudioIcons.Common.FILTER) {
  init {
    add(SystemNodeFilterAction)
    add(MergedSemanticsFilterAction)
    add(UnmergedSemanticsFilterAction)
  }
}

/**
 * Filter system nodes from view hierarchy and compose hierarchy.
 */
object SystemNodeFilterAction : ToggleAction("Filter System-Defined Layers") {
  override fun isSelected(event: AnActionEvent): Boolean =
    TreeSettings.hideSystemNodes

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    TreeSettings.hideSystemNodes = state

    if (state) {
      val model = LayoutInspector.get(event)?.layoutInspectorModel
      val selectedNode = model?.selection
      if (selectedNode?.isInComponentTree == false) {
        model.setSelection(selectedNode.findClosestUnfilteredNode(), SelectionOrigin.COMPONENT_TREE)
      }
      val hoveredNode = model?.hoveredNode
      if (hoveredNode?.isInComponentTree == false) {
        model.hoveredNode = null
      }
    }

    // Update the component tree:
    event.treePanel()?.refresh()

    event.getData(DEVICE_VIEW_MODEL_KEY)?.refresh()
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isVisible =
      LayoutInspector.get(event)?.currentClient?.let { client ->
        !client.isConnected // If not running, default to visible so user can modify selection when next client is connected
        || client.capabilities.contains(InspectorClient.Capability.SUPPORTS_SYSTEM_NODES)
      }
      ?: true
  }
}

object MergedSemanticsFilterAction : ToggleAction("Show merged semantics tree") {
  override fun isSelected(event: AnActionEvent): Boolean =
    TreeSettings.mergedSemanticsTree

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    TreeSettings.mergedSemanticsTree = state
    event.treePanel()?.refresh()
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isVisible = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_SHOW_SEMANTICS.get()
  }
}

object UnmergedSemanticsFilterAction : ToggleAction("Show unmerged semantics tree") {
  override fun isSelected(event: AnActionEvent): Boolean =
    TreeSettings.unmergedSemanticsTree

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    TreeSettings.unmergedSemanticsTree = state
    event.treePanel()?.refresh()
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isVisible = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_SHOW_SEMANTICS.get()
  }
}

object CallstackAction : ToggleAction("Show Compose as Callstack", null, null) {

  override fun isSelected(event: AnActionEvent): Boolean =
    TreeSettings.composeAsCallstack

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    TreeSettings.composeAsCallstack = state
    event.treePanel()?.refresh()
  }
}

object SupportLines : ToggleAction("Show Support Lines", null, null) {

  override fun isSelected(event: AnActionEvent): Boolean = TreeSettings.supportLines

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    TreeSettings.supportLines = state
    event.tree()?.repaint()
  }
}
