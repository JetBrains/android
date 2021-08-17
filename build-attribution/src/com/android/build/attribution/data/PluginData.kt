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

import com.intellij.util.containers.mapSmartSet

/**
 * Plugin representation in build analysis.
 * Only single instance is created for each plugin, plugins are matched by [idName],
 * which is a plugin class for binary plugins and "projectPath:fileName" for scripts.
 */
class PluginData(pluginType: PluginType, val idName: String) {
  var pluginType: PluginType = pluginType
    private set

  private val projectToDisplayName = mutableMapOf<String, DisplayName>()

  enum class PluginType {
    UNKNOWN,
    BINARY_PLUGIN,
    BUILDSRC_PLUGIN,
    SCRIPT
  }

  data class DisplayName(
    val name: String,
    /** Project path where plugin is defined by this name.*/
    val projectPath: String
  )

  override fun toString(): String = when (pluginType) {
    PluginType.UNKNOWN -> "unknown plugin"
    PluginType.BINARY_PLUGIN -> "binary plugin $idName"
    PluginType.BUILDSRC_PLUGIN -> "buildSrc plugin $idName"
    PluginType.SCRIPT -> "script $idName"
  }

  override fun equals(other: Any?): Boolean {
    return other is PluginData &&
           idName == other.idName
  }

  override fun hashCode(): Int {
    return idName.hashCode()
  }

  fun markAsBuildSrcPlugin() {
    this.pluginType = PluginType.BUILDSRC_PLUGIN
  }


  fun recordDisplayName(displayName: DisplayName) = projectToDisplayName.put(displayName.projectPath, displayName)

  fun displayNameInProject(project: String): String = projectToDisplayName[project]?.name ?: displayName

  fun displayNames(): Set<String> = projectToDisplayName.values.mapSmartSet { it.name }

  /**
   * Tries to select the best display name for this plugin from the ones defined in this project.
   * Normally there should only be a single name used in all sub-projects but otherwise select the first one.
   */
  val displayName: String
    get() = displayNames().minBy { it.length } ?: idName.takeIf { it.isNotBlank() } ?: "Unknown plugin"


  fun isJavaPlugin(): Boolean {
    return displayNames().any {
      it == "application" ||
      it == "java" ||
      it == "java-base" ||
      it == "java-gradle-plugin" ||
      it == "java-library" ||
      it == "java-platform"
    }
  }

  fun isAndroidPlugin(): Boolean {
    return idName.startsWith("com.android.build.gradle.")
  }

  fun isKotlinPlugin(): Boolean {
    return idName.startsWith("org.jetbrains.kotlin.")
  }

  fun isGradlePlugin() = idName.startsWith("org.gradle.")

}
