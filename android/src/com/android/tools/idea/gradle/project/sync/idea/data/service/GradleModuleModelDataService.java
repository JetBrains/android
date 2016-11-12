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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.facet.gradle.AndroidGradleFacet;
import com.android.tools.idea.gradle.project.sync.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.sync.setup.module.GradleModuleSetup;
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

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.GRADLE_MODULE_MODEL;
import static com.android.tools.idea.gradle.util.Facets.removeAllFacetsOfType;

/**
 * Applies Gradle settings to the modules of an Android project.
 */
public class GradleModuleModelDataService extends AbstractProjectDataService<GradleModuleModel, Void> {
  @NotNull private final GradleModuleSetup myModuleSetup;

  @SuppressWarnings("unused") // Instantiated by IDEA
  public GradleModuleModelDataService() {
    this(new GradleModuleSetup());
  }

  @VisibleForTesting
  GradleModuleModelDataService(@NotNull GradleModuleSetup moduleSetup) {
    myModuleSetup = moduleSetup;
  }

  @Override
  @NotNull
  public Key<GradleModuleModel> getTargetDataKey() {
    return GRADLE_MODULE_MODEL;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<GradleModuleModel>> toImport,
                         @Nullable ProjectData projectData,
                         @NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    if (!toImport.isEmpty()) {
      try {
        doImport(toImport, project, modelsProvider);
      }
      catch (Throwable e) {
        getLog().error(String.format("Failed to set up modules in project '%1$s'", project.getName()), e);
        GradleSyncState.getInstance(project).syncFailed(e.getMessage());
      }
    }
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(GradleModuleModelDataService.class);
  }

  private void doImport(@NotNull Collection<DataNode<GradleModuleModel>> toImport,
                        @NotNull Project project,
                        @NotNull IdeModifiableModelsProvider modelsProvider) throws Throwable {
    RunResult result = new WriteCommandAction.Simple(project) {
      @Override
      protected void run() throws Throwable {
        if (!project.isDisposed()) {
          Map<String, GradleModuleModel> gradleProjectsByName = indexByModuleName(toImport);
          for (Module module : modelsProvider.getModules()) {
            GradleModuleModel gradleModuleModel = gradleProjectsByName.get(module.getName());
            if (gradleModuleModel == null) {
              // This happens when there is an orphan IDEA module that does not map to a Gradle project. One way for this to happen is when
              // opening a project created in another machine, and Gradle import assigns a different name to a module. Then, user decides
              // not to delete the orphan module when Studio prompts to do so.
              removeAllFacetsOfType(AndroidGradleFacet.getFacetTypeId(), modelsProvider.getModifiableFacetModel(module));
            }
            else {
              myModuleSetup.setUpModule(module, modelsProvider, gradleModuleModel);
            }
          }
        }
      }
    }.execute();
    Throwable error = result.getThrowable();
    if (error != null) {
      throw error;
    }
  }

  @NotNull
  private static Map<String, GradleModuleModel> indexByModuleName(@NotNull Collection<DataNode<GradleModuleModel>> dataNodes) {
    Map<String, GradleModuleModel> gradleProjectsByModuleName = Maps.newHashMap();
    for (DataNode<GradleModuleModel> d : dataNodes) {
      GradleModuleModel gradleModuleModel = d.getData();
      gradleProjectsByModuleName.put(gradleModuleModel.getModuleName(), gradleModuleModel);
    }
    return gradleProjectsByModuleName;
  }
}
