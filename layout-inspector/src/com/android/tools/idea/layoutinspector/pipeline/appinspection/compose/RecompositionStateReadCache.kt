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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.compose

import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.InspectorModel.StateReadsNodeListener
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import java.util.WeakHashMap
import kotlinx.coroutines.launch
// TODO merge
//import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.RecompositionStateRead
//import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.RecompositionStateReadEvent

/**
 * Cache for recomposition state reads.
 *
 * Data missing from the cache will be loaded via the [client] and stored as an instance of
 * [RecomposeStateReadData].
 */
class RecompositionStateReadCache(
  client: ComposeLayoutInspectorClient?,
  private val model: InspectorModel,
  private val treeSettings: TreeSettings,
) {
  private data class Key(val anchorHash: Int, val recomposition: Int)

  private val onDemandCache = OnDemandStateReadCache(client, model)
  private val trackAllCache = StateReadForAllCache(client, model)
  private val listener = StateReadsNodeListener { view ->
    val node = view as? ComposeViewNode
    if (node != null) {
      if (!treeSettings.observeStateReadsForAll) {
        model.scope.launch { onDemandCache.startObserving(node) }
      }
    } else {
      clear()
    }
  }

  suspend fun requestRecomposeStateReads(node: ComposeViewNode, recomposition: Int) {
    return if (treeSettings.observeStateReadsForAll) {
      trackAllCache.requestRecomposeStateReads(node, recomposition)
    } else {
      onDemandCache.requestRecomposeStateReads(node, recomposition)
    }
  }

  init {
    model.addStateReadsNodeListener(listener)
  }

  fun disconnect() {
    model.removeStateReadsNodeListener(listener)
  }

  // TODO merge
  //fun handleEvent(event: RecompositionStateReadEvent) {
  //  if (!treeSettings.observeStateReadsForAll) {
  //    onDemandCache.handleEvent(event)
  //  }
  //}

  fun clear() {
    onDemandCache.clear()
    trackAllCache.clear()
  }

  /**
   * A cache for the "observeStateReadsForAll" mode. The state reads are kept in a weak hash map,
   * any data lost in low memory situation can most likely be retrieved from the device. Only if the
   * agent reached the max recompositions with state reads the data is lost.
   */
  private class StateReadForAllCache(
    private val client: ComposeLayoutInspectorClient?,
    private val model: InspectorModel,
  ) {
    private val cache: MutableMap<Key, List<RecomposeStateReadData>> = WeakHashMap()
    // These values may not be exact, since the agent may discard state reads at any time.
    private val firstRecompositions = mutableMapOf<Int, Int>()

    suspend fun requestRecomposeStateReads(node: ComposeViewNode, recomposition: Int) {
      val key = Key(node.anchorHash, recomposition)
      val result =
        cache[key]?.let {
          val firstRecomposition = firstRecompositions[node.anchorHash] ?: 1
          RecomposeStateReadResult(node, recomposition, it, firstRecomposition)
        } ?: fetchDataFor(node, recomposition)
      model.stateReadsModel.stateReads.tryEmit(result)
    }

    fun clear() {
      cache.clear()
      firstRecompositions.clear()
    }

    private suspend fun fetchDataFor(
      node: ComposeViewNode,
      recomposition: Int,
    ): RecomposeStateReadResult? {
      val response = client?.getRecompositionStateReads(node.anchorHash, recomposition)
      // TODO merge
      return TODO()
      //if (response == null || response.read == RecompositionStateRead.getDefaultInstance()) {
      //  return null
      //}
      //val reads = convertStateRead(response, model)
      //val firstRecomposition = response.firstRecomposition
      //val actualRecomposition = response.read.recompositionNumber
      //val key = Key(node.anchorHash, actualRecomposition)
      //cache[key] = reads
      //firstRecompositions[node.anchorHash] = firstRecomposition
      //return RecomposeStateReadResult(node, key.recomposition, reads, firstRecomposition)
    }
  }

  /**
   * A cache for the "onDemand" mode i.e. when "observeStateReadsForAll" is OFF. The state reads are
   * kept in a strong hash map. The data is deleted from the device when it is sent to Studio. Only
   * the state reads from a single composable will be kept in the cache at any given time.
   */
  private class OnDemandStateReadCache(
    private val client: ComposeLayoutInspectorClient?,
    private val model: InspectorModel,
  ) {
    private val lock = Any()
    private var anchorHashObserved = 0
    private val cache = mutableMapOf<Key, List<RecomposeStateReadData>>()
    private var firstRecomposition = 0

    suspend fun startObserving(node: ComposeViewNode) {
      if (node.anchorHash != anchorHashObserved) {
        clear()
        anchorHashObserved = node.anchorHash
        client?.updateSettings(keepRecompositionCounts = true)
      }
    }

    suspend fun requestRecomposeStateReads(node: ComposeViewNode, recomposition: Int) {
      val key = Key(node.anchorHash, recomposition)
      val result =
        cache[key]?.let { RecomposeStateReadResult(node, recomposition, it, firstRecomposition) }
          ?: fetchDataFor(node, recomposition)
      model.stateReadsModel.stateReads.tryEmit(result)
    }

    // TODO merge
    //fun handleEvent(event: RecompositionStateReadEvent) {
    //  synchronized(lock) {
    //    val node = model.stateReadsNode as? ComposeViewNode ?: return
    //    if (event.anchorHash != node.anchorHash) {
    //      return
    //    }
    //    val cacheWasEmpty = cache.isEmpty()
    //    convertStateReadEvent(event, model) { recomposition, readList ->
    //      cache[Key(anchorHashObserved, recomposition)] = readList
    //    }
    //    if (cacheWasEmpty && cache.isNotEmpty()) {
    //      val first = cache.keys.first()
    //      firstRecomposition = first.recomposition
    //      model.stateReadsModel.stateReads.tryEmit(
    //        RecomposeStateReadResult(node, firstRecomposition, cache[first]!!, firstRecomposition)
    //      )
    //    }
    //  }
    //}

    fun clear() {
      synchronized(lock) {
        cache.clear()
        firstRecomposition = 0
        anchorHashObserved = 0
      }
    }

    /**
     * Request state reads for a node and recomposition number. If we don't have state reads
     * available the device request will result in future state reads being observed.
     */
    private suspend fun fetchDataFor(
      node: ComposeViewNode,
      recomposition: Int,
    ): RecomposeStateReadResult? {
      val response =
        client?.getRecompositionStateReads(node.anchorHash, recomposition) ?: return null

      synchronized(lock) {
        if (response.anchorHash != anchorHashObserved) {
          // If we started observing a different composable before receiving this result
          // for an earlier composable: just ignore the result.
          return null
        }
        // TODO merge
        //if (response.read != RecompositionStateRead.getDefaultInstance()) {
        //  // The state reads existed on the device: return it now.
        //  val reads = convertStateRead(response, model)
        //  val key = Key(response.anchorHash, response.read.recompositionNumber)
        //  cache[key] = reads
        //  return RecomposeStateReadResult(
        //    node,
        //    key.recomposition,
        //    reads,
        //    cache.keys.first().recomposition,
        //  )
        //}
      }
      return null
    }
  }
}
