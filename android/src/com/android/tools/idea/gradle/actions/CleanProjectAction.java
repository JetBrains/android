package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.intellij.openapi.actionSystem.AnActionEvent;
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
    GradleProjectBuilder.getInstance(project).clean();
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent e, @NotNull Project project) {
  }
}
