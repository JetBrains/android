/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng.variantonly;

import com.android.tools.idea.gradle.project.sync.ng.ModuleSetup;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class VariantOnlyProjectModelsSetup extends ModuleSetup<VariantOnlyProjectModels> {
  public VariantOnlyProjectModelsSetup(@NotNull Project project,
                                       @NotNull IdeModifiableModelsProvider modelsProvider) {
    super(project, modelsProvider);
  }

  @Override
  public void setUpModules(@NotNull VariantOnlyProjectModels projectModels, @NotNull ProgressIndicator indicator) {
    throw new UnsupportedOperationException();
  }
}
