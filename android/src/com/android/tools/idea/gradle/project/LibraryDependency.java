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

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.BooleanFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;

/**
 * Dependency of an IDEA module on a Java library.
 */
class LibraryDependency {
  @NotNull private final LibraryData myLibraryData;

  @NotNull private DependencyScope myScope = DependencyScope.COMPILE;

  /**
   * Creates a new {@link LibraryDependency}.
   *
   * @param binaryPath the path of the library file to depend on.
   */
  LibraryDependency(@NotNull File binaryPath) {
    this(FileUtil.getNameWithoutExtension(binaryPath));
  }

  /**
   * Creates a new {@link LibraryDependency}.
   *
   * @param name the name of the library.
   */
  LibraryDependency(@NotNull String name) {
    myLibraryData = new LibraryData(GradleConstants.SYSTEM_ID, name);
  }

  void addPath(@NotNull LibraryPathType pathType, @Nullable File path) {
    if (path != null) {
      myLibraryData.addPath(pathType, path.getAbsolutePath());
    }
  }

  void setScope(@NotNull DependencyScope scope) {
    myScope = scope;
  }

  void addTo(@NotNull DataNode<ModuleData> moduleInfo, @NotNull DataNode<ProjectData> projectInfo) {
    DataNode<LibraryData> libraryInfo =
      ExternalSystemApiUtil.find(projectInfo, ProjectKeys.LIBRARY, new BooleanFunction<DataNode<LibraryData>>() {
        @Override
        public boolean fun(DataNode<LibraryData> node) {
          // Match only by name and binary path. Source and Javadoc paths are not relevant for comparison.
          LibraryData other = node.getData();
          return myLibraryData.getName().equals(other.getName()) &&
                 myLibraryData.getPaths(LibraryPathType.BINARY).equals(other.getPaths(LibraryPathType.BINARY));
        }
      });
    if (libraryInfo == null) {
      libraryInfo = projectInfo.createChild(ProjectKeys.LIBRARY, myLibraryData);
    }
    LibraryDependencyData dependencyInfo = new LibraryDependencyData(moduleInfo.getData(), libraryInfo.getData(), LibraryLevel.PROJECT);
    dependencyInfo.setScope(myScope);
    dependencyInfo.setExported(true);
    moduleInfo.createChild(ProjectKeys.LIBRARY_DEPENDENCY, dependencyInfo);
  }
}
