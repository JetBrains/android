/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.model

// Empty recomposition counts instance to be used when no recomposition numbers are available.
val emptyRecompositionData = RecompositionData(0, 0)

/**
 * The recomposition counts for a [ComposeViewNode] or a combination of nodes.
 *
 * @param count the number of recompositions
 * @param skips the number of times that recomposition was skipped
 * @param childCount the max number of recompositions among the children of this node
 * @param highlightCount a number that expresses relative recent counts for image highlighting
 */
class RecompositionData(
  var count: Int,
  var skips: Int,
  var childCount: Int = 0,
  var highlightCount: Float = 0f,
) {
  val isEmpty: Boolean
    get() = count == 0 && skips == 0 && highlightCount == 0f

  val hasHighlight: Boolean
    get() = highlightCount > 0f

  fun reset() {
    count = 0
    skips = 0
    childCount = 0
    highlightCount = 0f
  }

  fun maxOf(node: ViewNode) {
    (node as? ComposeViewNode)?.let { maxOf(it.recompositions) }
  }

  fun maxOf(other: RecompositionData) {
    count = maxOf(count, other.count)
    skips = maxOf(skips, other.skips)
    highlightCount = maxOf(highlightCount, other.highlightCount)
  }

  fun addChildCount(child: RecompositionData) {
    childCount = maxOf(childCount, maxOf(child.count, child.childCount))
  }

  fun update(newNumbers: RecompositionData) {
    highlightCount += maxOf(0, newNumbers.count - count)
    count = newNumbers.count
    skips = newNumbers.skips
    childCount = newNumbers.childCount
  }

  fun decreaseHighlights(): Float {
    highlightCount =
      if (highlightCount > DECREASE_BREAK_OFF) highlightCount / DECREASE_FACTOR else 0f
    return highlightCount
  }
}
