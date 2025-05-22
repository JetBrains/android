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

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId

/** Validates essential plugins and their dependencies (for use in E2E tests). */
@Suppress("UnstableApiUsage")
class ValidateEssentialPluginsAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    validateEssentialPlugins()
    thisLogger().info("Done validating plugins")
  }

  private fun validateEssentialPlugins() {
    // Until JetBrains fixes https://youtrack.jetbrains.com/issue/IJPL-6075 upstream, we want to ensure all Android plugin
    // dependencies are marked 'essential' so they cannot be disabled (see b/202048599, b/365493089). If this assertion fails,
    // then AndroidStudioApplicationInfo.xml needs to be adjusted to list additional essential plugins.
    val appInfoEx = ApplicationInfoEx.getInstanceEx()
    val essentialPlugins = appInfoEx.essentialPluginIds.toSet()
    val errors = mutableListOf<String>()
    for (plugin in essentialPlugins) {
      for (dependency in getRequiredPluginDependencies(plugin)) {
        if (dependency !in essentialPlugins && dependency != PluginManagerCore.CORE_ID) {
          errors.add("Essential plugin '$plugin' requires non-essential plugin '$dependency'")
        }
      }
    }
    if (errors.isNotEmpty()) {
      val msg = "The essential plugins in AndroidStudioApplicationInfo.xml do not form a transitive closure:\n" + errors.joinToString("\n")
      throw AssertionError(msg)
    }
  }

  /**
   * Returns the required plugin dependencies of [plugin], including those implied by v2 module dependencies
   * (unlike [PluginManagerCore.getNonOptionalDependenciesIds], which ignores v2 module edges).
   */
  private fun getRequiredPluginDependencies(plugin: PluginId): Collection<PluginId> {
    val pluginSet = PluginManagerCore.getPluginSet()
    val plugins = mutableSetOf<PluginId>()
    val modules = mutableSetOf<String>()

    // Using 'canonical' plugin IDs ensures we handle v1 modules correctly.
    fun getCanonicalPluginId(id: PluginId): PluginId = checkNotNull(pluginSet.findEnabledPlugin(id)).pluginId

    fun collectDependencies(descriptor: IdeaPluginDescriptorImpl) {
      // v1 dependencies.
      for (dep in descriptor.pluginDependencies) {
        if (!dep.isOptional) {
          plugins.add(getCanonicalPluginId(dep.pluginId))
        }
      }
      // v2 dependencies.
      for (dep in descriptor.dependencies.plugins) {
        plugins.add(getCanonicalPluginId(dep.id))
      }
      for (dep in descriptor.dependencies.modules) {
        val moduleDescriptor = checkNotNull(pluginSet.findEnabledModule(dep.name))
        if (modules.add(dep.name)) {
          // Traverse v2 module edges recursively (until we reach actual plugins).
          collectDependencies(moduleDescriptor)
        }
        // Add an implicit dependency on the v2 module 'host' plugin.
        plugins.add(getCanonicalPluginId(moduleDescriptor.pluginId))
      }
    }

    val descriptor = checkNotNull(pluginSet.findEnabledPlugin(plugin))
    collectDependencies(descriptor)

    val baseline = PluginManagerCore.getNonOptionalDependenciesIds(descriptor).map(::getCanonicalPluginId)
    check(plugins.containsAll(baseline)) {
      "The result of getRequiredPluginDependencies() appears to be inconsistent with getNonOptionalDependenciesIds() from IntelliJ"
    }

    return plugins
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
