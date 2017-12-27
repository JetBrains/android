package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.gradle.project.build.GradleBuildState;
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.intellij.openapi.actionSystem.AnActionEvent;
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
      DumbService.getInstance(project).showDumbModeNotification("Cannot clean project while build is in progress. " +
                                                                "Please wait until the build finishes to clean the project.");
      return;
    }
    GradleProjectBuilder.getInstance(project).clean();
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent e, @NotNull Project project) {
  }
}
