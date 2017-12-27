/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng;

import com.intellij.facet.Facet;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.intellij.ide.highlighter.ModuleFileType.DOT_DEFAULT_EXTENSION;
import static com.intellij.openapi.externalSystem.util.ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY;
import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.getModuleDirPath;
import static org.jetbrains.plugins.gradle.util.GradleUtil.getConfigPath;

class ModuleFactory {
  @NotNull private final Project myProject;
  @NotNull private final IdeModifiableModelsProvider myModelsProvider;

  ModuleFactory(@NotNull Project project, @NotNull IdeModifiableModelsProvider modelsProvider) {
    myProject = project;
    myModelsProvider = modelsProvider;
  }

  @NotNull
  Module createModule(@NotNull SyncAction.ModuleModels moduleModels) {
    GradleProject gradleProject = moduleModels.findModel(GradleProject.class);
    assert gradleProject != null;
    File imlFilePath = getModuleImlFilePath(gradleProject, moduleModels);

    Module newModule = myModelsProvider.newModule(imlFilePath.getPath(), StdModuleTypes.JAVA.getId());
    newModule.setOption(EXTERNAL_SYSTEM_ID_KEY, GRADLE_SYSTEM_ID.getId()); // Identifies a module as a "Gradle" module.

    ModifiableRootModel rootModel = myModelsProvider.getModifiableRootModel(newModule);
    rootModel.inheritSdk();

    // Remove all dependencies.
    DependencyRemover dependencyRemover = new DependencyRemover(rootModel);
    for (OrderEntry orderEntry : rootModel.getOrderEntries()) {
      orderEntry.accept(dependencyRemover, null);
    }

    // Remove all facets.
    ModifiableFacetModel facetModel = myModelsProvider.getModifiableFacetModel(newModule);
    for (Facet facet : facetModel.getAllFacets()) {
      facetModel.removeFacet(facet);
    }

    return newModule;
  }

  @NotNull
  private File getModuleImlFilePath(@NotNull GradleProject gradleProject, @NotNull SyncAction.ModuleModels moduleModels) {
    String modulePath = getModulePath(gradleProject, moduleModels);
    String imlFileName = gradleProject.getName() + DOT_DEFAULT_EXTENSION;
    return new File(modulePath, imlFileName);
  }

  @NotNull
  private String getModulePath(@NotNull GradleProject gradleProject, @NotNull SyncAction.ModuleModels moduleModels) {
    GradleBuild gradleBuild = moduleModels.findModel(GradleBuild.class);
    if (gradleBuild != null) {
      File moduleDirPath = getModuleDirPath(gradleBuild, gradleProject.getPath());
      if (moduleDirPath == null) {
        throw new IllegalStateException(String.format("Unable to find root directory for module '%1$s'", gradleProject.getName()));
      }
      return toCanonicalPath(moduleDirPath.getPath());
    }

    String projectPath = myProject.getBasePath();
    assert projectPath != null; // We should not be dealing with 'default' project.
    return toSystemDependentName(getConfigPath(gradleProject, projectPath));
  }

  private static class DependencyRemover extends RootPolicy<Object> {
    @NotNull private final ModifiableRootModel myRootModel;

    DependencyRemover(@NotNull ModifiableRootModel rootModel) {
      myRootModel = rootModel;
    }

    @Override
    public Object visitModuleOrderEntry(ModuleOrderEntry orderEntry, Object value) {
      return remove(orderEntry, value);
    }

    @Override
    public Object visitLibraryOrderEntry(LibraryOrderEntry orderEntry, Object value) {
      return remove(orderEntry, value);
    }

    private Object remove(OrderEntry orderEntry, Object value) {
      myRootModel.removeOrderEntry(orderEntry);
      return value;
    }
  }
}
