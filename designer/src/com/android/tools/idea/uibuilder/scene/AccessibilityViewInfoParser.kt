/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene

import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.android.ide.common.rendering.api.ViewInfo

/**
 * Custom parser to create a [ViewInfo] hierarchy based on the [AccessibilityNodeInfo] tree instead
 * of the [View] tree.
 */
val accessibilityBasedHierarchyParser = { view: Any ->
  if (view !is View) {
    emptyList()
  } else {
    val nodeInfo: AccessibilityNodeInfo = view.createAccessibilityNodeInfo()
    nodeInfo.setQueryFromAppProcessEnabled(view, true)
    parseChildren(view, nodeInfo, 0, 0)
  }
}

private fun parseChildren(
  rootView: View,
  nodeInfo: AccessibilityNodeInfo,
  parentX: Int,
  parentY: Int
): List<ViewInfo> {
  val childCount = nodeInfo.childCount
  val children: MutableList<ViewInfo> = ArrayList(childCount)
  for (i in 0 until childCount) {
    val childNodeInfo = nodeInfo.getChild(i)
    val bounds = childNodeInfo.boundsInScreen

    // Create a ViewInfo for each AccessibilityNodeInfo.
    // Use the root view as the viewObject.
    // Bounds in ViewInfo are with respect to the parent.
    val result =
      ViewInfo(
        childNodeInfo.className.toString(),
        null,
        bounds.left - parentX,
        bounds.top - parentY,
        bounds.right - parentX,
        bounds.bottom - parentY,
        rootView,
        childNodeInfo,
        rootView.layoutParams
      )

    result.children = parseChildren(rootView, childNodeInfo, bounds.left, bounds.top)
    children.add(result)
  }
  return children
}

/**
 * Returns the text coming from the [AccessibilityNodeInfo] associated with this [ViewInfo],
 * or null if there is no [AccessibilityNodeInfo].
 */
fun ViewInfo.getAccessibilityText() = (accessibilityObject as? AccessibilityNodeInfo)?.text?.toString()

/**
 * Returns the source id from the [AccessibilityNodeInfo] associated with this [ViewInfo].
 * If the [AccessibilityNodeInfo] does not exist, but viewObject is a [View], this creates
 * an [AccessibilityNodeInfo] for that [View] first.
 */
fun ViewInfo.getAccessibilitySourceId(): Long {
  if (accessibilityObject != null) {
    return (accessibilityObject as AccessibilityNodeInfo).sourceNodeId
  } else {
    val node = (viewObject as? View)?.createAccessibilityNodeInfo() ?: return -1
    return node.sourceNodeId
  }
}
