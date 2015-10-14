/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.sync.service;

import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.BuildFileStatement;
import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.service.sync.change.EntityAdded;
import com.android.tools.idea.gradle.service.sync.change.EntityAddedImpl;
import com.android.tools.idea.gradle.service.sync.change.ProjectStructureChange;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.collect.Lists;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData;
import com.intellij.openapi.externalSystem.service.project.IdeModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Ide2GradleModuleDependencyService extends AbstractIde2GradleProjectSyncService<ModuleDependencyData> {

  private static final Logger LOG = Logger.getInstance(Ide2GradleModuleDependencyService.class);

  @NotNull
  @Override
  public Key<ModuleDependencyData> getKey() {
    return ProjectKeys.MODULE_DEPENDENCY;
  }

  @Nullable
  @Override
  public ProjectStructureChange<ModuleDependencyData> build(@Nullable DataNode<ModuleDependencyData> previousStateNode,
                                                            @Nullable DataNode<ModuleDependencyData> currentStateNode,
                                                            @NotNull Project project)
  {
    if (previousStateNode == null && currentStateNode != null) {
      return new EntityAddedImpl<ModuleDependencyData>(currentStateNode);
    }
    return null;
  }

  @Override
  protected boolean processEntityAddition(@NotNull EntityAdded<ModuleDependencyData> change, @NotNull Project ideProject) {
    ModuleDependencyData data = change.getAddedEntity().getData();
    IdeModelsProvider modelsProvider = new IdeModelsProviderImpl(ideProject);
    Module ownerIdeModule = modelsProvider.findIdeModule(data.getOwnerModule());
    if (ownerIdeModule == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("Skipping change '%s' by ide -> gradle sync service %s. Reason: can't find matching owner ide module",
                                change, getClass()));
      }
      return false;
    }

    VirtualFile ownerModuleGradleFile = GradleUtil.getGradleBuildFile(ownerIdeModule);
    if (ownerModuleGradleFile == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("Skipping change '%s' by ide -> gradle sync service %s. Reason: can't find matching gradle config file "
                                + "for owner ide module '%s'", change, getClass(), ownerIdeModule.getName()));
      }
      return false;
    }

    Module dependencyIdeModule = modelsProvider.findIdeModule(data.getTarget());
    if (dependencyIdeModule == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("Skipping change '%s' by ide -> gradle sync service %s. Reason: can't find matching dependency ide module",
                                change, getClass()));
      }
      return false;
    }

    String dependencyModuleGradleConfigPath = GradleUtil.getGradlePath(dependencyIdeModule);
    if (dependencyModuleGradleConfigPath == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("Skipping change '%s' by ide -> gradle sync service %s. Reason: can't find matching gradle config file "
                                + "for dependency ide module '%s'", change, getClass(), dependencyIdeModule.getName()));
      }
      return false;
    }

    final GradleBuildFile buildFile = new GradleBuildFile(ownerModuleGradleFile, ideProject);
    final List<BuildFileStatement> dependencies = buildFile.getDependencies();
    final Dependency newGradleDependency = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.MODULE, dependencyModuleGradleConfigPath);
    for (BuildFileStatement statement : dependencies) {
      if (!(statement instanceof Dependency)) {
        continue;
      }
      Dependency dependency = (Dependency)statement;
      if (dependency.matches(newGradleDependency)) {
        return true;
      }
    }
    WriteCommandAction.runWriteCommandAction(ideProject, new Runnable() {
      @Override
      public void run() {
        ArrayList<BuildFileStatement> newDependencies = Lists.newArrayList(dependencies);
        newDependencies.add(newGradleDependency);
        buildFile.setValue(BuildFileKey.DEPENDENCIES, newDependencies);
      }
    });
    return true;
  }
}
