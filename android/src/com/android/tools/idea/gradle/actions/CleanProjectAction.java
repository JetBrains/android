package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.gradle.util.ProjectBuilder;
import com.android.tools.idea.gradle.util.Projects;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;

/**
 * Invokes the "clean" Gradle task on a Gradle-based Android project.
 */
public class CleanProjectAction extends AnAction {
  public CleanProjectAction() {
    super("Clean Project");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      ProjectBuilder.getInstance(project).clean();
    }
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    boolean isGradleProject = project != null && Projects.requiresAndroidModel(project);
    e.getPresentation().setEnabledAndVisible(isGradleProject);
  }
}
