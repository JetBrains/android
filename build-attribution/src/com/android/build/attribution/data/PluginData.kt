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

data class PluginData(val pluginType: PluginType, val displayName: String) {
  enum class PluginType {
    UNKNOWN,
    PLUGIN,
    SCRIPT
  }

  constructor(pluginIdentifier: PluginIdentifier?) : this(getPluginType(pluginIdentifier), getPluginName(pluginIdentifier))

  fun equals(pluginIdentifier: PluginIdentifier?): Boolean {
    if (pluginIdentifier == null) {
      return pluginType == PluginType.UNKNOWN
    }
    return pluginType == getPluginType(pluginIdentifier) && displayName == getPluginName(pluginIdentifier)
  }

  override fun toString(): String {
    return when (pluginType) {
      PluginType.UNKNOWN -> "unknown plugin"
      PluginType.PLUGIN -> "plugin $displayName"
      PluginType.SCRIPT -> "script $displayName"
    }
  }

  companion object {
    private fun getPluginType(pluginIdentifier: PluginIdentifier?): PluginType {
      return when (pluginIdentifier) {
        is BinaryPluginIdentifier -> PluginType.PLUGIN
        is ScriptPluginIdentifier -> PluginType.SCRIPT
        else -> PluginType.UNKNOWN
      }
    }

    private fun getPluginName(pluginIdentifier: PluginIdentifier?): String {
      if (pluginIdentifier == null) {
        return ""
      }
      if (pluginIdentifier.displayName.startsWith("com.android.internal.")) {
        return pluginIdentifier.displayName.replace("com.android.internal.", "com.android.")
      }
      return pluginIdentifier.displayName
    }
  }
}
