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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.view

import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.layoutinspector.model.InspectorModel
import kotlinx.coroutines.withContext
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.GetPropertiesResponse

/**
 * Cache of view properties, to avoid expensive refetches when possible.
 */
class ViewPropertiesCache(
  private val client: ViewLayoutInspectorClient,
  private val model: InspectorModel
) {
  private var lastGeneration = 0
  private val cache = mutableMapOf<Long, ViewPropertiesData>()

  /**
   * Request [ViewPropertiesData] cached against the passed in [viewId].
   *
   * This could be null if the viewId is invalid (e.g. stale).
   */
  suspend fun fetch(viewId: Long): ViewPropertiesData? {
    if (lastGeneration == model.lastGeneration) {
      val cached = cache[viewId]
      if (cached != null) {
        return cached
      }
    }

    // Don't update the cache if we're not actively communicating with the inspector. Otherwise,
    // we might override values with those that don't match our last snapshot.
    if (!client.isFetchingContinuously) return null

    return withContext(AndroidDispatchers.workerThread) {
      updateCache(client.fetchProperties(viewId))
    }
  }

  private fun updateCache(properties: GetPropertiesResponse): ViewPropertiesData? {
    if (properties.viewId == 0L || properties.generation < model.lastGeneration) return null

    if (lastGeneration < properties.generation) {
      lastGeneration = properties.generation
      cache.clear()
    }

    val data = ViewPropertiesDataGenerator(properties, model).generate()
    cache[properties.viewId] = data
    return data
  }
}
