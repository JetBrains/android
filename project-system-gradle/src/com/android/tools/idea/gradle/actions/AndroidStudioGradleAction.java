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
package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for actions that perform Gradle-specific tasks in Android Studio.
 */
public abstract class AndroidStudioGradleAction extends AnAction {
  protected AndroidStudioGradleAction(@Nullable String text) {
    super(text);
  }

  protected AndroidStudioGradleAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @NotNull
  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    if (!isGradleProject(e)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    Project project = e.getProject();
    //noinspection UnstableApiUsage
    if (project == null || !TrustedProjects.isTrusted(project)) {
      e.getPresentation().setEnabled(false);
      return;
    }

    // Make it visible and enabled for Gradle projects, and let subclasses decide whether the action should be enabled or not.
    e.getPresentation().setEnabledAndVisible(true);
    doUpdate(e, project);
  }

  protected abstract void doUpdate(@NotNull AnActionEvent e, @NotNull Project project);

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    if (!isGradleProject(e)) {
      return;
    }
    Project project = e.getProject();
    assert project != null;
    doPerform(e, project);
  }

  protected abstract void doPerform(@NotNull AnActionEvent e, @NotNull Project project);

  protected static boolean isGradleProject(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    return project != null && ProjectSystemUtil.getProjectSystem(project) instanceof GradleProjectSystem;
  }
}
