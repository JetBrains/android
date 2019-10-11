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
package com.android.build.attribution.data

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.gradle.tooling.events.PluginIdentifier

/**
 * A cache object to unify [PluginData] objects and share them between different analyzers.
 */
data class PluginContainer(private val pluginCache: Cache<String, PluginData> = CacheBuilder.newBuilder().build<String, PluginData>()) {
  fun getPlugin(pluginType: PluginData.PluginType, displayName: String): PluginData {
    val pluginData = PluginData(pluginType, displayName)
    return pluginCache.get(pluginData.toString()) {
      pluginData
    }
  }

  fun getPlugin(pluginIdentifier: PluginIdentifier?, projectPath: String): PluginData {
    val pluginData = PluginData(pluginIdentifier, projectPath)
    return pluginCache.get(pluginData.toString()) {
      pluginData
    }
  }

  fun clear() {
    pluginCache.invalidateAll()
  }
}
