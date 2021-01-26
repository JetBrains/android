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

import com.android.ide.common.attribution.AndroidGradlePluginAttributionData
import org.gradle.tooling.events.PluginIdentifier

/**
 * A cache object to unify [PluginData] objects and share them between different analyzers.
 */
class PluginContainer {
  private val pluginCache = HashMap<String, PluginData>()

  fun getPlugin(pluginType: PluginData.PluginType, displayName: String): PluginData {
    return pluginCache.getOrPut(PluginData.toString(pluginType, displayName)) {
      PluginData(pluginType, displayName)
    }
  }

  fun getPlugin(pluginIdentifier: PluginIdentifier?, projectPath: String): PluginData {
    return pluginCache.getOrPut(PluginData.toString(pluginIdentifier, projectPath)) {
      PluginData(pluginIdentifier, projectPath)
    }
  }

  fun updatePluginsData(agpAttributionData: AndroidGradlePluginAttributionData) {
    // Identify the build src plugins
    pluginCache.values.forEach { plugin ->
      if (agpAttributionData.buildSrcPlugins.contains(plugin.displayName)) {
        plugin.markAsBuildSrcPlugin()
      }
    }
  }

  fun clear() {
    pluginCache.clear()
  }

  fun getPlugin(pluginType: PluginData.PluginType, displayName: String, projectPath: String): PluginData {
    if (pluginType == PluginData.PluginType.SCRIPT) {
      return getPlugin(pluginType, "$projectPath:$displayName")
    }
    return getPlugin(pluginType, displayName)
  }
}
