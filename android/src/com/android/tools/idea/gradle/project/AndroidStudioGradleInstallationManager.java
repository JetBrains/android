package com.android.tools.idea.gradle.project;

import static com.android.tools.idea.gradle.project.AndroidGradleProjectSettingsControlBuilder.ANDROID_STUDIO_JAVA_HOME_NAME;
import static com.android.tools.idea.gradle.project.AndroidGradleProjectSettingsControlBuilder.EMBEDDED_JDK_NAME;
import static com.android.tools.idea.sdk.IdeSdks.JDK_LOCATION_ENV_VARIABLE_NAME;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import java.io.File;
import org.gradle.internal.impldep.com.amazonaws.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

public class AndroidStudioGradleInstallationManager extends GradleInstallationManager {
  @Nullable
  @Override
  public Sdk getGradleJdk(@Nullable Project project, @NotNull String linkedProjectPath) {
    if ((project != null) && StudioFlags.ALLOW_JDK_PER_PROJECT.get()) {
      // GradleInstallationManager.getGradleJdk implementation calls getGradleJvmPath and generates a JDK from this result.
      return super.getGradleJdk(project, linkedProjectPath);
    }
    return IdeSdks.getInstance().getJdk();
  }

  @Nullable
  @Override
  public String getGradleJvmPath(@NotNull Project project, @NotNull String linkedProjectPath) {
    if (!StudioFlags.ALLOW_JDK_PER_PROJECT.get()) {
      @Nullable Sdk jdk = getGradleJdk(project, linkedProjectPath);
      if (jdk == null) {
        return null;
      }
      return jdk.getHomePath();
    }
    IdeSdks ideSdks = IdeSdks.getInstance();
    // Using environment variable
    if (IdeSdks.getInstance().isUsingEnvVariableJdk()) {
      return ideSdks.getEnvVariableJdkValue();
    }

    GradleProjectSettings settings = GradleSettings.getInstance(project).getLinkedProjectSettings(linkedProjectPath);
    if (settings != null) {
      String settingsJvm = settings.getGradleJvm();
      if (settingsJvm != null) {
        // Try to resolve from variables before looking in GradleInstallationManager
        switch (settingsJvm) {
          case JDK_LOCATION_ENV_VARIABLE_NAME:
            if (IdeSdks.getInstance().isJdkEnvVariableValid()) {
              return ideSdks.getEnvVariableJdkValue();
            }
          case EMBEDDED_JDK_NAME: {
            File embeddedPath = ideSdks.getEmbeddedJdkPath();
            if (embeddedPath != null) {
              return embeddedPath.getAbsolutePath();
            }
          }
          case ANDROID_STUDIO_JAVA_HOME_NAME: {
            String javaHome = IdeSdks.getJdkFromJavaHome();
            if (!StringUtils.isNullOrEmpty(javaHome)) {
              return javaHome;
            }
          }
        }
      }
    }
    // None of the environment variables is used (or is used but invalid), handle it in the same way IDEA does.
    return super.getGradleJvmPath(project, linkedProjectPath);
  }
}
