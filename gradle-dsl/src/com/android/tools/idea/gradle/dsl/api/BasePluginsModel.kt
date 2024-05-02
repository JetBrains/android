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
package com.android.tools.idea.gradle.dsl.api

interface BasePluginsModel {
  /**
   * @return a list of plugins declared (and also possibly applied) in this model
   */
  fun plugins(): List<PluginModel>

  /**
   * @return a list of plugins applied in this model
   */
  fun appliedPlugins(): List<PluginModel>

  /**
   * Modify the underlying object, if necessary, such that the given plugin is applied, and return the model corresponding to the
   * plugin declaration.
   *
   * @param plugin the name of a plugin
   * @return the model for the given plugin
   */
  fun applyPlugin(plugin: String): PluginModel

  /**
   * Modify the underlying object such that the given plugin is declared at the specified version and with the specified apply
   * status, and return the model corresponding to the plugin declaration.
   *
   * TODO(xof): how should we handle the case where we already have a plugin declaration for this plugin?
   *
   * @param plugin the name of a plugin
   * @param version the version specification for the given plugin
   * @return the model for the given plugin
   */
  fun applyPlugin(plugin: String, version: String): PluginModel {
    return applyPlugin(plugin, version, null)
  }

  /**
   * Modify the underlying object such that the given plugin is declared at the specified version and with the specified apply
   * status, and return the model corresponding to the plugin declaration.
   *
   * TODO(xof): how should we handle the case where we already have a plugin declaration for this plugin?
   *
   * @param plugin the name of a plugin
   * @param version the version specification for the given plugin
   * @param apply whether the plugin should be declared as applied in this model
   * @return the model for the given plugin
   */
  fun applyPlugin(plugin: String, version: String, apply: Boolean?): PluginModel

  /**
   * Modify the underlying object such that the given plugin is no longer declared.
   *
   * @param plugin the name of a plugin
   */
  fun removePlugin(plugin: String)
}