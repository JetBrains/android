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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.sync.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.sync.facet.gradle.AndroidGradleFacet;
import com.android.tools.idea.gradle.project.sync.facet.gradle.AndroidGradleFacetType;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.GradleSyncSummary;
import com.google.common.collect.Maps;
import com.intellij.facet.ModifiableFacetModel;
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

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.GRADLE_MODEL;
import static com.android.tools.idea.gradle.util.Facets.findFacet;
import static com.android.tools.idea.gradle.util.Facets.removeAllFacetsOfType;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

/**
 * Service that stores the "Gradle project paths" of an imported Android-Gradle project.
 */
public class GradleModelDataService extends AbstractProjectDataService<GradleModuleModel, Void> {
  @Override
  @NotNull
  public Key<GradleModuleModel> getTargetDataKey() {
    return GRADLE_MODEL;
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
    return Logger.getInstance(GradleModelDataService.class);
  }

  private static void doImport(@NotNull Collection<DataNode<GradleModuleModel>> toImport,
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
              String gradleVersion = gradleModuleModel.getGradleVersion();
              GradleSyncSummary syncReport = GradleSyncState.getInstance(project).getSummary();
              if (isNotEmpty(gradleVersion) && syncReport.getGradleVersion() == null) {
                syncReport.setGradleVersion(GradleVersion.parse(gradleVersion));
              }
              customizeModule(module, gradleModuleModel, modelsProvider);
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

  private static void customizeModule(@NotNull Module module,
                                      @NotNull GradleModuleModel gradleModuleModel,
                                      @NotNull IdeModifiableModelsProvider modelsProvider) {
    AndroidGradleFacet androidGradleFacet = setAndGetAndroidGradleFacet(module, modelsProvider);
    androidGradleFacet.setGradleModuleModel(gradleModuleModel);
  }

  /**
   * Retrieves the Android-Gradle facet from the given module. If the given module does not have it, this method will create a new one.
   *
   * @param module         the given module.
   * @param modelsProvider platform modifiable models provider
   * @return the Android-Gradle facet from the given module.
   */
  @NotNull
  private static AndroidGradleFacet setAndGetAndroidGradleFacet(@NotNull Module module, @NotNull IdeModifiableModelsProvider modelsProvider) {
    AndroidGradleFacet facet = findFacet(module, modelsProvider, AndroidGradleFacet.getFacetTypeId());
    if (facet != null) {
      return facet;
    }

    // Module does not have Android-Gradle facet. Create one and add it.
    ModifiableFacetModel model = modelsProvider.getModifiableFacetModel(module);
    AndroidGradleFacetType facetType = AndroidGradleFacet.getFacetType();
    facet = facetType.createFacet(module, AndroidGradleFacet.getFacetName(), facetType.createDefaultConfiguration(), null);
    model.addFacet(facet);
    return facet;
  }
}
