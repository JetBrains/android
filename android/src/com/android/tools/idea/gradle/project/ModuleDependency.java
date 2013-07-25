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

import com.google.common.collect.Sets;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

/**
 * Dependency of an IDEA module on another module.
 */
class ModuleDependency {
  @NotNull private final String myModuleName;

  @NotNull private DependencyScope myScope = DependencyScope.COMPILE;

  /**
   * Creates a new {@link ModuleDependency}.
   *
   * @param moduleName the name of the IDEA module to depend on.
   */
  ModuleDependency(@NotNull String moduleName) {
    myModuleName = moduleName;
  }

  void setScope(@NotNull DependencyScope scope) {
    myScope = scope;
  }

  void addTo(@NotNull DataNode<ModuleData> moduleInfo, @NotNull DataNode<ProjectData> projectInfo) {
    Set<String> registeredModuleNames = Sets.newHashSet();
    Collection<DataNode<ModuleData>> modules = ExternalSystemApiUtil.getChildren(projectInfo, ProjectKeys.MODULE);
    for (DataNode<ModuleData> m : modules) {
      String name = m.getData().getName();
      registeredModuleNames.add(name);
      if (name.equals(myModuleName)) {
        ModuleDependencyData dependencyInfo = new ModuleDependencyData(moduleInfo.getData(), m.getData());
        dependencyInfo.setScope(myScope);
        dependencyInfo.setExported(true);
        moduleInfo.createChild(ProjectKeys.MODULE_DEPENDENCY, dependencyInfo);
        return;
      }
    }
    String format = "Unable to find module with name '%1$s'. Registered modules: %2$s";
    String msg = String.format(format, myModuleName, registeredModuleNames);
    throw new IllegalStateException(msg);
  }
}
