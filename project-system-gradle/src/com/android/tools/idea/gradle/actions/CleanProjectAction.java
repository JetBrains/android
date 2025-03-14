// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.gradle.project.build.GradleBuildState;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbModeBlockedFunctionality;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Invokes the "clean" Gradle task on a Gradle-based Android project.
 */
public class CleanProjectAction extends AndroidStudioGradleAction {
  public CleanProjectAction() {
    super("Clean Project");
  }

  @Override
  protected void doPerform(@NotNull AnActionEvent e, @NotNull Project project) {
    if (GradleBuildState.getInstance(project).isBuildInProgress()) {
      // Build is running, do not start clean (b/27896976)
      DumbService.getInstance(project).showDumbModeNotificationForFunctionality("Cannot clean project while build is in progress. " +
                                                                                "Please wait until the build finishes to clean the project.",
                                                                                DumbModeBlockedFunctionality.Android);
      return;
    }
    GradleBuildInvoker.getInstance(project).cleanProject();
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent e, @NotNull Project project) {
    boolean requiresAndroidModel = ProjectSystemUtil.requiresAndroidModel(project);
    e.getPresentation().setVisible(requiresAndroidModel);
    e.getPresentation().setEnabled(requiresAndroidModel && !isGradleSyncInProgress(project));
  }
}
