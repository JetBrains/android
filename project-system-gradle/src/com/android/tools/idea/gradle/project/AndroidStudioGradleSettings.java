package com.android.tools.idea.gradle.project;

import com.intellij.openapi.project.Project;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.settings.TestRunner;

public class AndroidStudioGradleSettings extends GradleSettings {
  public AndroidStudioGradleSettings(@NotNull Project project) {
    super(project);
  }

  @Nullable
  @Override
  public GradleProjectSettings getLinkedProjectSettings(@NotNull String linkedProjectPath) {
    GradleProjectSettings projectSettings = super.getLinkedProjectSettings(linkedProjectPath);
    if (projectSettings != null) {
      configureForAndroidStudio(projectSettings);
    }
    return projectSettings;
  }

  @NotNull
  @Override
  public Collection<GradleProjectSettings> getLinkedProjectsSettings() {
    Collection<GradleProjectSettings> linkedProjectsSettings = super.getLinkedProjectsSettings();
    for (GradleProjectSettings projectSettings : linkedProjectsSettings) {
      configureForAndroidStudio(projectSettings);
    }

    return linkedProjectsSettings;
  }

  static void configureForAndroidStudio(@NotNull GradleProjectSettings projectSettings) {
    projectSettings.setResolveModulePerSourceSet(true);
    projectSettings.setResolveExternalAnnotations(false);
    projectSettings.setTestRunner(TestRunner.CHOOSE_PER_TEST);
    projectSettings.setUseQualifiedModuleNames(true);
  }
}
