/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.project.build;

import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.util.BuildMode.*;

/**
 * Builds a project, regardless of the compiler strategy being used (JPS or "direct Gradle invocation.")
 */
public final class GradleProjectBuilder {
  @NotNull private final Project myProject;

  @NotNull
  public static GradleProjectBuilder getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleProjectBuilder.class);
  }

  public GradleProjectBuilder(@NotNull Project project) {
    myProject = project;
  }

  public void compileJava() {
    if (AndroidProjectInfo.getInstance(myProject).requiresAndroidModel()) {
      if (GradleProjectInfo.getInstance(myProject).isDirectGradleBuildEnabled()) {
        Module[] modules = ModuleManager.getInstance(myProject).getModules();
        GradleBuildInvoker.getInstance(myProject).compileJava(modules, TestCompileType.ALL);
        return;
      }
      buildProjectWithJps(COMPILE_JAVA);
    }
  }

  public void clean() {
    if (AndroidProjectInfo.getInstance(myProject).requiresAndroidModel()) {
      if (GradleProjectInfo.getInstance(myProject).isDirectGradleBuildEnabled()) {
        GradleBuildInvoker.getInstance(myProject).cleanProject();
        return;
      }
      buildProjectWithJps(CLEAN);
    }
  }

  public void cleanAndGenerateSources() {
    doGenerateSources(true /* clean project */);
  }

  public void generateSources() {
    doGenerateSources(false /* do not clean project */);
  }

  private void doGenerateSources(boolean cleanProject) {
    if (!isSourceGenerationEnabled()) {
      return;
    }
    if (AndroidProjectInfo.getInstance(myProject).requiresAndroidModel()) {
      if (GradleProjectInfo.getInstance(myProject).isDirectGradleBuildEnabled()) {
        if (cleanProject) {
          GradleBuildInvoker.getInstance(myProject).cleanAndGenerateSources();
          return;
        }
        GradleBuildInvoker.getInstance(myProject).generateSources();
        return;
      }
      buildProjectWithJps(SOURCE_GEN);
    }
  }

  public boolean isSourceGenerationEnabled() {
    if (AndroidProjectInfo.getInstance(myProject).requiresAndroidModel()) {
      int moduleCount = ModuleManager.getInstance(myProject).getModules().length;
      GradleExperimentalSettings settings = GradleExperimentalSettings.getInstance();
      return isSourceGenerationEnabled(settings, moduleCount);
    }
    return false;
  }

  @VisibleForTesting
  @Contract(pure = true)
  static boolean isSourceGenerationEnabled(@NotNull GradleExperimentalSettings settings, int moduleCount) {
    return !settings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC || moduleCount <= settings.MAX_MODULE_COUNT_FOR_SOURCE_GEN;
  }

  private void buildProjectWithJps(@NotNull BuildMode buildMode) {
    BuildSettings.getInstance(myProject).setBuildMode(buildMode);
    CompilerManager.getInstance(myProject).make(null);
  }
}
