/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.tools.idea.gradle.GradleModel;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.facet.AndroidGradleFacetType;
import com.android.tools.idea.gradle.util.Facets;
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

import static com.android.tools.idea.gradle.AndroidProjectKeys.GRADLE_MODEL;
import static com.android.tools.idea.gradle.util.Facets.findFacet;
import static com.android.tools.idea.gradle.util.Projects.setGradleVersionUsed;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

/**
 * Service that stores the "Gradle project paths" of an imported Android-Gradle project.
 */
public class GradleModelDataService extends AbstractProjectDataService<GradleModel, Void> {
  private static final Logger LOG = Logger.getInstance(GradleModelDataService.class);

  @Override
  @NotNull
  public Key<GradleModel> getTargetDataKey() {
    return GRADLE_MODEL;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<GradleModel>> toImport,
                         @Nullable final ProjectData projectData,
                         @NotNull final Project project,
                         @NotNull final IdeModifiableModelsProvider modelsProvider) {
    if (!toImport.isEmpty()) {
      try {
        doImport(toImport, project, modelsProvider);
      }
      catch (Throwable e) {
        LOG.error(String.format("Failed to set up modules in project '%1$s'", project.getName()), e);
        GradleSyncState.getInstance(project).syncFailed(e.getMessage());
      }
    }
  }

  private static void doImport(@NotNull final Collection<DataNode<GradleModel>> toImport,
                               @NotNull final Project project,
                               @NotNull final IdeModifiableModelsProvider modelsProvider) throws Throwable {
    RunResult result = new WriteCommandAction.Simple(project) {
      @Override
      protected void run() throws Throwable {
        if (!project.isDisposed()) {
          Map<String, GradleModel> gradleProjectsByName = indexByModuleName(toImport);
          for (Module module : modelsProvider.getModules()) {
            GradleModel gradleModel = gradleProjectsByName.get(module.getName());
            if (gradleModel == null) {
              // This happens when there is an orphan IDEA module that does not map to a Gradle project. One way for this to happen is when
              // opening a project created in another machine, and Gradle import assigns a different name to a module. Then, user decides
              // not to delete the orphan module when Studio prompts to do so.
              Facets.removeAllFacetsOfType(AndroidGradleFacet.TYPE_ID, modelsProvider.getModifiableFacetModel(module));
            }
            else {
              String gradleVersion = gradleModel.getGradleVersion();
              if (isNotEmpty(gradleVersion)) {
                setGradleVersionUsed(project, gradleVersion);
              }
              customizeModule(module, gradleModel, modelsProvider);
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
  private static Map<String, GradleModel> indexByModuleName(@NotNull Collection<DataNode<GradleModel>> dataNodes) {
    Map<String, GradleModel> gradleProjectsByModuleName = Maps.newHashMap();
    for (DataNode<GradleModel> d : dataNodes) {
      GradleModel gradleModel = d.getData();
      gradleProjectsByModuleName.put(gradleModel.getModuleName(), gradleModel);
    }
    return gradleProjectsByModuleName;
  }

  private static void customizeModule(@NotNull Module module,
                                      @NotNull GradleModel gradleModel,
                                      @NotNull IdeModifiableModelsProvider modelsProvider) {
    AndroidGradleFacet androidGradleFacet = setAndGetAndroidGradleFacet(module, modelsProvider);
    androidGradleFacet.setGradleModel(gradleModel);
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
    AndroidGradleFacet facet = findFacet(module, modelsProvider, AndroidGradleFacet.TYPE_ID);
    if (facet != null) {
      return facet;
    }

    // Module does not have Android-Gradle facet. Create one and add it.
    ModifiableFacetModel model = modelsProvider.getModifiableFacetModel(module);
    final AndroidGradleFacetType facetType = AndroidGradleFacet.getFacetType();
    facet = facetType.createFacet(module, AndroidGradleFacet.NAME, facetType.createDefaultConfiguration(), null);
    model.addFacet(facet);
    return facet;
  }
}
