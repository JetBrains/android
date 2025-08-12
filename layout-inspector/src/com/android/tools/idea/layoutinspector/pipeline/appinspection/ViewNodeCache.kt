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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * A base class for caching data associated with [ViewNode] that is additionally nested within a
 * layout tree (so if the root of that layout tree is dropped, all data cached within it is removed
 * as well).
 *
 * This class also encapsulates the idea of fetching data from the device if it's not found locally.
 *
 * @param D The type of cached data.
 */
abstract class ViewNodeCache<D>(protected val model: InspectorModel) {

  /**
   * If true, allow fetching data from the device if we don't have it in our local cache.
   *
   * We provide this lever because sometimes the inspector is in snapshot mode, and we don't want to
   * pull data from the device that might be newer than what we see in our snapshot.
   */
  var allowFetching = false

  // Specifically, this is a Map<RootId, Map<ViewId, Data>>()
  // Occasionally, roots are discarded, so we can drop whole branches of cached data in that case.
  private val cache: MutableMap<Long, ConcurrentHashMap<Long, D>> = ConcurrentHashMap()

  /** Remove all nested data for views that are children to [rootId]. */
  fun clearFor(rootId: Long) {
    cache.remove(rootId)
  }

  /**
   * Remove all nested data for views that are not children under the passed in list of IDs.
   *
   * This is a useful method to call when an old root window is removed.
   */
  fun retain(rootIdsToKeep: Iterable<Long>) {
    cache.keys.removeAll { rootId -> !rootIdsToKeep.contains(rootId) }
  }

  /**
   * Request data cached against the passed in [node].
   *
   * This may initiate a fetch to device if the data is not locally cached already.
   *
   * This may also ultimately return null if the viewId is invalid (e.g. stale, and no longer found
   * inside the model).
   */
  suspend fun getDataFor(node: ViewNode): D? {
    val root = model.rootFor(node) ?: return null // Unrooted nodes are not supported
    val cached = cache[root.drawId]?.get(node.drawId)
    if (cached != null) {
      return cached
    }

    // Don't update the cache if we're not actively communicating with the inspector. Otherwise,
    // we might override values with those that don't match our last snapshot.
    if (!allowFetching) return null

    return withContext(AndroidDispatchers.workerThread) {
      val data = fetchDataFor(root, node)
      if (data != null) {
        setDataFor(root.drawId, node.drawId, data)
      }
      data
    }
  }

  fun getCachedDataFor(rootId: Long, composeId: Long): D? = cache[rootId]?.get(composeId)

  protected abstract suspend fun fetchDataFor(root: ViewNode, node: ViewNode): D?

  protected fun setDataFor(rootId: Long, viewId: Long, data: D) {
    val innerMap = cache.computeIfAbsent(rootId) { ConcurrentHashMap() }
    innerMap[viewId] = data
  }

  fun clear() {
    cache.clear()
  }
}
