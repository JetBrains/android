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

import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.stateinspection.StateReadProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** The max number of [composable,recompositions] to keep state reads for. */
private const val MAX_CACHE_SIZE = 2000

/**
 * Cache for recomposition state reads.
 *
 * Data missing from the cache will be loaded via the [client] and stored as an instance of
 * [RecomposeStateReadData].
 */
class RecompositionStateReadCache(
  private val client: ComposeLayoutInspectorClient,
  private val model: InspectorModel,
  parentScope: CoroutineScope,
) : StateReadProvider {
  private val scope = parentScope.createChildScope()
  private val cache = LruCache()
  private var pendingRequest: Key? = null
  private val modificationListener =
    object : InspectorModel.ModificationListener {
      override fun onModification(
        oldWindow: AndroidWindow?,
        newWindow: AndroidWindow?,
        isStructuralChange: Boolean,
      ) {
        val pending = pendingRequest ?: return
        val composable = model.stateReadsNode as? ComposeViewNode
        if (composable == null || composable.anchorHash != pending.anchorHash) {
          // The composable from the pending request is no longer being observed:
          pendingRequest = null
          return
        }
        if (composable.recompositions.count > pending.recomposition) {
          pendingRequest = null
          scope.launch {
            // No state reads were found for the last requested composable.
            // Try again now that there have been more recompositions.
            requestRecompositionStateReads(composable, composable.recompositions.count)
          }
        }
      }
    }

  init {
    model.addModificationListener(modificationListener)
    scope.launch {
      // The ComposeInspectorClient will send an updateSettings command to the agent during creation
      // with the initial observedForStateReads value of None. There is no need to send it again.
      model.stateReadsModel.observedForStateReads.drop(1).collect {
        client.updateSettings(keepRecompositionCounts = true)
      }
    }
  }

  fun disconnect() {
    model.removeModificationListener(modificationListener)
    scope.cancel()
  }

  override suspend fun requestRecompositionStateReads(
    composable: ComposeViewNode,
    recomposition: Int,
  ) {
    val key = Key(composable.anchorHash, recomposition)
    val node = lookup(key) ?: fetchDataFor(key) ?: cache.closest(key)
    val result =
      node?.let {
        RecomposeStateReadResult(composable, node.recomposition, node.reads, node.prev != null)
      }
    model.stateReadsModel.stateReads.emit(result)
    if (result == null) {
      pendingRequest = Key(composable.anchorHash, composable.recompositions.count)
    }
  }

  // Lookup the wanted state reads in the cache.
  // Side effect: Make sure the state reads for the previous recomposition is loaded if it exists.
  private suspend fun lookup(key: Key): StateReadNode? {
    val node = cache[key] ?: return null
    if (node.recomposition == 1 || node.prev?.recomposition == key.recomposition - 1) {
      return node
    }

    // We do not have the state reads for the previous recomposition: try to load it such that the
    // UI can display an enabled previous control.
    // TODO: Add data such that we don't repeat loading the same (empty) previous state reads.
    fetchDataFor(key.prev)
    return node
  }

  /**
   * Fetch state reads data from the agent.
   *
   * @param key the composable and recomposition we want to load state reads for.
   */
  private suspend fun fetchDataFor(key: Key): StateReadNode? {
    val hasPrev = cache.contains(key.prev)
    val hasNext = cache.contains(key.next)
    val start = maxOf(1, key.recomposition - (if (!hasPrev) 4 else 0))
    val end = key.recomposition + (if (!hasNext) 4 else 0)
    val response =
      client.getRecompositionStateReads(
        anchorHash = key.anchorHash,
        recompositionNumberStart = start,
        recompositionNumberEnd = end,
        includeExtra = true,
      )
    var first: StateReadNode? = null
    convertStateRead(response, model).forEach { (recomposition, reads) ->
      val node = StateReadNode(recomposition, reads)
      cache[Key(key.anchorHash, recomposition)] = node
      first = first ?: node
    }
    if (first == null) {
      return null
    }
    if (first.recomposition > start) {
      val prev = first.prev
      if (prev != null && prev.recomposition + 1 < first.recomposition) {
        // Avoid creating "holes" in the series:
        cache.dropAllPriorTo(key.anchorHash, first)
      }
    }

    // Return the state reads closest to the recomposition requested:
    var prev = first
    var actual = first
    while (actual != null && actual.recomposition < key.recomposition) {
      prev = actual
      actual = actual.next
    }
    return actual ?: prev
  }

  fun clear() {
    cache.clear()
  }

  /** A composable and recomposition pair that may have State Read data */
  private data class Key(val anchorHash: Int, val recomposition: Int) {
    val prev: Key
      get() = Key(anchorHash, recomposition - 1)

    val next: Key
      get() = Key(anchorHash, recomposition + 1)
  }

  /** State reads for a [Key] */
  private class StateReadNode(
    /** The recomposition number of these state reads */
    val recomposition: Int,
    /** The state read data. */
    val reads: List<RecomposeStateReadData>,
  ) {
    /** The State reads for the next recomposition we have data for. */
    var next: StateReadNode? = null
    /** The State reads for the previous recomposition we have data for. */
    var prev: StateReadNode? = null
  }

  /**
   * LRU cache from [Key] to [StateReadNode]. The cache maintains a double linked list of
   * [StateReadNode] for each anchorHash value. The purpose of the linked list is to be able to
   * determine if we have any values prior go a given [StateReadNode]. The value discarded when the
   * cache is full: is the least recent accessed value.
   */
  private class LruCache : LinkedHashMap<RecompositionStateReadCache.Key, RecompositionStateReadCache.StateReadNode>(16, 0.75f, true) {
    private val top = mutableMapOf<Int, RecompositionStateReadCache.StateReadNode>()

    override fun get(key: RecompositionStateReadCache.Key): RecompositionStateReadCache.StateReadNode? {
      val node = super.get(key)
      node?.prev?.access(key.anchorHash)
      return node
    }

    override fun put(key: Key, value: StateReadNode): StateReadNode? {
      val result = super.put(key, value)

      // Update the doubly linked list in top:
      val topNode = top[key.anchorHash]
      if (result != null) {
        value.prev = result.prev
        value.next = result.next
        if (topNode != null && topNode.recomposition == value.recomposition) {
          top[key.anchorHash] = value
        }
        return result
      }
      if (topNode == null || topNode.recomposition < value.recomposition) {
        value.prev = topNode
        topNode?.next = value
        top[key.anchorHash] = value
        return null
      }
      var prev = topNode
      var node = prev.prev
      while (node != null && node.recomposition > value.recomposition) {
        prev = node
        node = node.prev
      }
      value.next = prev
      value.prev = node
      prev.prev = value
      node?.next = value
      return null
    }

    override fun remove(key: RecompositionStateReadCache.Key): RecompositionStateReadCache.StateReadNode {
      error("Not implemented")
    }

    override fun removeEldestEntry(eldest: Map.Entry<RecompositionStateReadCache.Key, RecompositionStateReadCache.StateReadNode>): Boolean {
      if (size < MAX_CACHE_SIZE) {
        return false
      }
      var prev = eldest.value.prev
      while (prev != null) {
        val key = Key(eldest.key.anchorHash, prev.recomposition)
        super.remove(key)
        prev = prev.prev
      }
      val next = eldest.value.next
      next?.prev = null
      if (next == null) {
        top.remove(eldest.key.anchorHash)
      }
      return true
    }

    override fun clear() {
      super.clear()
      top.clear()
    }

    // Move this node in front of the LRU cache i.e. less likely to be discarded
    private fun RecompositionStateReadCache.StateReadNode.access(anchorHash: Int) {
      super.get(Key(anchorHash, this.recomposition))
    }

    fun dropAllPriorTo(anchorHash: Int, node: RecompositionStateReadCache.StateReadNode) {
      var prev = node.prev
      while (prev != null) {
        super.remove(Key(anchorHash, prev.recomposition))
        prev = prev.prev
      }
      node.prev = null
    }

    fun closest(key: RecompositionStateReadCache.Key): RecompositionStateReadCache.StateReadNode? {
      var node = top[key.anchorHash] ?: return null
      var prev = node.prev
      while (prev != null && prev.recomposition > key.recomposition) {
        node = prev
        prev = prev.prev
      }
      return node
    }
  }
}
