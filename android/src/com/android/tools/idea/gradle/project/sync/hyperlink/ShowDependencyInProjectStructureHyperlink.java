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

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.structure.editors.AndroidProjectSettingsService;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import org.jetbrains.annotations.NotNull;

public class ShowDependencyInProjectStructureHyperlink extends NotificationHyperlink {
  @NotNull private final Module myModule;
  @NotNull private final GradleCoordinate myDependency;

  public ShowDependencyInProjectStructureHyperlink(@NotNull Module module, @NotNull GradleCoordinate dependency) {
    super("open.dependency.in.project.structure", "Show in Project Structure dialog");
    myModule = module;
    myDependency = dependency;
  }

  @Override
  protected void execute(@NotNull Project project) {
    ProjectSettingsService service = ProjectSettingsService.getInstance(project);
    if (service instanceof AndroidProjectSettingsService) {
      ((AndroidProjectSettingsService)service).openAndSelectDependency(myModule, myDependency);
    }
  }
}
