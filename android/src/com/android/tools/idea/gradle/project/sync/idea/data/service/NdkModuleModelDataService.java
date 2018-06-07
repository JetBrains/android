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
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.setup.module.NdkModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.ndk.NdkModuleCleanupStep;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NDK_MODEL;

public class NdkModuleModelDataService extends ModuleModelDataService<NdkModuleModel> {
  @NotNull private final ModuleSetupContext.Factory myModuleSetupContextFactory;
  @NotNull private final NdkModuleSetup myModuleSetup;
  @NotNull private final NdkModuleCleanupStep myCleanupStep;

  @SuppressWarnings("unused") // Instantiated by IDEA
  public NdkModuleModelDataService() {
    this(new ModuleSetupContext.Factory(), new NdkModuleSetup(), new NdkModuleCleanupStep());
  }

  @VisibleForTesting
  NdkModuleModelDataService(@NotNull ModuleSetupContext.Factory moduleSetupContextFactory,
                            @NotNull NdkModuleSetup moduleSetup,
                            @NotNull NdkModuleCleanupStep cleanupStep) {
    myModuleSetupContextFactory = moduleSetupContextFactory;
    myModuleSetup = moduleSetup;
    myCleanupStep = cleanupStep;
  }

  @Override
  @NotNull
  public Key<NdkModuleModel> getTargetDataKey() {
    return NDK_MODEL;
  }

  @Override
  protected void importData(@NotNull Collection<DataNode<NdkModuleModel>> toImport,
                            @NotNull Project project,
                            @NotNull IdeModifiableModelsProvider modelsProvider,
                            @NotNull Map<String, NdkModuleModel> modelsByModuleName) {
    boolean syncSkipped = GradleSyncState.getInstance(project).isSyncSkipped();

    for (Module module : modelsProvider.getModules()) {
      NdkModuleModel ndkModuleModel = modelsByModuleName.get(module.getName());
      if (ndkModuleModel != null) {
        ModuleSetupContext context = myModuleSetupContextFactory.create(module, modelsProvider);
        myModuleSetup.setUpModule(context, ndkModuleModel, syncSkipped);
      }
      else {
        onModelNotFound(module, modelsProvider);
      }
    }
  }

  @Override
  protected void onModelNotFound(@NotNull Module module, @NotNull IdeModifiableModelsProvider modelsProvider) {
    myCleanupStep.cleanUpModule(module, modelsProvider);
  }
}
