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
package com.android.tools.idea.layoutinspector.runningdevices.ui.rendering

import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.awt.Rectangle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Draw instructions to render the bounds of a node.
 *
 * @param rootViewId The drawId of the root view the [bounds] belong to.
 * @param bounds The bounds of the node being rendered.
 */
data class DrawInstruction(val rootViewId: Long, val bounds: Rectangle)

/**
 * Contains state that controls the rendering of the view bounds. It is different from
 * [InspectorModel], which contains state about the inspector in general, like shared state between
 * bounds rendering and component tree (like selected and hovered nodes), client etc.
 *
 * This is a new render model, currently used only for on-device rendering. This render model is
 * agnostic to on-device rendering and is designed to be used by any [LayoutInspectorRenderer] in
 * embedded Layout Inspector. Once standalone Layout Inspector and 3D view are removed, this should
 * take over as the only render model.
 *
 * Embedded Layout Inspector could already use this render model for all its renderers, but since
 * standalone Layout Inspector is still around, it's best if both standalone and embedded share the
 * same render model, to make it easier to find bugs which would otherwise be visible only when
 * using one of the two.
 */
class OnDeviceRendererModel(
  parentDisposable: Disposable,
  private val inspectorModel: InspectorModel,
  private val treeSettings: TreeSettings,
) : Disposable {

  private val _interceptClicks = MutableStateFlow<Boolean>(false)
  /** When true, prevents clicks from being dispatched to the app. */
  val interceptClicks = _interceptClicks.asStateFlow()

  private val _visibleNodes = MutableStateFlow<List<DrawInstruction>>(emptyList())
  /** All the nodes currently visible on screen. */
  val visibleNodes = _visibleNodes.asStateFlow()

  private val _selectedNode = MutableStateFlow<DrawInstruction?>(null)
  val selectedNode = _selectedNode.asStateFlow()

  private val _hoveredNode = MutableStateFlow<DrawInstruction?>(null)
  val hoveredNode = _hoveredNode.asStateFlow()

  private val modificationListener =
    object : InspectorModel.ModificationListener {
      override fun onModification(
        oldWindow: AndroidWindow?,
        newWindow: AndroidWindow?,
        isStructuralChange: Boolean,
      ) {
        val newNodes = getNodes()
        _visibleNodes.value = newNodes.mapNotNull { it.toDrawInstruction() }
        // If the model is updated the DrawInstruction for the selected and hovered nodes can be
        // stale (for example if the position has changed).
        _selectedNode.value = newNodes.find { it == inspectorModel.selection }?.toDrawInstruction()
        _hoveredNode.value = newNodes.find { it == inspectorModel.hoveredNode }?.toDrawInstruction()
      }
    }

  private val selectionListener =
    object : InspectorModel.SelectionListener {
      override fun onSelection(oldNode: ViewNode?, newNode: ViewNode?, origin: SelectionOrigin) {
        _selectedNode.value = newNode?.toDrawInstruction()
      }
    }

  private val hoverListener =
    object : InspectorModel.HoverListener {
      override fun onHover(oldNode: ViewNode?, newNode: ViewNode?) {
        _hoveredNode.value = newNode?.toDrawInstruction()
      }
    }

  init {
    Disposer.register(parentDisposable, this)

    inspectorModel.addModificationListener(modificationListener)
    inspectorModel.addSelectionListener(selectionListener)
    inspectorModel.addHoverListener(hoverListener)
  }

  fun setInterceptClicks(enable: Boolean) {
    _interceptClicks.value = enable
  }

  fun selectNode(x: Double, y: Double, rootId: Long = inspectorModel.root.drawId) {
    val node = findViewNodesAt(rootId, x, y).firstOrNull()
    inspectorModel.setSelection(node, SelectionOrigin.INTERNAL)
  }

  fun hoverNode(x: Double, y: Double, rootId: Long = inspectorModel.root.drawId) {
    val node = findViewNodesAt(rootId, x, y).firstOrNull()
    inspectorModel.hoveredNode = node
  }

  /** Returns the list of visible nodes belonging to [rootId], at the provided coordinates. */
  private fun findViewNodesAt(rootId: Long, x: Double, y: Double): List<ViewNode> {
    return getNodes(rootId).filter { it.layoutBounds.contains(x, y) }
  }

  /** Returns all the visible nodes belonging to [rootId]. */
  private fun getNodes(rootId: Long = inspectorModel.root.drawId): List<ViewNode> {
    return inspectorModel[rootId]
      ?.flattenedList()
      ?.filter { inspectorModel.isVisible(it) }
      ?.filter {
        !treeSettings.hideSystemNodes || (treeSettings.hideSystemNodes && !it.isSystemNode)
      } ?: emptyList()
  }

  override fun dispose() {
    inspectorModel.removeModificationListener(modificationListener)
    inspectorModel.removeSelectionListener(selectionListener)
    inspectorModel.removeHoverListener(hoverListener)
  }

  /** Convert a ViewNode to [DrawInstruction]. */
  private fun ViewNode.toDrawInstruction(): DrawInstruction? {
    val rootView = inspectorModel.rootFor(this) ?: return null
    return DrawInstruction(rootViewId = rootView.drawId, bounds = layoutBounds)
  }
}
