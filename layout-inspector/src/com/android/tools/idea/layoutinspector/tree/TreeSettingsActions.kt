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
object FilterGroupAction : DropDownAction("Filter", "View Options for Component Tree", StudioIcons.Common.VISIBILITY_INLINE) {
  init {
    add(SystemNodeFilterAction)
    add(HighlightSemanticsAction)
    add(CallstackAction)
    add(RecompositionCounts)
    add(SupportLines)
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
    inspector.currentClient.stats.hideSystemNodes = state

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
    event.presentation.isVisible = isActionActive(event, Capability.SUPPORTS_SYSTEM_NODES)
  }
}

object HighlightSemanticsAction : ToggleAction("Highlight Semantics Layers") {
  override fun isSelected(event: AnActionEvent): Boolean =
    LayoutInspector.get(event)?.treeSettings?.highlightSemantics ?: DEFAULT_HIGHLIGHT_SEMANTICS

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    LayoutInspector.get(event)?.treeSettings?.highlightSemantics = state

    // Update the component tree:
    event.treePanel()?.updateSemanticsFiltering()
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isVisible = isActionActive(event, Capability.SUPPORTS_SEMANTICS)
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
    event.presentation.isVisible = isActionActive(event, Capability.SUPPORTS_COMPOSE)
  }
}

object SupportLines : ToggleAction("Show Support Lines", null, null) {

  override fun isSelected(event: AnActionEvent): Boolean =
    LayoutInspector.get(event)?.treeSettings?.supportLines ?: DEFAULT_SUPPORT_LINES

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    LayoutInspector.get(event)?.treeSettings?.supportLines = state
    event.treePanel()?.component?.repaint()
  }
}

object RecompositionCounts : ToggleAction("Show Recomposition Counts", null, null) {

  override fun isSelected(event: AnActionEvent): Boolean =
    LayoutInspector.get(event)?.treeSettings?.showRecompositions ?: DEFAULT_RECOMPOSITIONS

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    val inspector = LayoutInspector.get(event) ?: return
    inspector.treeSettings.showRecompositions = state
    inspector.currentClient.stats.showRecompositions = state
    val panel = event.treePanel()
    panel?.updateRecompositionColumnVisibility()
    panel?.resetRecompositionCounts()
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isVisible = isActionActive(event, Capability.SUPPORTS_COMPOSE) &&
                                   StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLE_RECOMPOSITION_COUNTS.get() &&
                                   StudioFlags.USE_COMPONENT_TREE_TABLE.get()
    event.presentation.isEnabled = isActionActive(event, Capability.SUPPORTS_COMPOSE_RECOMPOSITION_COUNTS)
    event.presentation.text =
      if (event.presentation.isEnabled) "Show Recomposition Counts" else "Show Recomposition Counts (Needs Compose 1.2.1+)"
  }
}

fun isActionActive(event: AnActionEvent, vararg capabilities: Capability): Boolean =
  LayoutInspector.get(event)?.currentClient?.let { client ->
    !client.isConnected || // If not running, default to visible so user can modify selection when next client is connected
    capabilities.all { client.capabilities.contains(it) }
  } ?: true
