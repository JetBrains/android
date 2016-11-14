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
import com.android.tools.idea.gradle.project.sync.setup.module.NdkModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.cpp.ContentRootModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.cpp.NdkFacetModuleSetupStep;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.RunResult;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NATIVE_ANDROID_MODEL;

public class NdkModuleModelDataService extends AbstractProjectDataService<NdkModuleModel, Void> {
  private static final Logger LOG = Logger.getInstance(NdkModuleModelDataService.class);

  private final ImmutableList<NdkModuleSetupStep> mySetupSteps =
    ImmutableList.of(new NdkFacetModuleSetupStep(), new ContentRootModuleSetupStep());

  @NotNull
  @Override
  public Key<NdkModuleModel> getTargetDataKey() {
    return NATIVE_ANDROID_MODEL;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<NdkModuleModel>> toImport,
                         @Nullable ProjectData projectData,
                         @NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    try {
      doImport(toImport, project, modelsProvider);
    }
    catch (Throwable e) {
      LOG.error(String.format("Failed to set up Native Android Gradle modules in project '%1$s'", project.getName()), e);
      String msg = e.getMessage();
      if (msg == null) {
        msg = e.getClass().getCanonicalName();
      }
      GradleSyncState.getInstance(project).syncFailed(msg);
    }
  }

  private void doImport(final Collection<DataNode<NdkModuleModel>> toImport,
                        final Project project,
                        final IdeModifiableModelsProvider modelsProvider) throws Throwable {
    RunResult result = new WriteCommandAction.Simple(project) {
      @Override
      protected void run() throws Throwable {
        Map<String, NdkModuleModel> modelsByModuleName = indexByModuleName(toImport);
        for (Module module : modelsProvider.getModules()) {
          NdkModuleModel ndkModuleModel = modelsByModuleName.get(module.getName());
          customizeModule(module, modelsProvider, ndkModuleModel);
        }
      }
    }.execute();
    Throwable error = result.getThrowable();
    if (error != null) {
      throw error;
    }
  }

  @NotNull
  private static Map<String, NdkModuleModel> indexByModuleName(@NotNull Collection<DataNode<NdkModuleModel>> nodes) {
    Map<String, NdkModuleModel> index = Maps.newHashMap();
    for (DataNode<NdkModuleModel> dataNode : nodes) {
      NdkModuleModel ndkModuleModel = dataNode.getData();
      index.put(ndkModuleModel.getModuleName(), ndkModuleModel);
    }
    return index;
  }

  private void customizeModule(@NotNull Module module,
                               @NotNull IdeModifiableModelsProvider modelsProvider,
                               @Nullable NdkModuleModel ndkModuleModel) {
    for (NdkModuleSetupStep setupStep : mySetupSteps) {
      setupStep.setUpModule(module, modelsProvider, ndkModuleModel, null, null);
    }
  }
}
