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
import org.gradle.tooling.events.BinaryPluginIdentifier
import org.gradle.tooling.events.PluginIdentifier
import org.gradle.tooling.events.ScriptPluginIdentifier

/**
 * A cache object to unify [PluginData] objects and share them between different analyzers.
 */
class PluginContainer {
  private val pluginCache = HashMap<String, PluginData>()
  private val pluginDisplayNamesToPlugin = HashMap<PluginData.DisplayName, PluginData>()

  fun getPlugin(pluginIdentifier: PluginIdentifier?, projectPath: String): PluginData {
    val pluginIdName = getPluginIdName(pluginIdentifier, projectPath)
    return pluginCache.getOrPut(pluginIdName) { PluginData(getPluginType(pluginIdentifier), pluginIdName) }.also { plugin ->
      if (pluginIdentifier != null) {
        val displayName = getPluginDisplayName(pluginIdentifier, projectPath)
        plugin.recordDisplayName(displayName)
        pluginDisplayNamesToPlugin[displayName] = plugin
      }
    }
  }

  fun findPluginByName(pluginName: String, projectPath: String): PluginData? {
    return pluginDisplayNamesToPlugin[PluginData.DisplayName(pluginName.cleanUpInternalPluginName(), projectPath)]
           ?: pluginCache[pluginName.cleanUpInternalPluginName()]
  }

  fun updatePluginsData(agpAttributionData: AndroidGradlePluginAttributionData) {
    // Identify the build src plugins
    pluginCache.values.forEach { plugin ->
      if (plugin.displayNames().any { agpAttributionData.buildSrcPlugins.contains(it) }) {
        plugin.markAsBuildSrcPlugin()
      }
    }
  }

  fun clear() {
    pluginCache.clear()
    pluginDisplayNamesToPlugin.clear()
  }

  private fun getPluginType(pluginIdentifier: PluginIdentifier?): PluginData.PluginType {
    return when (pluginIdentifier) {
      is BinaryPluginIdentifier -> PluginData.PluginType.BINARY_PLUGIN
      is ScriptPluginIdentifier -> PluginData.PluginType.SCRIPT
      else -> PluginData.PluginType.UNKNOWN
    }
  }

  private fun getPluginIdName(pluginIdentifier: PluginIdentifier?, projectPath: String): String = when (pluginIdentifier) {
    null -> ""
    is BinaryPluginIdentifier -> pluginIdentifier.className.cleanUpInternalPluginName()
    is ScriptPluginIdentifier -> "$projectPath:${pluginIdentifier.displayName}"
    else -> pluginIdentifier.displayName
  }

  private fun getPluginDisplayName(pluginIdentifier: PluginIdentifier, projectPath: String): PluginData.DisplayName = when (pluginIdentifier) {
    is ScriptPluginIdentifier -> PluginData.DisplayName("$projectPath:${pluginIdentifier.displayName}", projectPath)
    else -> PluginData.DisplayName(pluginIdentifier.displayName.cleanUpInternalPluginName(), projectPath)
  }

  private fun String.cleanUpInternalPluginName() = if (startsWith("com.android.internal.")) {
    replace("com.android.internal.", "com.android.")
  }
  else this
}
