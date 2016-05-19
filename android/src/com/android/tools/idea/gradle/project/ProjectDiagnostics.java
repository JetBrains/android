/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.project.subset.ProjectSubset;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.PROJECT_STRUCTURE_ISSUES;
import static com.android.tools.idea.gradle.util.GradleUtil.getCachedProjectData;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAll;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

public final class ProjectDiagnostics {
  private ProjectDiagnostics() {
  }

  public static void findAndReportStructureIssues(@NotNull Project project) {
    Multimap<String, Module> modulesByPath = ArrayListMultimap.create();

    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      File moduleFilePath = new File(toSystemDependentName(module.getModuleFilePath()));
      File moduleDirPath = moduleFilePath.getParentFile();
      if (moduleDirPath != null) {
        modulesByPath.put(moduleDirPath.getPath(), module);
      }
    }

    Set<String> modulePaths = modulesByPath.keySet();
    for (String modulePath : modulePaths) {
      Collection<Module> modules = modulesByPath.get(modulePath);
      int moduleCount = modules.size();
      if (moduleCount > 1) {
        ProjectSyncMessages messages = ProjectSyncMessages.getInstance(project);
        StringBuilder msg = new StringBuilder();
        msg.append("The modules ");

        int i = 0;
        Set<String> moduleNames = Sets.newHashSet();
        for (Module module : modules) {
          if (i++ != 0) {
            msg.append(", ");
          }
          String name = module.getName();
          moduleNames.add(name);
          msg.append("'").append(name).append("'");
        }
        msg.append(" point to same directory in the file system.");

        String[] lines = { msg.toString(), "Each module has to have a unique path."};
        Message message = new Message(PROJECT_STRUCTURE_ISSUES, Message.Type.ERROR, lines);

        List<DataNode<ModuleData>> modulesToDisplayInDialog = Lists.newArrayList();
        if (ProjectSubset.isSettingEnabled() ) {
          DataNode<ProjectData> projectInfo = getCachedProjectData(project);
          if (projectInfo != null) {
            Collection<DataNode<ModuleData>> cachedModules = findAll(projectInfo, MODULE);
            for (DataNode<ModuleData> moduleNode : cachedModules) {
              if (moduleNames.contains(moduleNode.getData().getExternalName())) {
                modulesToDisplayInDialog.add(moduleNode);
              }
            }
          }
        }

        if (modulesToDisplayInDialog.isEmpty()) {
          messages.add(message);
        }
        else {
          messages.add(message, new AddOrRemoveModulesHyperlink());
        }
      }
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
}
