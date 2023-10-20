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
package com.android.tools.idea.gradle.project;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class GradleProjectInfo {
  private volatile boolean myNewProject;
  /**
   * See <a href="https://issuetracker.google.com/291935296">this bug</a> for more info.
   * <p>
   * This field, related getters and setters and their usages need to be maintained for the time being
   * since the gradle-profiler intelliJ IDEA plugin still rely on them.
   */
  private volatile boolean mySkipStartupActivity;

  @NotNull
  public static GradleProjectInfo getInstance(@NotNull Project project) {
    return project.getService(GradleProjectInfo.class);
  }

  public GradleProjectInfo(@NotNull Project ignoredProject) { }

  public boolean isNewProject() {
    return myNewProject;
  }

  public void setNewProject(boolean newProject) {
    myNewProject = newProject;
  }

  @Deprecated
  public boolean isSkipStartupActivity() {
    return mySkipStartupActivity;
  }

  @Deprecated
  public void setSkipStartupActivity(boolean skipStartupActivity) {
    mySkipStartupActivity = skipStartupActivity;
  }
}
