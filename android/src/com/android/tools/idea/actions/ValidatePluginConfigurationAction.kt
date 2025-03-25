/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.actions

import com.intellij.ide.plugins.PluginManagerCore.findPlugin
import com.intellij.ide.plugins.PluginManagerCore.getNonOptionalDependenciesIds
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.thisLogger

/** Validates IDE plugins and their dependencies (for use in E2E tests). */
class ValidatePluginConfigurationAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    validateEssentialPlugins()
    thisLogger().info("Done validating plugins")
  }

  @Suppress("UnstableApiUsage")
  private fun validateEssentialPlugins() {
    // Until JetBrains fixes https://youtrack.jetbrains.com/issue/IJPL-6075 upstream, we want to ensure all Android plugin
    // dependencies are marked 'essential' so they cannot be disabled (see b/202048599, b/365493089). If this assertion fails,
    // then AndroidStudioApplicationInfo.xml needs to be adjusted to list additional essential plugins.
    val appInfoEx = ApplicationInfoEx.getInstanceEx()
    val essentialPlugins = appInfoEx.essentialPluginIds.toSet()
    val errors = mutableListOf<String>()
    for (plugin in essentialPlugins) {
      val descriptor = findPlugin(plugin) ?: error("Failed to find descriptor for essential plugin: $plugin")
      for (dependency in getNonOptionalDependenciesIds(descriptor)) {
        // No need to worry about V1 modules of the form "com.intellij.modules.*". These are not true plugins; they cannot be disabled.
        val dependencyDescriptor = checkNotNull(findPlugin(dependency)) { "Failed to find plugin descriptor for: $dependency" }
        val isV1Module = dependency.idString.startsWith("com.intellij.modules.") && dependency != dependencyDescriptor.pluginId
        if (dependency !in essentialPlugins && !isV1Module) {
          errors.add("Essential plugin '$plugin' depends on non-essential plugin '$dependency'")
        }
      }
    }
    if (errors.isNotEmpty()) {
      val msg = "The essential plugins in AndroidStudioApplicationInfo.xml do not form a transitive closure:\n" + errors.joinToString("\n")
      throw AssertionError(msg)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
