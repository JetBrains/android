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
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
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
object FilterGroupAction : DropDownAction("Filter", "View options for Component Tree", StudioIcons.Common.VISIBILITY_INLINE) {
  init {
    add(SystemNodeFilterAction)
    add(MergedSemanticsFilterAction)
    add(UnmergedSemanticsFilterAction)
    add(CallstackAction)
    add(SupportLines)
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isVisible = isActionVisible(event, Capability.SUPPORTS_SYSTEM_NODES, Capability.SUPPORTS_SEMANTICS)
  }
}

/**
 * Filter system nodes from view hierarchy and compose hierarchy.
 */
object SystemNodeFilterAction : ToggleAction("Filter System-Defined Layers") {
  override fun isSelected(event: AnActionEvent): Boolean =
    LayoutInspector.get(event)?.treeSettings?.hideSystemNodes ?: DEFAULT_HIDE_SYSTEM_NODES

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    val inspector = LayoutInspector.get(event) ?: return
    val treeSettings = inspector.treeSettings
    treeSettings.hideSystemNodes = state

    if (state) {
      val model = inspector.layoutInspectorModel
      val selectedNode = model.selection
      if (selectedNode != null && !selectedNode.isInComponentTree(treeSettings)) {
        model.setSelection(selectedNode.findClosestUnfilteredNode(treeSettings), SelectionOrigin.COMPONENT_TREE)
      }
      val hoveredNode = model.hoveredNode
      if (hoveredNode != null && !hoveredNode.isInComponentTree(treeSettings)) {
        model.hoveredNode = null
      }
    }

    // Update the component tree:
    event.treePanel()?.refresh()

    event.getData(DEVICE_VIEW_MODEL_KEY)?.refresh()
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isVisible = isActionVisible(event, Capability.SUPPORTS_SYSTEM_NODES)
  }
}

object MergedSemanticsFilterAction : ToggleAction("Show merged semantics tree") {
  override fun isSelected(event: AnActionEvent): Boolean =
    LayoutInspector.get(event)?.treeSettings?.mergedSemanticsTree ?: DEFAULT_MERGED_SEMANTICS_TREE

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    LayoutInspector.get(event)?.treeSettings?.mergedSemanticsTree = state
    event.treePanel()?.refresh()
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isVisible =
      StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_SHOW_SEMANTICS.get() &&
      isActionVisible(event, Capability.SUPPORTS_SEMANTICS)
  }
}

object UnmergedSemanticsFilterAction : ToggleAction("Show unmerged semantics tree") {
  override fun isSelected(event: AnActionEvent): Boolean =
    LayoutInspector.get(event)?.treeSettings?.unmergedSemanticsTree ?: DEFAULT_UNMERGED_SEMANTICS_TREE

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    LayoutInspector.get(event)?.treeSettings?.unmergedSemanticsTree = state
    event.treePanel()?.refresh()
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isVisible =
      StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_SHOW_SEMANTICS.get() &&
      isActionVisible(event, Capability.SUPPORTS_SEMANTICS)
  }
}

object CallstackAction : ToggleAction("Show Compose as Callstack", null, null) {

  override fun isSelected(event: AnActionEvent): Boolean =
    LayoutInspector.get(event)?.treeSettings?.composeAsCallstack ?: DEFAULT_COMPOSE_AS_CALLSTACK

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    LayoutInspector.get(event)?.treeSettings?.composeAsCallstack = state
    event.treePanel()?.refresh()
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isVisible =
      StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_SHOW_SEMANTICS.get() &&
      isActionVisible(event, Capability.SUPPORTS_COMPOSE)
  }
}

object SupportLines : ToggleAction("Show Support Lines", null, null) {

  override fun isSelected(event: AnActionEvent): Boolean =
    LayoutInspector.get(event)?.treeSettings?.supportLines ?: DEFAULT_SUPPORT_LINES

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    LayoutInspector.get(event)?.treeSettings?.supportLines = state
    event.tree()?.repaint()
  }
}

private fun isActionVisible(event: AnActionEvent, vararg capabilities: Capability): Boolean =
  LayoutInspector.get(event)?.currentClient?.let { client ->
    !client.isConnected || // If not running, default to visible so user can modify selection when next client is connected
    capabilities.any { client.capabilities.contains(it) }
  } ?: true
