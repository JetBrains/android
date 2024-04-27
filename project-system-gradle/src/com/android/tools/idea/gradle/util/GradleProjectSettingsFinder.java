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
package com.android.tools.idea.gradle.util;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getSettings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public class GradleProjectSettingsFinder {
  @NotNull
  public static GradleProjectSettingsFinder getInstance() {
    return ApplicationManager.getApplication().getService(GradleProjectSettingsFinder.class);
  }

  @Nullable
  public GradleProjectSettings findGradleProjectSettings(@NotNull Project project) {
    GradleSettings settings = (GradleSettings)getSettings(project, GradleConstants.SYSTEM_ID);

    GradleSettings.MyState state = settings.getState();
    assert state != null;
    Set<GradleProjectSettings> allProjectsSettings = state.getLinkedExternalProjectsSettings();

    return getFirstNotNull(allProjectsSettings);
  }

  @Nullable
  private static GradleProjectSettings getFirstNotNull(@Nullable Set<GradleProjectSettings> allProjectSettings) {
    if (allProjectSettings != null) {
      return allProjectSettings.stream().filter(settings -> settings != null).findFirst().orElse(null);
    }
    return null;
  }
}
