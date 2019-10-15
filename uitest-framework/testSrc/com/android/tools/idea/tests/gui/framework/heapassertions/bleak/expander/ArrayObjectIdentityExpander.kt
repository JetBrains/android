/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.heapassertions.bleak.expander

import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.Edge
import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.HeapGraph
import gnu.trove.TObjectHash
import java.lang.ref.WeakReference

/** [ArrayObjectIdentityExpander] expands arrays, creating a child node for each non-null element,
 * with an [ObjectLabel] as the edge label. This means objects in arrays can be tracked regardless
 * of their position in the array, which is particularly important for array-backed data structures
 * like ArrayList and maps where elements move around (e.g. as the result of inserting an element
 * in a list, or rehashing a HashMap (though the possibility of the creation of TreeNodes for
 * overly full buckets throws a wrench in things - CollectionExpander might be more appropriate)).
 *
 * For large arrays, the default implementation of getChildForLabel is slow (linear in the size of
 * the array). For arrays larger than LABEL_MAP_DEGREE_THRESHOLD, a map from labels back to nodes
 * is maintained to accelerate lookups. For small arrays, the extra memory usage is not worth the
 * small (or possibly negative) performance improvement.
 */
class ArrayObjectIdentityExpander(g: HeapGraph): Expander(g) {
  private val labelToNodeMap: MutableMap<Node, MutableMap<Label, Node>> = mutableMapOf()

  // primitive arrays should be expanded by DefaultObjectExpander so we don't end up with nodes for primitive types
  override fun canExpand(obj: Any): Boolean = obj.javaClass.isArray && !obj.javaClass.componentType.isPrimitive

  override fun canPotentiallyGrowIndefinitely(n: Node) = true

  override fun expand(n: Node) {
    val arr = n.obj as Array<*>
    val map = if (arr.size > LABEL_MAP_DEGREE_THRESHOLD) {
      labelToNodeMap[n] = mutableMapOf()
      labelToNodeMap[n]
    } else null
    for (obj in arr) {
      if (obj != null && obj !== TObjectHash.REMOVED && (TRACK_WEAK_REFS_IN_ARRAYS || obj !is WeakReference<*>)) {
        val label = ObjectLabel(obj)
        val childNode = n.addEdgeTo(obj, label)
        if (map != null && map[label] == null) {
          map[label] = childNode
        }
      }
    }
  }

  override fun expandCorrespondingEdge(n: Node, e: Edge): Node? {
    return if ((n.obj as Array<*>).any { it === e.end.obj }) n.addEdgeTo(e.end.obj, ObjectLabel(e.end.obj)) else null
  }

  override fun getChildForLabel(n: Node, label: Label): Node? = labelToNodeMap[n]?.get(label) ?: super.getChildForLabel(n, label)

  companion object {
    private const val LABEL_MAP_DEGREE_THRESHOLD = 50
    private val TRACK_WEAK_REFS_IN_ARRAYS = System.getProperty("bleak.track.weak.refs.in.arrays") == "true"
  }
}

