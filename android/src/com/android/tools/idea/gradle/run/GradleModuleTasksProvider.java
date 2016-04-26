/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.run;

import com.android.tools.idea.fd.InstantRunGradleUtils;
import com.android.tools.idea.fd.InstantRunTasksProvider;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.gradle.util.BuildMode;
import com.google.common.collect.Lists;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class GradleModuleTasksProvider implements InstantRunTasksProvider {
  private final Module[] myModules;

  GradleModuleTasksProvider(@NotNull Module[] modules) {
    myModules = modules;
    if (myModules.length == 0) {
      throw new IllegalArgumentException("No modules provided");
    }
  }

  @NotNull
  @Override
  public List<String> getCleanAndGenerateSourcesTasks() {
    List<String> tasks = Lists.newArrayList();

    tasks.addAll(GradleInvoker.findCleanTasksForModules(myModules));
    tasks.addAll(GradleInvoker.findTasksToExecute(myModules, BuildMode.SOURCE_GEN, GradleInvoker.TestCompileType.NONE));

    return tasks;
  }

  @NotNull
  public List<String> getUnitTestTasks(@NotNull BuildMode buildMode) {
    // Make sure all "intermediates/classes" directories are up-to-date.
    Module[] affectedModules = getAffectedModules(myModules[0].getProject(), myModules);
    return GradleInvoker.findTasksToExecute(affectedModules, buildMode, GradleInvoker.TestCompileType.JAVA_TESTS);
  }

  @NotNull
  private static Module[] getAffectedModules(@NotNull Project project, @NotNull Module[] modules) {
    final CompilerManager compilerManager = CompilerManager.getInstance(project);
    CompileScope scope = compilerManager.createModulesCompileScope(modules, true, true);
    return scope.getAffectedModules();
  }

  @NotNull
  @Override
  public List<String> getIncrementalBuildTasks() {
    Module module = myModules[0];
    AndroidGradleModel model = AndroidGradleModel.get(module);
    if (model == null) {
      throw new IllegalStateException("Attempted to obtain incremental dex task for module that does not have a Gradle facet");
    }

    return Collections.singletonList(InstantRunGradleUtils.getIncrementalDexTask(model, module));
  }

  @NotNull
  @Override
  public List<String> getFullBuildTasks() {
    return getTasksFor(BuildMode.ASSEMBLE, GradleInvoker.TestCompileType.NONE);
  }

  @NotNull
  public List<String> getTasksFor(@NotNull BuildMode buildMode, @NotNull GradleInvoker.TestCompileType testCompileType) {
    return GradleInvoker.findTasksToExecute(myModules, buildMode, testCompileType);
  }
}
