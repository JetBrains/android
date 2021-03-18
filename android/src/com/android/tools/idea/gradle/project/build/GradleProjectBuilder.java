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

import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.serviceContainer.NonInjectable;
import org.jetbrains.annotations.NotNull;

/**
 * Builds a project, regardless of the compiler strategy being used (JPS or "direct Gradle invocation.")
 */
public final class GradleProjectBuilder {
  @NotNull private final Project myProject;
  @NotNull private final AndroidProjectInfo myAndroidProjectInfo;
  @NotNull private final GradleBuildInvoker myBuildInvoker;

  @NotNull
  public static GradleProjectBuilder getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleProjectBuilder.class);
  }

  public GradleProjectBuilder(@NotNull Project project){
    this(project, AndroidProjectInfo.getInstance(project), GradleBuildInvoker.getInstance(project));
  }

  @NonInjectable
  private GradleProjectBuilder(@NotNull Project project,
                              @NotNull AndroidProjectInfo androidProjectInfo,
                              @NotNull GradleBuildInvoker buildInvoker) {
    myProject = project;
    myAndroidProjectInfo = androidProjectInfo;
    myBuildInvoker = buildInvoker;
  }

  public void compileJava() {
    if (myAndroidProjectInfo.requiresAndroidModel()) {
      Module[] modules = ModuleManager.getInstance(myProject).getModules();
      myBuildInvoker.compileJava(modules, TestCompileType.ALL);
    }
  }

  public void clean() {
    if (myAndroidProjectInfo.requiresAndroidModel()) {
      myBuildInvoker.cleanProject();
    }
  }

  public void generateSources() {
    doGenerateSources(false /* do not clean project */);
  }

  private void doGenerateSources(boolean cleanProject) {
    if (myAndroidProjectInfo.requiresAndroidModel()) {
      if (cleanProject) {
        myBuildInvoker.cleanAndGenerateSources();
        return;
      }
      myBuildInvoker.generateSources();
    }
  }
}
