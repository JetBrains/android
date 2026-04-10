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

import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.RecompositionDetailsResult
import com.android.tools.idea.layoutinspector.recompositions.ObservedNodes
import com.android.tools.idea.layoutinspector.recompositions.ObservedNodes.All
import com.android.tools.idea.layoutinspector.recompositions.ObservedNodes.None
import com.android.tools.idea.layoutinspector.recompositions.ObservedNodes.Some
import com.android.tools.idea.layoutinspector.recompositions.RecompositionKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Data for compose recompositions */
class InspectorRecompositionModel {

  /** The nodes recompositions are observed for. */
  private val _observedForRecompositions = MutableStateFlow<ObservedNodes>(None)
  val observedForRecompositions: StateFlow<ObservedNodes> = _observedForRecompositions.asStateFlow()

  /** The key that recomposition data is requested for. */
  private val _recompositionDataRequested = MutableStateFlow<RecompositionKey?>(null)
  val recompositionDataRequested = _recompositionDataRequested.asStateFlow()

  /** The recomposition details for [recompositionDataRequested] */
  val recompositionDetails = MutableStateFlow<RecompositionDetailsResult?>(null)

  fun requestRecompositionDataFor(node: ComposeViewNode, recomposition: Int = node.recompositions.count) {
    _recompositionDataRequested.value = RecompositionKey(node, recomposition)
  }

  fun stopShowingRecompositionDetails() {
    _recompositionDataRequested.value = null
    recompositionDetails.value = null
  }

  fun observeNode(node: ComposeViewNode) {
    val current = _observedForRecompositions.value
    _observedForRecompositions.value =
      when (current) {
        is All -> All // Switch from All to Some is not supported
        is None -> Some(setOf(node.anchorHash))
        is Some -> Some(current.nodeAnchors + node.anchorHash)
      }
  }

  fun stopObservingNode(node: ComposeViewNode) {
    val current = _observedForRecompositions.value
    _observedForRecompositions.value =
      when (current) {
        is All -> All // Switch from All to Some is not supported
        is None -> None
        is Some -> (current.nodeAnchors - node.anchorHash).let { remaining -> if (remaining.isEmpty()) None else Some(remaining) }
      }
  }

  fun observeAll() {
    _observedForRecompositions.value = All
  }

  fun observeNone() {
    _observedForRecompositions.value = None
  }

  fun isObservingAll(): Boolean = _observedForRecompositions.value == All

  fun isObservingAny(): Boolean = _observedForRecompositions.value != None

  fun isNodeObserved(node: ComposeViewNode): Boolean {
    return when (val current = _observedForRecompositions.value) {
      is All -> true
      is None -> false
      is Some -> current.nodeAnchors.contains(node.anchorHash)
    }
  }
}
