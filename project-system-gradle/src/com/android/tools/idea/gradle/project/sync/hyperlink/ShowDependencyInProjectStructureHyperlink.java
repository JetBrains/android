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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import com.android.tools.idea.gradle.project.sync.issues.SyncIssueNotificationHyperlink;
import com.android.tools.idea.projectsystem.AndroidProjectSettingsService;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import org.jetbrains.annotations.NotNull;

public class ShowDependencyInProjectStructureHyperlink extends SyncIssueNotificationHyperlink {
  @NotNull private final Module myModule;
  @NotNull private final String myDependencyString;

  public ShowDependencyInProjectStructureHyperlink(@NotNull Module module, @NotNull String dependencyString) {
    super("open.dependency.in.project.structure",
          "Show in Project Structure dialog",
          AndroidStudioEvent.GradleSyncQuickFix.SHOW_DEPENDENCY_IN_PROJECT_STRUCTURE_HYPERLINK);
    myModule = module;
    myDependencyString = dependencyString;
  }

  @Override
  protected void execute(@NotNull Project project) {
    ProjectSettingsService service = ProjectSettingsService.getInstance(project);
    if (service instanceof AndroidProjectSettingsService) {
      ((AndroidProjectSettingsService)service).openAndSelectDependency(myModule, myDependencyString);
    }
  }
}
