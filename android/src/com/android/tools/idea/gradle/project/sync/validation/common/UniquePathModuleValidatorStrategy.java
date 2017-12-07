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

import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.util.GradleProjects;
import com.android.tools.idea.project.messages.SyncMessage;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.gradle.project.sync.messages.GroupNames.PROJECT_STRUCTURE_ISSUES;
import static com.android.tools.idea.project.messages.MessageType.ERROR;

class UniquePathModuleValidatorStrategy extends CommonProjectValidationStrategy {
  @NotNull private final Multimap<String, Module> myModulesByPath = HashMultimap.create();

  UniquePathModuleValidatorStrategy(@NotNull Project project) {
    super(project);
  }

  @Override
  void validate(@NotNull Module module) {
    if (!GradleProjects.isIdeaAndroidModule(module)) {
      return;
    }
    File moduleFolderPath = GradleProjects.findModuleRootFolderPath(module);
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
      for (Module module : modules) {
        if (i++ != 0) {
          msg.append(", ");
        }
        String name = module.getName();
        msg.append("'").append(name).append("'");
      }
      msg.append("] point to the same directory in the file system.");

      String[] lines = {msg.toString(), "Each module must have a unique path."};
      SyncMessage message = new SyncMessage(PROJECT_STRUCTURE_ISSUES, ERROR, lines);

      Project project = getProject();
      GradleSyncMessages.getInstance(project).report(message);
    }
  }

  @VisibleForTesting
  @NotNull
  Multimap<String, Module> getModulesByPath() {
    return myModulesByPath;
  }
}
