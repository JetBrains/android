/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sets up a {@link Module} using the settings from a given Gradle model.
 */
public interface ModuleCustomizer<T> {
  /**
   * Customizes the given module (e.g. add facets, SDKs, etc.)
   *
   * @param project              project that owns the module to customize.
   * @param moduleModel          modifiable root module of the module to customize. The caller is responsible to commit the changes to model
   *                             and the customizer should not call commit on the model.
   * @param externalProjectModel the imported Gradle model.
   */
  void customizeModule(@NotNull Project project, @NotNull ModifiableRootModel moduleModel, @Nullable T externalProjectModel);
}
