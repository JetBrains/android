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
package com.android.tools.idea.gradle.dsl.api;

import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PluginsModel extends BasePluginsModel {

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

}
