package com.android.tools.idea.gradle.project;

import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;

/**
 * @author Vladislav.Soroka
 * @since 11/10/2015
 */
public class AndroidStudioGradleInstallationManager extends GradleInstallationManager {
  @Nullable
  @Override
  public Sdk getGradleJdk(@Nullable Project project, @NotNull String linkedProjectPath) {
    return IdeSdks.getJdk();
  }
}
