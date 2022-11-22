package com.android.tools.idea.gradle.project;

import static com.android.tools.idea.gradle.project.AndroidGradleProjectSettingsControlBuilder.ANDROID_STUDIO_JAVA_HOME_NAME;
import static com.android.tools.idea.gradle.project.AndroidGradleProjectSettingsControlBuilder.EMBEDDED_JDK_NAME;
import static com.android.tools.idea.sdk.IdeSdks.JDK_LOCATION_ENV_VARIABLE_NAME;
import static com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_JAVA_HOME;
import static com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_PROJECT_JDK;

import com.android.tools.idea.sdk.IdeSdks;
import com.android.utils.FileUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.gradle.internal.impldep.com.amazonaws.util.StringUtils;
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
            Path embeddedPath = ideSdks.getEmbeddedJdkPath();
            if (embeddedPath != null) {
              return embeddedPath.toAbsolutePath().toString();
            }
          }
          case USE_JAVA_HOME:
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

  public boolean isUsingJavaHomeJdk(@NotNull Project project) {
    String basePath = project.getBasePath();
    if (basePath == null) {
      return false;
    }
    String projectJvmPath = getGradleJvmPath(project, basePath);
    if (projectJvmPath == null) {
      return false;
    }
    String javaHome = IdeSdks.getJdkFromJavaHome();
    if (javaHome == null) {
      return false;
    }
    return FileUtils.isSameFile(new File(projectJvmPath), new File(javaHome));
  }

  /**
   * Creates or reuses a JDK with its default name and sets the project to use it.  Must be run inside a write action.
   * @param project Project to be modified to use the given JDK
   * @param jdkPath Path where the JDK is located
   */
  public static void setJdkAsProjectJdk(@NotNull Project project, @NotNull String jdkPath) {
    Sdk jdk = IdeSdks.getInstance().setJdkPath(Paths.get(jdkPath));
    ProjectRootManager.getInstance(project).setProjectSdk(jdk);
    String basePath = project.getBasePath();
    if (basePath != null) {
      GradleProjectSettings projectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(basePath);
      if (projectSettings != null) {
        projectSettings.setGradleJvm(USE_PROJECT_JDK);
      }
    }
  }
}
