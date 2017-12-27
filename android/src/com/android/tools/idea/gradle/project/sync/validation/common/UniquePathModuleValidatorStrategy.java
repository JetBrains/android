/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.validation.common;

import com.android.tools.idea.gradle.project.subset.ProjectSubset;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.idea.data.DataNodeCaches;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.util.Projects;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.*;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

import static com.android.tools.idea.gradle.project.sync.messages.GroupNames.PROJECT_STRUCTURE_ISSUES;
import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.android.tools.idea.gradle.util.Projects.findModuleRootFolderPath;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAll;

class UniquePathModuleValidatorStrategy extends CommonProjectValidationStrategy {
  @NotNull private final Multimap<String, Module> myModulesByPath = HashMultimap.create();

  UniquePathModuleValidatorStrategy(@NotNull Project project) {
    super(project);
  }

  @Override
  void validate(@NotNull Module module) {
    if(!Projects.isIdeaAndroidModule(module)) {
      return;
    }
    File moduleFolderPath = findModuleRootFolderPath(module);
    if (moduleFolderPath != null) {
      myModulesByPath.put(moduleFolderPath.getPath(), module);
    }
  }

  @Override
  void fixAndReportFoundIssues() {
    Set<String> modulePaths = myModulesByPath.keySet();
    for (String modulePath : modulePaths) {
      List<Module> modules = new ArrayList<>(myModulesByPath.get(modulePath));
      modules.sort(Comparator.comparing(Module::getName));

      int moduleCount = modules.size();
      if (moduleCount <= 1) {
        continue;
      }
      StringBuilder msg = new StringBuilder();
      msg.append("The modules [");

      int i = 0;
      Set<String> moduleNames = new HashSet<>();
      for (Module module : modules) {
        if (i++ != 0) {
          msg.append(", ");
        }
        String name = module.getName();
        moduleNames.add(name);
        msg.append("'").append(name).append("'");
      }
      msg.append("] point to the same directory in the file system.");

      String[] lines = {msg.toString(), "Each module must have a unique path."};
      SyncMessage message = new SyncMessage(PROJECT_STRUCTURE_ISSUES, ERROR, lines);

      List<DataNode<ModuleData>> modulesToDisplayInDialog = Lists.newArrayList();
      Project project = getProject();
      if (ProjectSubset.getInstance(project).isFeatureEnabled()) {
        DataNode<ProjectData> projectInfo = DataNodeCaches.getInstance(project).getCachedProjectData();
        if (projectInfo != null) {
          Collection<DataNode<ModuleData>> cachedModules = findAll(projectInfo, MODULE);
          for (DataNode<ModuleData> moduleNode : cachedModules) {
            if (moduleNames.contains(moduleNode.getData().getExternalName())) {
              modulesToDisplayInDialog.add(moduleNode);
            }
          }
        }
      }

      if (!modulesToDisplayInDialog.isEmpty()) {
        message.add(new AddOrRemoveModulesHyperlink());
      }

      GradleSyncMessages.getInstance(project).report(message);
    }
  }

  private static class AddOrRemoveModulesHyperlink extends NotificationHyperlink {
    AddOrRemoveModulesHyperlink() {
      super("add.or.remove.modules", "Configure Project Subset");
    }

    @Override
    protected void execute(@NotNull Project project) {
      ProjectSubset subset = ProjectSubset.getInstance(project);
      subset.addOrRemoveModules();
    }
  }

  @VisibleForTesting
  @NotNull
  Multimap<String, Module> getModulesByPath() {
    return myModulesByPath;
  }
}
