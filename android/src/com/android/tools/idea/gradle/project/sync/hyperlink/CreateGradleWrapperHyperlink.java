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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;

public class CreateGradleWrapperHyperlink extends NotificationHyperlink {
  public CreateGradleWrapperHyperlink() {
    super("createGradleWrapper", "Migrate to Gradle wrapper and sync project");
  }

  @Override
  protected void execute(@NotNull Project project) {
    File projectDirPath = getBaseDirPath(project);
    try {
      GradleWrapper.create(projectDirPath);
      GradleProjectSettings settings = GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(project);
      if (settings != null) {
        settings.setDistributionType(DEFAULT_WRAPPED);
      }
      requestSync(project);
    }
    catch (IOException e) {
      // Unlikely to happen.
      Messages.showErrorDialog(project, "Failed to create Gradle wrapper: " + e.getMessage(), "Quick Fix");
    }
  }

  private static void requestSync(@NotNull Project project) {
    // TODO use another trigger?
    GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, TRIGGER_PROJECT_MODIFIED);
  }
}
