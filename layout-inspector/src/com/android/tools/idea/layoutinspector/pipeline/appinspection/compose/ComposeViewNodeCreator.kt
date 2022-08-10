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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.compose

import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.FLAG_HAS_MERGED_SEMANTICS
import com.android.tools.idea.layoutinspector.model.FLAG_HAS_UNMERGED_SEMANTICS
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.ComposableNode
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetComposablesResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Quad
import java.awt.Rectangle
import java.util.EnumSet

private val composeSupport = EnumSet.of(Capability.SUPPORTS_COMPOSE)
private val semanticsSupport = EnumSet.of(Capability.SUPPORTS_COMPOSE, Capability.SUPPORTS_SEMANTICS)

/**
 * Helper class which handles the logic of using data from a [LayoutInspectorComposeProtocol.GetComposablesResponse] in order to
 * create [ComposeViewNode]s.
 */
class ComposeViewNodeCreator(result: GetComposablesResult) {
  private val response = result.response
  private val pendingRecompositionCountReset = result.pendingRecompositionCountReset
  private val stringTable = StringTableImpl(response.stringsList)
  private val roots = response.rootsList.map { it.viewId to it.nodesList }.toMap()
  private var composeFlags = 0
  private var nodesCreated = false

  /**
   * The dynamic capabilities based on the loaded data for compose.
   */
  val dynamicCapabilities: Set<Capability>
    get() {
      val hasSemantics = (composeFlags and (FLAG_HAS_MERGED_SEMANTICS or FLAG_HAS_UNMERGED_SEMANTICS)) != 0
      return when {
        nodesCreated && hasSemantics -> semanticsSupport
        nodesCreated -> composeSupport
        else -> emptySet()
      }
    }

  /**
   * AndroidViews embedded in this Compose application.
   *
   * Maps an AndroidView uniqueDrawingId to the [ComposeViewNode] that should be the parent of the Android View.
   */
  val androidViews = mutableMapOf<Long, ComposeViewNode>()

  /**
   * Views created by Compose that should be skipped.
   *
   * This includes RippleContainers with RippleHostViews.
   */
  val viewsToSkip = response.rootsList.associateBy({ it.viewId }, { it.viewsToSkipList})

  /**
   * Given an ID that should correspond to an AndroidComposeView, create a list of compose nodes
   * for any Composable children it may have.
   *
   * This can return null if the ID passed in isn't found, either because it's not an
   * AndroidComposeView or it references a view not referenced by this particular
   * [GetComposablesResponse].
   */
  fun createForViewId(id: Long, shouldInterrupt: () -> Boolean): List<ComposeViewNode>? {
    androidViews.clear()
    val result = ViewNode.writeAccess {
      roots[id]?.map { node -> node.convert(shouldInterrupt, this) }
    }
    nodesCreated = nodesCreated || (result?.isNotEmpty() ?: false)
    return result
  }

  private fun ComposableNode.convert(shouldInterrupt: () -> Boolean, access: ViewNode.WriteAccess): ComposeViewNode {
    if (shouldInterrupt()) {
      throw InterruptedException()
    }

    val layoutBounds = Rectangle(bounds.layout.x, bounds.layout.y, bounds.layout.w, bounds.layout.h)
    val renderBounds = bounds.render.takeIf { it != Quad.getDefaultInstance() }?.toShape() ?: layoutBounds
    val node = ComposeViewNode(
      id,
      stringTable[name],
      null,
      layoutBounds,
      renderBounds,
      null,
      "",
      0,
      if (pendingRecompositionCountReset) 0 else recomposeCount,
      if (pendingRecompositionCountReset) 0 else recomposeSkips,
      stringTable[filename],
      packageHash,
      offset,
      lineNumber,
      flags,
      anchorHash
    )

    composeFlags = composeFlags or flags
    access.apply {
      childrenList.mapTo(node.children) { it.convert(shouldInterrupt, access).apply { parent = node } }
    }
    if (viewId != 0L) {
      androidViews[viewId] = node
    }
    return node
  }
}
