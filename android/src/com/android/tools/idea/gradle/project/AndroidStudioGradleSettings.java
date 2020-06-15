package com.android.tools.idea.gradle.project;

import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.util.Collection;
import org.jetbrains.plugins.gradle.settings.TestRunner;

public class AndroidStudioGradleSettings extends GradleSettings{
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
    projectSettings.setResolveModulePerSourceSet(false);
    projectSettings.setTestRunner(TestRunner.PLATFORM);
    projectSettings.setUseAutoImport(false);
    projectSettings.setUseQualifiedModuleNames(false);

    // Workaround to make integration (non-UI tests pass)
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      Sdk jdk = IdeSdks.getInstance().getJdk();
      if (jdk != null) {
        projectSettings.setGradleJvm(jdk.getName());
      }
    }
  }
}
