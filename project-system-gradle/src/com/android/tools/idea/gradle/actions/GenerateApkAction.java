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

import com.android.tools.idea.projectsystem.BuildApkActionToken;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class GenerateApkAction extends AnAction {
  private static final String ACTION_TEXT = "Generate APKs";

  public GenerateApkAction() {
    super(ACTION_TEXT);
  }

  @NotNull
  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null || !TrustedProjects.isProjectTrusted(project)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    boolean isSupported = BuildApkActionToken.isSupported(project);
    e.getPresentation().setEnabledAndVisible(isSupported);
    if (isSupported) {
      boolean syncInProgress = ProjectSystemUtil.getProjectSystem(project).getSyncManager().isSyncInProgress();
      e.getPresentation().setEnabled(!syncInProgress);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null && TrustedProjects.isProjectTrusted(project)) {
      BuildApkActionToken.execute(project);
    }
  }
}
