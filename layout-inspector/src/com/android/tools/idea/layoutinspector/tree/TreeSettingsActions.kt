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
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.snapshots.FileEditorInspectorClient
import com.android.tools.idea.layoutinspector.ui.RenderModel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import icons.StudioIcons

/** This file contains view options for the component tree. */

/** Filter menu group */
class FilterGroupAction(renderModelProvider: () -> RenderModel?) :
  DropDownAction(
    "Filter",
    "View Options for Component Tree",
    StudioIcons.Common.VISIBILITY_INLINE,
  ) {
  init {
    add(SystemNodeFilterAction(renderModelProvider))
    add(HighlightSemanticsAction)
    add(CallstackAction)
    add(RecompositionCounts)
    add(SupportLines)
  }
}

/** Filter system nodes from view hierarchy and compose hierarchy. */
class SystemNodeFilterAction(private val renderModelProvider: () -> RenderModel?) :
  ToggleAction("Filter System-Defined Layers") {
  override fun isSelected(event: AnActionEvent): Boolean =
    LayoutInspector.get(event)?.treeSettings?.hideSystemNodes ?: DEFAULT_HIDE_SYSTEM_NODES

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    val inspector = LayoutInspector.get(event) ?: return
    val treeSettings = inspector.treeSettings
    treeSettings.hideSystemNodes = state
    inspector.currentClient.stats.hideSystemNodes = state

    if (state) {
      val model = inspector.inspectorModel
      val selectedNode = model.selection
      if (selectedNode != null && !selectedNode.isInComponentTree(treeSettings)) {
        model.setSelection(
          selectedNode.findClosestUnfilteredNode(treeSettings),
          SelectionOrigin.COMPONENT_TREE,
        )
      }
      val hoveredNode = model.hoveredNode
      if (hoveredNode != null && !hoveredNode.isInComponentTree(treeSettings)) {
        model.hoveredNode = null
      }
    }

    // Update the component tree:
    event.treePanel()?.refresh()
    renderModelProvider()?.refresh()
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

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

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

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

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isVisible = isActionActive(event, Capability.SUPPORTS_COMPOSE)
  }
}

object SupportLines : ToggleAction("Show Support Lines", null, null) {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

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

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isVisible =
      isActionActive(event, Capability.SUPPORTS_COMPOSE) &&
        LayoutInspector.get(event)?.currentClient !is FileEditorInspectorClient

    // The compose inspector is tracking the recompositions based on a compiler generated key
    // based on the source information. If the source information is missing we cannot track
    // recompositions.
    event.presentation.isEnabled =
      isActionActive(
        event,
        Capability.SUPPORTS_COMPOSE_RECOMPOSITION_COUNTS,
        Capability.HAS_LINE_NUMBER_INFORMATION,
      )
    event.presentation.text =
      if (event.presentation.isEnabled || !isActionActive(event)) "Show Recomposition Counts"
      else if (!isActionActive(event, Capability.HAS_LINE_NUMBER_INFORMATION))
        "Show Recomposition Counts (No Source Information Found)"
      else "Show Recomposition Counts (Needs Compose 1.2.1+)"
  }
}

fun isActionActive(event: AnActionEvent, vararg capabilities: Capability): Boolean =
  LayoutInspector.get(event)?.currentClient?.let { client ->
    // If not running, default to visible so user can modify selection when next client is connected
    !client.isConnected || capabilities.all { client.capabilities.contains(it) }
  } ?: true

fun inLiveMode(event: AnActionEvent): Boolean =
  // If not running, default to visible so user can modify selection when next client is connected
  LayoutInspector.get(event)?.currentClient?.let { client ->
    client.inLiveMode || !client.isConnected
  } ?: true
