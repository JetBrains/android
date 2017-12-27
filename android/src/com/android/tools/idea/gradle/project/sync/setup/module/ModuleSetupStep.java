/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.module;

import com.android.tools.idea.gradle.project.sync.ng.SyncAction;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ModuleSetupStep<T> {
  public final void setUpModule(@NotNull Module module,
                                @NotNull IdeModifiableModelsProvider ideModelsProvider,
                                @Nullable T gradleModel,
                                @Nullable SyncAction.ModuleModels gradleModels,
                                @Nullable ProgressIndicator indicator) {
    if (gradleModel == null) {
      return;
    }
    doSetUpModule(module, ideModelsProvider, gradleModel, gradleModels, indicator);
  }

  protected abstract void doSetUpModule(@NotNull Module module,
                                        @NotNull IdeModifiableModelsProvider ideModelsProvider,
                                        @NotNull T gradleModel,
                                        @Nullable SyncAction.ModuleModels gradleModels,
                                        @Nullable ProgressIndicator indicator);

  public boolean invokeOnBuildVariantChange() {
    return false;
  }

  public boolean invokeOnSkippedSync() {
    return false;
  }
}
