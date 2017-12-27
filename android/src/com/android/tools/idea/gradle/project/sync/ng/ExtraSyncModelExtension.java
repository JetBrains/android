/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng;

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Interface of extra sync models extension.
 */
public interface ExtraSyncModelExtension {
  /**
   * Extra models to request.
   *
   * @return classes to be requested from gradle
   */
  @NotNull
  Set<Class<?>> getExtraProjectModelClasses();

  /**
   * Add paths containing these classes to classpath of gradle tooling extension.
   *
   * @return classes to be available for gradle
   */
  @NotNull
  Set<Class<?>> getToolingExtensionsClasses();

  /**
   * Setup module given the returned models from gradle sync invocation.
   * A typical routine consists of:
   * <pre>
   *   <ul>
   *     <li>Check if the specific model exists. For example, check if AppEngineModel exists for AppEngine plugin.</li>
   *     <li>If model does not exist, do nothing.</li>
   *     <li>If model exists, setup extra configurations for this module. For example, create facet, run configurations and etc.</li>
   *   </ul>
   * </pre>
   */
  void setupExtraModels(@NotNull SyncAction.ModuleModels moduleModels,
                        @NotNull Project project,
                        @NotNull Module module,
                        @NotNull IdeModifiableModelsProvider modelsProvider);
}
