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
package com.android.tools.idea.gradle.project.sync.idea.data.service;

import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.setup.module.NdkModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.cpp.ContentRootModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.cpp.NdkFacetModuleSetupStep;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NATIVE_ANDROID_MODEL;

public class NdkModuleModelDataService extends ModuleModelDataService<NdkModuleModel> {
  private final ImmutableList<NdkModuleSetupStep> mySetupSteps =
    ImmutableList.of(new NdkFacetModuleSetupStep(), new ContentRootModuleSetupStep());

  @Override
  @NotNull
  public Key<NdkModuleModel> getTargetDataKey() {
    return NATIVE_ANDROID_MODEL;
  }

  @Override
  protected void importData(@NotNull Collection<DataNode<NdkModuleModel>> toImport,
                            @NotNull Project project,
                            @NotNull IdeModifiableModelsProvider modelsProvider,
                            @NotNull Map<String, NdkModuleModel> modelsByName) {
    for (Module module : modelsProvider.getModules()) {
      NdkModuleModel ndkModuleModel = modelsByName.get(module.getName());
      customizeModule(module, modelsProvider, ndkModuleModel);
    }
  }

  private void customizeModule(@NotNull Module module,
                               @NotNull IdeModifiableModelsProvider modelsProvider,
                               @Nullable NdkModuleModel ndkModuleModel) {
    for (NdkModuleSetupStep setupStep : mySetupSteps) {
      setupStep.setUpModule(module, modelsProvider, ndkModuleModel, null, null);
    }
  }
}
