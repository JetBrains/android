/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.variant.view;

import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import org.jetbrains.annotations.NotNull;

/**
 * Sets up a {@link com.intellij.openapi.module.Module} using the settings from a model coming from an external system (e.g. Gradle.)
 */
public interface BuildVariantModuleCustomizer<T> extends ModuleCustomizer<T> {
  ExtensionPointName<BuildVariantModuleCustomizer> EP_NAME =
    ExtensionPointName.create("com.android.gradle.buildVariantModuleCustomizer");

  /**
   * @return the "external system" where this customizer is available (e.g. Gradle), or {@link ProjectSystemId#IDE} if it is always
   * available.
   */
  @NotNull
  ProjectSystemId getProjectSystemId();

  /**
   * @return the type of model this customizer can handle.
   */
  @NotNull
  Class<T> getSupportedModelType();
}
