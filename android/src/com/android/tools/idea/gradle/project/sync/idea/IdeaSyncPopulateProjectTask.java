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
package com.android.tools.idea.gradle.project.sync.idea;

import com.android.tools.idea.gradle.project.subset.ProjectSubset;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.externalSystem.model.ProjectKeys.PROJECT;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAll;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findParent;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.visit;
import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;

public class IdeaSyncPopulateProjectTask {
  @NotNull private final Project myProject;
  @NotNull private final DataNode<ProjectData> myProjectInfo;

  public IdeaSyncPopulateProjectTask(@NotNull Project project, @NotNull DataNode<ProjectData> projectInfo) {
    myProject = project;
    myProjectInfo = projectInfo;
  }

  public void populateProject(@Nullable PostSyncProjectSetup.Request setupRequest, boolean allowModuleSelection) {
    Collection<DataNode<ModuleData>> activeModules = getActiveModules(allowModuleSelection);
    populateProject(activeModules, setupRequest);
  }

  @NotNull
  private Collection<DataNode<ModuleData>> getActiveModules(boolean allowModuleSelection) {
    Collection<DataNode<ModuleData>> modules = findAll(myProjectInfo, ProjectKeys.MODULE);
    ProjectSubset subview = ProjectSubset.getInstance(myProject);
    if (!ApplicationManager.getApplication().isUnitTestMode() &&
        ProjectSubset.getInstance(myProject).isFeatureEnabled() &&
        modules.size() > 1) {
      if (allowModuleSelection) {
        // Importing a project. Allow user to select which modules to include in the project.
        Collection<DataNode<ModuleData>> selection = subview.showModuleSelectionDialog(modules);
        if (selection != null) {
          return selection;
        }
      }
      else {
        // We got here because a project was synced with Gradle. Make sure that we don't add any modules that were not selected during
        // project import (if applicable.)
        String[] persistedModuleNames = subview.getSelection();
        if (persistedModuleNames != null) {
          int moduleCount = persistedModuleNames.length;
          if (moduleCount > 0) {
            List<String> moduleNames = Lists.newArrayList(persistedModuleNames);
            List<DataNode<ModuleData>> selectedModules = Lists.newArrayListWithExpectedSize(moduleCount);
            for (DataNode<ModuleData> module : modules) {
              String name = module.getData().getExternalName();
              if (moduleNames.contains(name)) {
                selectedModules.add(module);
              }
            }
            return selectedModules;
          }
        }
      }
    }
    // Delete any stored module selection.
    subview.clearSelection();
    return modules; // Import all modules, not just subset.
  }

  public void populateProject(@NotNull Collection<DataNode<ModuleData>> activeModules,
                              @Nullable PostSyncProjectSetup.Request setupRequest) {
    invokeAndWaitIfNeeded((Runnable)() -> GradleSyncMessages.getInstance(myProject).removeProjectMessages());

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      populate(activeModules, new EmptyProgressIndicator(), setupRequest);
      return;
    }

    Task.Backgroundable task = new Task.Backgroundable(myProject, "Project Setup", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        populate(activeModules, indicator, setupRequest);
      }
    };
    task.queue();
  }

  private void populate(@NotNull Collection<DataNode<ModuleData>> activeModules,
                        @NotNull ProgressIndicator indicator,
                        @Nullable PostSyncProjectSetup.Request setupRequest) {
    disableExcludedModules(activeModules);
    doSelectiveImport(activeModules, myProject);
    if (setupRequest != null) {
      PostSyncProjectSetup.getInstance(myProject).setUpProject(setupRequest, indicator);
    }
  }

  /**
   * Reuse external system 'selective import' feature for importing of the project sub-set.
   */
  private void disableExcludedModules(@NotNull Collection<DataNode<ModuleData>> activeModules) {
    Collection<DataNode<ModuleData>> allModules = findAll(myProjectInfo, ProjectKeys.MODULE);
    if (activeModules.size() != allModules.size()) {
      Set<DataNode<ModuleData>> moduleToIgnore = Sets.newHashSet(allModules);
      moduleToIgnore.removeAll(activeModules);
      for (DataNode<ModuleData> moduleNode : moduleToIgnore) {
        visit(moduleNode, node -> node.setIgnored(true));
      }
    }
  }

  /**
   * Reuse external system 'selective import' feature for importing of the project sub-set.
   * And do not ignore projectNode children data, e.g. project libraries
   */
  private static void doSelectiveImport(@NotNull Collection<DataNode<ModuleData>> activeModules, @NotNull Project project) {
    ProjectDataManager dataManager = ServiceManager.getService(ProjectDataManager.class);
    DataNode<ProjectData> projectNode = activeModules.isEmpty() ? null : findParent(activeModules.iterator().next(), PROJECT);

    // do not ignore projectNode child data, e.g. project libraries
    if (projectNode != null) {
      Collection<DataNode<ModuleData>> allModules = findAll(projectNode, ProjectKeys.MODULE);
      if (activeModules.size() != allModules.size()) {
        Set<DataNode<ModuleData>> moduleToIgnore = ContainerUtil.newIdentityTroveSet(allModules);
        moduleToIgnore.removeAll(activeModules);
        for (DataNode<ModuleData> moduleNode : moduleToIgnore) {
          visit(moduleNode, node -> node.setIgnored(true));
        }
      }
      dataManager.importData(projectNode, project, true /* synchronous */);
    }
    else {
      dataManager.importData(activeModules, project, true /* synchronous */);
    }
  }
}
