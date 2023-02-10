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
package com.android.tools.idea.gradle.dsl.api.settings;

import com.android.tools.idea.gradle.dsl.api.PluginModel;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PluginsModel {
  /**
   * @return a list of plugins declared (and also possibly applied) in this model
   */
  List<PluginModel> plugins();

  /**
   * @return a list of plugins applied in this model
   */
  List<PluginModel> appliedPlugins();

  /**
   * Modify the underlying object, if necessary, such that the given plugin is applied, and return the model corresponding to the
   * plugin declaration.
   *
   * @param plugin the name of a plugin
   * @return the model for the given plugin
   */
  @NotNull
  PluginModel applyPlugin(@NotNull String plugin);

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
  @NotNull
  default PluginModel applyPlugin(@NotNull String plugin, @NotNull String version) {
    return applyPlugin(plugin, version, null);
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
  @NotNull
  PluginModel applyPlugin(@NotNull String plugin, @NotNull String version, @Nullable Boolean apply);

  /**
   * Modify the underlying object so that the given reference is used as an alias to apply a plugin with the specified
   * apply status, and return the model corresponding to the plugin declaration.
   *
   * @param reference a reference to a plugin alias
   * @param apply whether the plugin should be declared as applied in this model
   * @return the model for the given plugin
   */
  @NotNull
  PluginModel applyPlugin(@NotNull ReferenceTo reference, @Nullable Boolean apply);

  /**
   * Modify the underlying object such that the given plugin is no longer declared.
   *
   * @param plugin the name of a plugin
   */
  void removePlugin(@NotNull String plugin);
}
