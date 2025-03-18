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
import com.android.tools.idea.layoutinspector.ui.RenderSettings
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
 * @param color The color used to render these [bounds].
 * @param label Optional label to be rendered with the [bounds].
 */
data class DrawInstruction(
  val rootViewId: Long,
  val bounds: Rectangle,
  val color: Int,
  val label: String?,
)

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
  val inspectorModel: InspectorModel,
  private val treeSettings: TreeSettings,
  private val renderSettings: RenderSettings,
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

  private val _recomposingNodes = MutableStateFlow<List<DrawInstruction>>(emptyList())
  /** All the nodes that had a recent recomposition count change. */
  val recomposingNodes = _recomposingNodes.asStateFlow()

  private var renderSettingsState = renderSettings.toState()

  private val modificationListener =
    object : InspectorModel.ModificationListener {
      override fun onModification(
        oldWindow: AndroidWindow?,
        newWindow: AndroidWindow?,
        isStructuralChange: Boolean,
      ) {
        val newNodes = getNodes()
        setVisibleNodes(newNodes)
        // If the model is updated the DrawInstruction for the selected and hovered nodes can be
        // stale (for example if the position has changed).
        setSelectedNode(newNodes.find { it == inspectorModel.selection })
        setHoveredNode(newNodes.find { it == inspectorModel.hoveredNode })

        if (treeSettings.showRecompositions) {
          setRecomposingNodes(newNodes.filter { it.recompositions.hasHighlight })
        } else {
          setRecomposingNodes(emptyList())
        }
      }
    }

  private val selectionListener =
    object : InspectorModel.SelectionListener {
      override fun onSelection(oldNode: ViewNode?, newNode: ViewNode?, origin: SelectionOrigin) {
        setSelectedNode(newNode)
      }
    }

  private val hoverListener =
    object : InspectorModel.HoverListener {
      override fun onHover(oldNode: ViewNode?, newNode: ViewNode?) {
        setHoveredNode(newNode)
      }
    }

  private val renderSettingsListener =
    object : RenderSettings.Listener {
      override fun onChange(state: RenderSettings.State) {
        renderSettingsState = state

        if (state.drawBorders == false) {
          setVisibleNodes(emptyList())
        } else {
          setVisibleNodes(getNodes())
        }
      }
    }

  init {
    Disposer.register(parentDisposable, this)

    inspectorModel.addModificationListener(modificationListener)
    inspectorModel.addSelectionListener(selectionListener)
    inspectorModel.addHoverListener(hoverListener)

    renderSettings.modificationListeners.add(renderSettingsListener)
  }

  fun setInterceptClicks(enable: Boolean) {
    _interceptClicks.value = enable
  }

  fun selectNode(x: Double, y: Double, rootId: Long = inspectorModel.root.drawId) {
    val node = findNodesAt(x, y, rootId).firstOrNull()
    inspectorModel.setSelection(node, SelectionOrigin.INTERNAL)
  }

  fun hoverNode(x: Double, y: Double, rootId: Long = inspectorModel.root.drawId) {
    val node = findNodesAt(x, y, rootId).firstOrNull()
    inspectorModel.hoveredNode = node
  }

  /** Returns the list of visible nodes belonging to [rootId], at the provided coordinates. */
  fun findNodesAt(x: Double, y: Double, rootId: Long): List<ViewNode> {
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

    renderSettings.modificationListeners.remove(renderSettingsListener)
  }

  private fun setSelectedNode(node: ViewNode?) {
    // TODO(next CL): don't set label if disabled in render settings.
    _selectedNode.value =
      node?.toDrawInstruction(color = renderSettings.selectionColor, label = node.unqualifiedName)
  }

  private fun setHoveredNode(node: ViewNode?) {
    _hoveredNode.value = node?.toDrawInstruction(color = renderSettings.hoverColor)
  }

  /** Sets the visible nodes, while respecting render settings. */
  private fun setVisibleNodes(nodes: List<ViewNode>) {
    if (renderSettings.drawBorders) {
      _visibleNodes.value =
        nodes.mapNotNull { it.toDrawInstruction(color = renderSettings.baseColor) }
    } else {
      _visibleNodes.value = emptyList()
    }
  }

  private fun setRecomposingNodes(nodes: List<ViewNode>) {
    _recomposingNodes.value =
      nodes.mapNotNull {
        val color = renderSettings.recompositionColor.applyRecompositionAlpha(it, inspectorModel)
        it.toDrawInstruction(color = color)
      }
  }

  /** Convert a ViewNode to [DrawInstruction]. */
  private fun ViewNode.toDrawInstruction(color: Int, label: String? = null): DrawInstruction? {
    val rootView = inspectorModel.rootFor(this) ?: return null
    return DrawInstruction(
      rootViewId = rootView.drawId,
      bounds = layoutBounds,
      color = color,
      label = label,
    )
  }
}

/** Changes the alpha channel of this color based on how frequently [node] recomposed. */
private fun Int.applyRecompositionAlpha(node: ViewNode, inspectorModel: InspectorModel): Int {
  val maxAlpha = 160
  val highlightCount = node.recompositions.highlightCount
  val alpha =
    ((highlightCount * maxAlpha) / inspectorModel.maxHighlight).toInt().coerceIn(8, maxAlpha)
  return setColorAlpha(alpha)
}

/** Set the alpha for the int representation of a color. */
private fun Int.setColorAlpha(alpha: Int): Int {
  val validAlpha = alpha.coerceIn(0, 255)
  return (validAlpha shl 24) or (this and 0x00FFFFFF)
}
