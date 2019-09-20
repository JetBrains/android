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

import com.android.tools.idea.gradle.project.sync.GradleModuleModels;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.intellij.ide.highlighter.ModuleFileType.DOT_DEFAULT_EXTENSION;
import static com.intellij.openapi.externalSystem.util.ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY;
import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static org.jetbrains.plugins.gradle.util.GradleUtil.getConfigPath;

class ModuleFactory {
  @NotNull private final Project myProject;
  @NotNull private final IdeModifiableModelsProvider myModelsProvider;

  ModuleFactory(@NotNull Project project, @NotNull IdeModifiableModelsProvider modelsProvider) {
    myProject = project;
    myModelsProvider = modelsProvider;
  }

  @NotNull
  Module createModule(@NotNull GradleModuleModels moduleModels) {
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

    return newModule;
  }

  @NotNull
  private File getModuleImlFilePath(@NotNull GradleProject gradleProject, @NotNull GradleModuleModels moduleModels) {
    String modulePath = getModulePath(gradleProject, moduleModels);
    String imlFileName = moduleModels.getModuleName() + DOT_DEFAULT_EXTENSION;
    return new File(modulePath, imlFileName);
  }

  @NotNull
  private String getModulePath(@NotNull GradleProject gradleProject, @NotNull GradleModuleModels moduleModels) {
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
    public Object visitModuleOrderEntry(@NotNull ModuleOrderEntry orderEntry, Object value) {
      return remove(orderEntry, value);
    }

    @Override
    public Object visitLibraryOrderEntry(@NotNull LibraryOrderEntry orderEntry, Object value) {
      return remove(orderEntry, value);
    }

    private Object remove(OrderEntry orderEntry, Object value) {
      myRootModel.removeOrderEntry(orderEntry);
      return value;
    }
  }

  /**
   * Returns the physical path of the module's root directory (the path in the file system.)
   * <p>
   * It is important to note that Gradle has its own "logical" path that may or may not be equal to the physical path of a Gradle project.
   * For example, the sub-project at ${projectRootDir}/apps/app will have the Gradle path :apps:app. Gradle also allows mapping physical
   * paths to a different logical path. For example, in settings.gradle:
   * <pre>
   *   include ':app'
   *   project(':app').projectDir = new File(rootDir, 'apps/app')
   * </pre>
   * In this example, sub-project at ${projectRootDir}/apps/app will have the Gradle path :app.
   * </p>
   *
   * @param build contains information about the root Gradle project and its sub-projects. Such information includes the physical path of
   *              the root Gradle project and its sub-projects.
   * @param path  the Gradle "logical" path. This path uses colon as separator, and may or may not be equal to the physical path of a
   *              Gradle project.
   * @return the physical path of the module's root directory.
   */
  @Nullable
  private static File getModuleDirPath(@NotNull GradleBuild build, @NotNull String path) {
    for (BasicGradleProject project : build.getProjects()) {
      if (project.getPath().equals(path)) {
        return project.getProjectDirectory();
      }
    }
    return null;
  }
}
