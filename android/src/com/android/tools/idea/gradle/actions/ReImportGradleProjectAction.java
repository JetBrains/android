/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.importing.GradleProjectImporter;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.tools.idea.Projects.getBaseDirPath;

public class ReImportGradleProjectAction extends AndroidStudioGradleAction {
  private static final String DEFAULT_TITLE = "Re-Import Gradle Project";
  private static final Logger LOG = Logger.getInstance(CleanImportProjectAction.class);

  @NotNull private String myTitle;
  @NotNull private GradleProjectImporter myImporter;

  public ReImportGradleProjectAction() {
    this(DEFAULT_TITLE);
  }

  public ReImportGradleProjectAction(@NotNull String title) {
    this(title, GradleProjectImporter.getInstance());
  }

  @VisibleForTesting
  ReImportGradleProjectAction(@NotNull String title, @NotNull GradleProjectImporter gradleImporter) {
    super(title);
    myTitle = title;
    myImporter = gradleImporter;
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent e, @NotNull Project project) {
    boolean enabled = !GradleSyncState.getInstance(project).isSyncInProgress();
    e.getPresentation().setEnabled(enabled);
  }

  @Override
  protected void doPerform(@NotNull AnActionEvent e, @NotNull Project project) {
    String projectName = project.getName();
    LOG.info(String.format("Re-importing project '%1$s'...", projectName));
    File projectDirPath = close(project);
    try {
      LOG.info(String.format("About to import project '%1$s'.", projectName));
      myImporter.importProject(projectName, projectDirPath, null);
      LOG.info(String.format("Done importing project '%1$s'.", projectName));
    }
    catch (Exception error) {
      Messages.showErrorDialog(error.getMessage(), myTitle);
      LOG.info(String.format("Failed to import project '%1$s'.", projectName));
    }
  }

  private static File close(@NotNull Project project) {
    String projectName = project.getName();
    File projectDirPath = getBaseDirPath(project);
    ProjectManagerEx.getInstanceEx().closeAndDispose(project);
    RecentProjectsManager.getInstance().removePath(PathUtil.toSystemIndependentName(projectDirPath.getPath()));
    WelcomeFrame.showIfNoProjectOpened();
    LOG.info(String.format("Closed project '%1$s'.", projectName));
    return projectDirPath;
  }
}
