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

import com.android.tools.idea.gradle.AndroidProjectKeys;
import com.android.tools.idea.gradle.IdeaGradleProject;
import com.android.tools.idea.gradle.ProjectImportEventMessage;
import com.android.tools.idea.gradle.dependency.*;
import com.android.tools.idea.gradle.dependency.LibraryDependency;
import com.android.tools.idea.gradle.dependency.ModuleDependency;
import com.google.common.base.Objects;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.util.BooleanFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Arrays;
import java.util.Collection;

class ImportedDependencyUpdater extends DependencyUpdater<DataNode<ModuleData>> {
  @NotNull private final DataNode<ProjectData> myProjectInfo;
  @NotNull private final Collection<DataNode<ModuleData>> myModules;

  ImportedDependencyUpdater(@NotNull DataNode<ProjectData> projectInfo) {
    myProjectInfo = projectInfo;
    myModules = ExternalSystemApiUtil.getChildren(projectInfo, ProjectKeys.MODULE);
  }

  @Override
  protected void updateDependency(@NotNull DataNode<ModuleData> moduleInfo, @NotNull LibraryDependency dependency) {
    final LibraryData library = createLibraryData(dependency);
    DataNode<LibraryData> libraryInfo =
      ExternalSystemApiUtil.find(myProjectInfo, ProjectKeys.LIBRARY, new BooleanFunction<DataNode<LibraryData>>() {
        @Override
        public boolean fun(DataNode<LibraryData> node) {
          // Match only by name and binary path. Source and Javadoc paths are not relevant for comparison.
          LibraryData other = node.getData();
          return library.getName().equals(other.getName()) &&
                 library.getPaths(LibraryPathType.BINARY).equals(other.getPaths(LibraryPathType.BINARY));
        }
      });
    if (libraryInfo == null) {
      libraryInfo = myProjectInfo.createChild(ProjectKeys.LIBRARY, library);
    }
    LibraryDependencyData dependencyInfo = new LibraryDependencyData(moduleInfo.getData(), libraryInfo.getData(), LibraryLevel.PROJECT);
    dependencyInfo.setScope(dependency.getScope());
    dependencyInfo.setExported(true);
    moduleInfo.createChild(ProjectKeys.LIBRARY_DEPENDENCY, dependencyInfo);
  }

  @NotNull
  private static LibraryData createLibraryData(@NotNull LibraryDependency dependency) {
    LibraryData data = new LibraryData(GradleConstants.SYSTEM_ID, dependency.getName());
    for (LibraryDependency.PathType type : LibraryDependency.PathType.values()) {
      LibraryPathType newPathType = convertPathType(type);
      for (String path : dependency.getPaths(type)) {
        data.addPath(newPathType, path);
      }
    }
    return data;
  }

  @NotNull
  private static LibraryPathType convertPathType(@NotNull com.android.tools.idea.gradle.dependency.LibraryDependency.PathType pathType) {
    String pathTypeAsString = pathType.toString();
    LibraryPathType[] allTypes = LibraryPathType.values();
    for (LibraryPathType type : allTypes) {
      if (type.toString().equalsIgnoreCase(pathTypeAsString)) {
        return type;
      }
    }
    String msg = String.format("Unable to find a counterpart for '%1$s' in %2$s", pathTypeAsString, Arrays.toString(allTypes));
    throw new IllegalArgumentException(msg);
  }

  @Override
  protected boolean tryUpdating(@NotNull DataNode<ModuleData> moduleInfo, @NotNull ModuleDependency dependency) {
    String dependencyName = findDependencyName(moduleInfo, dependency);
    for (DataNode<ModuleData> module : myModules) {
      String name = getNameOf(module);
      if (name.equals(dependencyName)) {
        ModuleDependencyData dependencyInfo = new ModuleDependencyData(moduleInfo.getData(), module.getData());
        dependencyInfo.setScope(dependency.getScope());
        dependencyInfo.setExported(true);
        moduleInfo.createChild(ProjectKeys.MODULE_DEPENDENCY, dependencyInfo);
        return true;
      }
    }
    return false;
  }

  @NotNull
  private String findDependencyName(@NotNull DataNode<ModuleData> moduleInfo, @NotNull ModuleDependency dependency) {
    String dependencyName = dependency.getName();
    String dependencyGradlePath = dependency.getGradlePath();
    for (DataNode<ModuleData> module : myModules) {
      String moduleName = getNameOf(module);
      if (moduleName.equals(getNameOf(moduleInfo))) {
        // this is the same module as the one we are configuring.
        continue;
      }
      IdeaGradleProject gradleProject = getFirstNodeData(module, AndroidProjectKeys.IDE_GRADLE_PROJECT);
      if (gradleProject != null && Objects.equal(dependencyGradlePath, gradleProject.getGradleProjectPath())) {
        dependencyName = moduleName;
        break;
      }
    }
    return dependencyName;
  }

  @Nullable
  private static <T> T getFirstNodeData(@NotNull DataNode<ModuleData> moduleInfo, @NotNull Key<T> key) {
    Collection<DataNode<T>> children = ExternalSystemApiUtil.getChildren(moduleInfo, key);
    return getFirstNodeData(children);
  }

  @Nullable
  private static <T> T getFirstNodeData(Collection<DataNode<T>> nodes) {
    DataNode<T> node = ContainerUtil.getFirstItem(nodes);
    return node != null ? node.getData() : null;
  }

  @NotNull
  @Override
  protected String getNameOf(@NotNull DataNode<ModuleData> moduleInfo) {
    return moduleInfo.getData().getName();
  }

  @Override
  protected void log(@NotNull DataNode<ModuleData> moduleInfo, @NotNull String category, @NotNull String message) {
    moduleInfo.createChild(AndroidProjectKeys.IMPORT_EVENT_MSG, new ProjectImportEventMessage(category, message));
  }
}
