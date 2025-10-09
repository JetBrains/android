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
package com.android.tools.idea.layoutinspector.model

import com.android.tools.idea.layoutinspector.common.ephemeralFlow
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.RecomposeStateReadResult
import com.android.tools.idea.layoutinspector.stateinspection.ObservedNodes
import com.android.tools.idea.layoutinspector.stateinspection.ObservedNodes.All
import com.android.tools.idea.layoutinspector.stateinspection.ObservedNodes.None
import com.android.tools.idea.layoutinspector.stateinspection.ObservedNodes.Some
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Data for compose State Reads */
class InspectorStateReadModel {

  /** The nodes state reads are observed for. */
  private val _observedForStateReads = MutableStateFlow<ObservedNodes>(None)
  val observedForStateReads: StateFlow<ObservedNodes> = _observedForStateReads.asStateFlow()

  /** The state reads currently selected for [InspectorModel.stateReadsNode] */
  val stateReads = ephemeralFlow<RecomposeStateReadResult?>()

  fun observeNode(node: ComposeViewNode) {
    val current = _observedForStateReads.value
    _observedForStateReads.value =
      when (current) {
        is All -> All // Switch from All to Some is not supported
        is None -> Some(setOf(node))
        is Some -> Some(current.nodes + node)
      }
  }

  fun stopObservingNode(node: ComposeViewNode) {
    val current = _observedForStateReads.value
    _observedForStateReads.value =
      when (current) {
        is All -> All // Switch from All to Some is not supported
        is None -> None
        is Some ->
          (current.nodes - node).let { remaining ->
            if (remaining.isEmpty()) None else Some(remaining)
          }
      }
  }

  // TODO(b/452847216): How do we handle nodes that appear under [node] after observeSubtree is
  // called.
  fun observeSubtree(node: ComposeViewNode) {
    val current = _observedForStateReads.value
    val subTree = subTree(node)
    _observedForStateReads.value =
      when (current) {
        is All -> All // Switch from All to Some is not supported
        is None -> Some(subTree)
        is Some -> Some(current.nodes + subTree)
      }
  }

  fun stopObservingSubtree(node: ComposeViewNode) {
    val current = _observedForStateReads.value
    val subTree = subTree(node)
    _observedForStateReads.value =
      when (current) {
        is All -> All // Switch from All to Some is not supported
        is None -> Some(subTree)
        is Some ->
          (current.nodes - subTree).let { remaining ->
            if (remaining.isEmpty()) None else Some(remaining)
          }
      }
  }

  fun observeAll() {
    _observedForStateReads.value = All
  }

  fun observeNone() {
    _observedForStateReads.value = None
  }

  fun isObservingAll(): Boolean = _observedForStateReads.value == All

  fun isObservingAny(): Boolean = _observedForStateReads.value != None

  fun isNodeObserved(node: ComposeViewNode): Boolean {
    return when (val current = _observedForStateReads.value) {
      is All -> true
      is None -> false
      is Some -> current.nodes.contains(node)
    }
  }

  fun isSubTreeObserved(node: ComposeViewNode): Boolean {
    return when (val current = _observedForStateReads.value) {
      is All -> true
      is None -> false
      is Some -> current.nodes.containsAll(subTree(node))
    }
  }

  private fun subTree(view: ViewNode): MutableSet<ComposeViewNode> =
    ViewNode.readAccess { view.flatten().filterIsInstance<ComposeViewNode>() }.toMutableSet()
}
