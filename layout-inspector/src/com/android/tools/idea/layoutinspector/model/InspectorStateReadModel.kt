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

import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.RecomposeStateReadResult
import com.android.tools.idea.layoutinspector.stateinspection.ObservedNodes
import com.android.tools.idea.layoutinspector.stateinspection.ObservedNodes.All
import com.android.tools.idea.layoutinspector.stateinspection.ObservedNodes.None
import com.android.tools.idea.layoutinspector.stateinspection.ObservedNodes.Some
import com.android.tools.idea.layoutinspector.stateinspection.StateReadKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Data for compose State Reads */
class InspectorStateReadModel {

  /** The nodes state reads are observed for. */
  private val _observedForStateReads = MutableStateFlow<ObservedNodes>(None)
  val observedForStateReads: StateFlow<ObservedNodes> = _observedForStateReads.asStateFlow()

  /** The key that state reads are requested for. */
  private val _stateReadRequested = MutableStateFlow<StateReadKey?>(null)
  val stateReadRequested = _stateReadRequested.asStateFlow()

  /** The state reads for [stateReadRequested] */
  val stateReads = MutableStateFlow<RecomposeStateReadResult?>(null)

  fun requestStateReadFor(node: ComposeViewNode, recomposition: Int = node.recompositions.count) {
    _stateReadRequested.value = StateReadKey(node, recomposition)
  }

  fun stopShowingStateReads() {
    _stateReadRequested.value = null
  }

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
}
