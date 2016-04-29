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
package com.android.tools.idea.gradle.service;

import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.NativeAndroidGradleModel;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.android.tools.idea.gradle.customizer.cpp.ContentRootModuleCustomizer;
import com.android.tools.idea.gradle.customizer.cpp.NativeAndroidGradleFacetModuleCustomizer;
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

import static com.android.tools.idea.gradle.AndroidProjectKeys.NATIVE_ANDROID_MODEL;

public class NativeAndroidGradleModelDataService extends AbstractProjectDataService<NativeAndroidGradleModel, Void> {
  private static final Logger LOG = Logger.getInstance(NativeAndroidGradleModelDataService.class);

  private final ImmutableList<ModuleCustomizer<NativeAndroidGradleModel>> myCustomizers =
    ImmutableList.of(new NativeAndroidGradleFacetModuleCustomizer(), new ContentRootModuleCustomizer());

  @NotNull
  @Override
  public Key<NativeAndroidGradleModel> getTargetDataKey() {
    return NATIVE_ANDROID_MODEL;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<NativeAndroidGradleModel>> toImport,
                         @Nullable ProjectData projectData,
                         @NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    if (!toImport.isEmpty()) {
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
  }

  private void doImport(final Collection<DataNode<NativeAndroidGradleModel>> toImport,
                        final Project project,
                        final IdeModifiableModelsProvider modelsProvider) throws Throwable {
    RunResult result = new WriteCommandAction.Simple(project) {
      @Override
      protected void run() throws Throwable {
        Map<String, NativeAndroidGradleModel> androidModelsByModuleName = indexByModuleName(toImport);
        for (Module module : modelsProvider.getModules()) {
          NativeAndroidGradleModel androidModel = androidModelsByModuleName.get(module.getName());
          customizeModule(module, project, modelsProvider, androidModel);
        }
      }
    }.execute();
    Throwable error = result.getThrowable();
    if (error != null) {
      throw error;
    }
  }

  @NotNull
  private static Map<String, NativeAndroidGradleModel> indexByModuleName(@NotNull Collection<DataNode<NativeAndroidGradleModel>> nodes) {
    Map<String, NativeAndroidGradleModel> index = Maps.newHashMap();
    for (DataNode<NativeAndroidGradleModel> dataNode : nodes) {
      NativeAndroidGradleModel androidModel = dataNode.getData();
      index.put(androidModel.getModuleName(), androidModel);
    }
    return index;
  }

  private void customizeModule(@NotNull Module module,
                               @NotNull Project project,
                               @NotNull IdeModifiableModelsProvider modelsProvider,
                               @Nullable NativeAndroidGradleModel androidModel) {
    for (ModuleCustomizer<NativeAndroidGradleModel> customizer : myCustomizers) {
      customizer.customizeModule(project, module, modelsProvider, androidModel);
    }
  }
}
