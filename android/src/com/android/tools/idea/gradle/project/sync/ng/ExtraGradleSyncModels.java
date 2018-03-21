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

import com.android.tools.idea.gradle.project.sync.ng.caching.CachedModuleModels;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Defines models to retrieve from Gradle during sync.
 */
public interface ExtraGradleSyncModels {
  /**
   * @return the types of the models to retrieve from Gradle Sync.
   */
  @NotNull
  Set<Class<?>> getModelTypes();

  /**
   * Applies the models, of the types specified in {@link #getModelTypes()}, to the given module.
   *
   * @param moduleModels   all the module-level models retrieved from Gradle, during sync.
   * @param module         the given module.
   * @param modelsProvider provides modifiable IDE models.
   */
  void applyModelsToModule(@NotNull GradleModuleModels moduleModels,
                           @NotNull Module module,
                           @NotNull IdeModifiableModelsProvider modelsProvider);

  /**
   * Adds Gradle models to the given cache. The cache is used to quickly setup a project when reopened, bypassing Gradle Sync (for
   * performance reasons.)
   *
   * @param module the module containing the models to store in the cache.
   * @param cache the model cache.
   */
  void addModelsToCache(@NotNull Module module, @NotNull CachedModuleModels cache);
}
