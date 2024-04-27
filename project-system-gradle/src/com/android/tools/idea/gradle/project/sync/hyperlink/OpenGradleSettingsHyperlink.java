/*
 * Copyright (C) 2013 The Android Open Source Project
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

import static com.android.tools.idea.gradle.util.GradleProjectSystemUtil.GRADLE_SYSTEM_ID;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getManager;

import com.android.tools.idea.gradle.project.sync.issues.SyncIssueNotificationHyperlink;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemIndependent;
import org.jetbrains.plugins.gradle.GradleManager;
import org.jetbrains.plugins.gradle.service.settings.GradleConfigurable;

public class OpenGradleSettingsHyperlink extends SyncIssueNotificationHyperlink {
  public OpenGradleSettingsHyperlink() {
    super("openGradleSettings", "Gradle settings", AndroidStudioEvent.GradleSyncQuickFix.OPEN_GRADLE_SETTINGS_HYPERLINK);
  }

  @Override
  protected void execute(@NotNull Project project) {
    showGradleSettings(project);
  }

  public static void showGradleSettings(@NotNull Project project) {
    ExternalSystemManager<?, ?, ?, ?, ?> manager = getManager(GRADLE_SYSTEM_ID);
    assert manager instanceof GradleManager;
    GradleManager gradleManager = (GradleManager)manager;
    Configurable configurable = gradleManager.getConfigurable(project);
    ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
  }

  public static void showGradleSettings(@NotNull Project project, @SystemIndependent @NotNull String gradleRootProjectPath) {
    ExternalSystemManager<?, ?, ?, ?, ?> manager = getManager(GRADLE_SYSTEM_ID);
    assert manager instanceof GradleManager;
    GradleManager gradleManager = (GradleManager)manager;
    Configurable configurable = gradleManager.getConfigurable(project);
    assert configurable instanceof GradleConfigurable;
    GradleConfigurable gradleConfigurable = (GradleConfigurable)configurable;
    ShowSettingsUtil.getInstance()
      .editConfigurable(project, gradleConfigurable, () -> gradleConfigurable.selectProject(gradleRootProjectPath));
  }
}
