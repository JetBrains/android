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

import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.projectsystem.AndroidProjectSettingsService;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SelectJdkFromFileSystemHyperlink extends NotificationHyperlink {
  @NotNull private final AndroidProjectSettingsService mySettingsService;

  @Nullable
  public static SelectJdkFromFileSystemHyperlink create(@NotNull Project project) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      return doCreate(project);
    }
    return null;
  }

  @VisibleForTesting
  static SelectJdkFromFileSystemHyperlink doCreate(@NotNull Project project) {
    if (!project.isDisposed()) {
      ProjectSettingsService service = ProjectSettingsService.getInstance(project);
      if (service instanceof AndroidProjectSettingsService) {
        return new SelectJdkFromFileSystemHyperlink((AndroidProjectSettingsService)service);
      }
    }
    return null;
  }

  private SelectJdkFromFileSystemHyperlink(@NotNull AndroidProjectSettingsService settingsService) {
    super("select.jdk", "Select the Gradle JDK location");
    mySettingsService = settingsService;
  }

  @Override
  protected void execute(@NotNull Project project) {
    if (!project.isDisposed()) {
      mySettingsService.chooseJdkLocation();
    }
  }
}
