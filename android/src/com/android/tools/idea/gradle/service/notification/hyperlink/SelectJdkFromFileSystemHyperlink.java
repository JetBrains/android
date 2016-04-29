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
package com.android.tools.idea.gradle.service.notification.hyperlink;

import com.android.tools.idea.gradle.structure.editors.AndroidProjectSettingsService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SelectJdkFromFileSystemHyperlink extends NotificationHyperlink {
  @NotNull private final AndroidProjectSettingsService mySettingsService;

  @Nullable
  public static SelectJdkFromFileSystemHyperlink create(@NotNull Project project) {
    ProjectSettingsService service = ProjectSettingsService.getInstance(project);
    if (service instanceof AndroidProjectSettingsService) {
      return new SelectJdkFromFileSystemHyperlink((AndroidProjectSettingsService)service);
    }
    return null;
  }

  private SelectJdkFromFileSystemHyperlink(@NotNull AndroidProjectSettingsService settingsService) {
    super("select.jdk", "Select a JDK from the File System");
    mySettingsService = settingsService;
  }

  @Override
  protected void execute(@NotNull Project project) {
    mySettingsService.chooseJdkLocation();
  }
}
