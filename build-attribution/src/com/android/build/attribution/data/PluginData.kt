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

import org.gradle.tooling.events.BinaryPluginIdentifier
import org.gradle.tooling.events.PluginIdentifier
import org.gradle.tooling.events.ScriptPluginIdentifier

class PluginData(pluginType: PluginType, val displayName: String) {
  var pluginType: PluginType = pluginType
    private set

  enum class PluginType {
    UNKNOWN,
    BINARY_PLUGIN,
    BUILDSRC_PLUGIN,
    SCRIPT
  }

  constructor(pluginIdentifier: PluginIdentifier?, projectPath: String) : this(getPluginType(pluginIdentifier),
                                                                               getPluginName(pluginIdentifier, projectPath))

  override fun toString(): String = toString(pluginType, displayName)

  override fun equals(other: Any?): Boolean {
    return other is PluginData &&
           displayName == other.displayName
  }

  override fun hashCode(): Int {
    return displayName.hashCode()
  }

  fun markAsBuildSrcPlugin() {
    this.pluginType = PluginType.BUILDSRC_PLUGIN
  }

  companion object {
    fun toString(pluginIdentifier: PluginIdentifier?, projectPath: String): String = toString(getPluginType(pluginIdentifier),
                                                                                              getPluginName(pluginIdentifier, projectPath))

    fun toString(pluginType: PluginType, displayName: String): String {
      return when (pluginType) {
        PluginType.UNKNOWN -> "unknown plugin"
        PluginType.BINARY_PLUGIN -> "binary plugin $displayName"
        PluginType.BUILDSRC_PLUGIN -> "buildSrc plugin $displayName"
        PluginType.SCRIPT -> "script $displayName"
      }
    }

    private fun getPluginType(pluginIdentifier: PluginIdentifier?): PluginType {
      return when (pluginIdentifier) {
        is BinaryPluginIdentifier -> PluginType.BINARY_PLUGIN
        is ScriptPluginIdentifier -> PluginType.SCRIPT
        else -> PluginType.UNKNOWN
      }
    }

    private fun getPluginName(pluginIdentifier: PluginIdentifier?, projectPath: String): String {
      if (pluginIdentifier == null) {
        return ""
      }
      if (pluginIdentifier.displayName.startsWith("com.android.internal.")) {
        return pluginIdentifier.displayName.replace("com.android.internal.", "com.android.")
      }
      if (pluginIdentifier is ScriptPluginIdentifier) {
        return "$projectPath:${pluginIdentifier.displayName}"
      }
      return pluginIdentifier.displayName
    }
  }
}
