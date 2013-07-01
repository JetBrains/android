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
    for (IdeaDependency dependency : module.getDependencies()) {
      DependencyScope scope = parseScope(dependency.getScope());

      if (dependency instanceof IdeaModuleDependency) {
        IdeaModule ideaModule = ((IdeaModuleDependency)dependency).getDependencyModule();
        Preconditions.checkNotNull(ideaModule);
        String dependencyName = Preconditions.checkNotNull(ideaModule.getName());

        ModuleDependency moduleDependency = new ModuleDependency(dependencyName);
        moduleDependency.setScope(scope);
        moduleDependency.addTo(moduleInfo, projectInfo);
      }
      else if (dependency instanceof IdeaSingleEntryLibraryDependency) {
        IdeaSingleEntryLibraryDependency ideaLibrary = (IdeaSingleEntryLibraryDependency)dependency;
        File binaryPath = ideaLibrary.getFile();
        Preconditions.checkNotNull(binaryPath);

        LibraryDependency libraryDependency = new LibraryDependency(binaryPath);

        libraryDependency.setScope(scope);
        libraryDependency.addPath(LibraryPathType.BINARY, binaryPath);
        libraryDependency.addPath(LibraryPathType.SOURCE, ideaLibrary.getSource());
        libraryDependency.addPath(LibraryPathType.DOC, ideaLibrary.getJavadoc());

        libraryDependency.addTo(moduleInfo, projectInfo);
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
