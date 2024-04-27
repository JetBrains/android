package com.android.tools.idea.gradle.project;

import static com.android.tools.idea.sdk.IdeSdks.JDK_LOCATION_ENV_VARIABLE_NAME;
import static com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_JAVA_HOME;

import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

public class AndroidStudioGradleInstallationManager extends GradleInstallationManager {

  @Nullable
  @Override
  public String getGradleJvmPath(@NotNull Project project, @NotNull String linkedProjectPath) {
    IdeSdks ideSdks = IdeSdks.getInstance();
    // Using environment variable
    if (ideSdks.isUsingEnvVariableJdk()) {
      return ideSdks.getEnvVariableJdkValue();
    }

    GradleProjectSettings settings = GradleSettings.getInstance(project).getLinkedProjectSettings(linkedProjectPath);
    if (settings != null) {
      String settingsJvm = settings.getGradleJvm();
      if (settingsJvm != null) {
        // Try to resolve from variables before looking in GradleInstallationManager
        switch (settingsJvm) {
          case JDK_LOCATION_ENV_VARIABLE_NAME:
              return ideSdks.getEnvVariableJdkValue();
          case USE_JAVA_HOME: {
              return ideSdks.getJdkFromJavaHome();
          }
        }
      }
    }
    // None of the environment variables is used (or is used but invalid), handle it in the same way IDEA does.
    return super.getGradleJvmPath(project, linkedProjectPath);
  }
}
