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
package com.android.tools.idea.gradle.project;

import com.google.common.base.Preconditions;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.LibraryPathType;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.roots.DependencyScope;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Populates an IDEA module with dependencies created from an {@link IdeaModule}.
 */
final class GradleDependencies {
  private GradleDependencies() {
  }

  static void populate(@NotNull DataNode<ModuleData> moduleInfo, @NotNull DataNode<ProjectData> projectInfo, @NotNull IdeaModule module) {
    for (IdeaDependency dep : module.getDependencies()) {
      DependencyScope scope = parseScope(dep.getScope());

      if (dep instanceof IdeaModuleDependency) {
        IdeaModule dependencyModule = ((IdeaModuleDependency)dep).getDependencyModule();
        Preconditions.checkNotNull(dependencyModule);
        String dependencyName = Preconditions.checkNotNull(dependencyModule.getName());

        ModuleDependency dependency = new ModuleDependency(dependencyName);
        dependency.setScope(scope);
        dependency.addTo(moduleInfo, projectInfo);
        continue;
      }

      if (dep instanceof IdeaSingleEntryLibraryDependency) {
        IdeaSingleEntryLibraryDependency gradleDependency = (IdeaSingleEntryLibraryDependency)dep;
        File binaryPath = gradleDependency.getFile();
        Preconditions.checkNotNull(binaryPath);

        LibraryDependency dependency = new LibraryDependency(binaryPath);

        dependency.setScope(scope);
        dependency.addPath(LibraryPathType.BINARY, binaryPath);
        dependency.addPath(LibraryPathType.SOURCE, gradleDependency.getSource());
        dependency.addPath(LibraryPathType.DOC, gradleDependency.getJavadoc());

        dependency.addTo(moduleInfo, projectInfo);
      }
    }
  }

  @NotNull
  private static DependencyScope parseScope(@Nullable IdeaDependencyScope scope) {
    if (scope != null) {
      String scopeAsString = scope.getScope();
      if (scopeAsString != null) {
        for (DependencyScope dependencyScope : DependencyScope.values()) {
          if (scopeAsString.equalsIgnoreCase(dependencyScope.toString())) {
            return dependencyScope;
          }
        }
      }
    }
    return DependencyScope.COMPILE;
  }
}
