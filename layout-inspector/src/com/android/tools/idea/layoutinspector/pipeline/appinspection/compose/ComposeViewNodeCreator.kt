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
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.ComposableNode
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetComposablesResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Quad

/**
 * Helper class which handles the logic of using data from a [LayoutInspectorComposeProtocol.GetComposablesResponse] in order to
 * create [ComposeViewNode]s.
 */
class ComposeViewNodeCreator(response: GetComposablesResponse) {
  private val stringTable = StringTableImpl(response.stringsList)
  private val nodes = response.rootsList.map { it.viewId to it.nodesList }.toMap()

  /**
   * Given an ID that should correspond to an AndroidComposeView, create a list of compose nodes
   * for any Composable children it may have.
   *
   * This can return null if the ID passed in isn't found, either because it's not an
   * AndroidComposeView or it references a view not referenced by this particular
   * [GetComposablesResponse].
   */
  fun createForViewId(id: Long, shouldInterrupt: () -> Boolean): List<ComposeViewNode>? {
    return nodes[id]?.map { node -> node.convert(shouldInterrupt) }
  }

  private fun ComposableNode.convert(shouldInterrupt: () -> Boolean): ComposeViewNode {
    if (shouldInterrupt()) {
      throw InterruptedException()
    }

    val node = ComposeViewNode(
      id,
      stringTable[name],
      null,
      bounds.layout.x,
      bounds.layout.y,
      bounds.layout.w,
      bounds.layout.h,
      bounds.render.takeIf { it != Quad.getDefaultInstance() }?.toShape(),
      null,
      "",
      0,
      stringTable[filename],
      packageHash,
      offset,
      lineNumber
    )

    childrenList.mapTo(node.children) { it.convert(shouldInterrupt).apply { parent = node } }
    return node
  }
}
