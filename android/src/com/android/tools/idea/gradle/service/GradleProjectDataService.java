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

import com.android.tools.idea.gradle.AndroidProjectKeys;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.IdeaGradleProject;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.util.Facets;
import com.google.common.collect.Maps;
import com.intellij.facet.FacetManager;
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
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

import static com.android.tools.idea.gradle.util.Projects.setGradleVersionUsed;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

/**
 * Service that stores the "Gradle project paths" of an imported Android-Gradle project.
 */
public class GradleProjectDataService extends AbstractProjectDataService<IdeaGradleProject, Void> {
  private static final Logger LOG = Logger.getInstance(GradleProjectDataService.class);

  @NotNull
  @Override
  public Key<IdeaGradleProject> getTargetDataKey() {
    return AndroidProjectKeys.IDE_GRADLE_PROJECT;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<IdeaGradleProject>> toImport,
                         @Nullable final ProjectData projectData,
                         @NotNull final Project project,
                         @NotNull final IdeModifiableModelsProvider modelsProvider) {
    if (!toImport.isEmpty()) {
      try {
        doImport(toImport, project);
      } catch (Throwable e) {
        LOG.error(String.format("Failed to set up modules in project '%1$s'", project.getName()), e);
        GradleSyncState.getInstance(project).syncFailed(e.getMessage());
      }
    }
  }

  private static void doImport(final Collection<DataNode<IdeaGradleProject>> toImport, final Project project) throws Throwable {
    RunResult result = new WriteCommandAction.Simple(project) {
      @Override
      protected void run() throws Throwable {
        if (!project.isDisposed()) {
          ModuleManager moduleManager = ModuleManager.getInstance(project);
          Map<String, IdeaGradleProject> gradleProjectsByName = indexByModuleName(toImport);
          for (Module module : moduleManager.getModules()) {
            IdeaGradleProject gradleProject = gradleProjectsByName.get(module.getName());
            if (gradleProject == null) {
              // This happens when there is an orphan IDEA module that does not map to a Gradle project. One way for this to happen is when
              // opening a project created in another machine, and Gradle import assigns a different name to a module. Then, user decides not
              // to delete the orphan module when Studio prompts to do so.
              Facets.removeAllFacetsOfType(module, AndroidGradleFacet.TYPE_ID);
            }
            else {
              String gradleVersion = gradleProject.getGradleVersion();
              if (isNotEmpty(gradleVersion)) {
                setGradleVersionUsed(project, gradleVersion);
              }
              customizeModule(module, gradleProject);
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
  private static Map<String, IdeaGradleProject> indexByModuleName(@NotNull Collection<DataNode<IdeaGradleProject>> dataNodes) {
    Map<String, IdeaGradleProject> gradleProjectsByModuleName = Maps.newHashMap();
    for (DataNode<IdeaGradleProject> d : dataNodes) {
      IdeaGradleProject gradleProject = d.getData();
      gradleProjectsByModuleName.put(gradleProject.getModuleName(), gradleProject);
    }
    return gradleProjectsByModuleName;
  }

  private static void customizeModule(@NotNull Module module, @NotNull IdeaGradleProject gradleProject) {
    AndroidGradleFacet androidGradleFacet = setAndGetAndroidGradleFacet(module);
    androidGradleFacet.setGradleProject(gradleProject);
  }

  /**
   * Retrieves the Android-Gradle facet from the given module. If the given module does not have it, this method will create a new one.
   *
   * @param module the given module.
   * @return the Android-Gradle facet from the given module.
   */
  @NotNull
  private static AndroidGradleFacet setAndGetAndroidGradleFacet(Module module) {
    AndroidGradleFacet facet = AndroidGradleFacet.getInstance(module);
    if (facet != null) {
      return facet;
    }

    // Module does not have Android-Gradle facet. Create one and add it.
    FacetManager facetManager = FacetManager.getInstance(module);
    ModifiableFacetModel model = facetManager.createModifiableModel();
    try {
      facet = facetManager.createFacet(AndroidGradleFacet.getFacetType(), AndroidGradleFacet.NAME, null);
      model.addFacet(facet);
    }
    finally {
      model.commit();
    }
    return facet;
  }
}
