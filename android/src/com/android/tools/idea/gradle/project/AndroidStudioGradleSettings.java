package com.android.tools.idea.gradle.project;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.util.Collection;

/**
 * @author Vladislav.Soroka
 * @since 3/31/2016
 */

public class AndroidStudioGradleSettings extends GradleSettings{
  public AndroidStudioGradleSettings(@NotNull Project project) {
    super(project);
  }

  @Nullable
  @Override
  public GradleProjectSettings getLinkedProjectSettings(@NotNull String linkedProjectPath) {
    GradleProjectSettings projectSettings = super.getLinkedProjectSettings(linkedProjectPath);
    if (projectSettings != null && projectSettings.isResolveModulePerSourceSet()) {
      projectSettings.setResolveModulePerSourceSet(false);
    }
    return projectSettings;
  }

  @NotNull
  @Override
  public Collection<GradleProjectSettings> getLinkedProjectsSettings() {
    Collection<GradleProjectSettings> linkedProjectsSettings = super.getLinkedProjectsSettings();
    for (GradleProjectSettings projectSettings : linkedProjectsSettings) {
      if(projectSettings.isResolveModulePerSourceSet()){
        projectSettings.setResolveModulePerSourceSet(false);
      }
    }

    return linkedProjectsSettings;
  }
}
