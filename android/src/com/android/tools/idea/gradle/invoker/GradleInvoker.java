/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.invoker;

import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.GradleBuilds;
import com.android.tools.idea.gradle.util.Projects;
import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.util.List;

/**
 * Invokes Gradle tasks directly. Results of tasks execution are displayed in both the "Messages" tool window and the new "Gradle Console"
 * tool window.
 */
public class GradleInvoker {
  private static final Logger LOG = Logger.getInstance(GradleInvoker.class);

  @NotNull private final Project myProject;

  public static GradleInvoker getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleInvoker.class);
  }

  public GradleInvoker(@NotNull Project project) {
    myProject = project;
  }

  public void cleanProject() {
    List<String> tasks = Lists.newArrayList(GradleBuilds.CLEAN_TASK_NAME);
    executeTasks(tasks, null);
  }

  public void generateSources() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    List<String> tasks = findTasksToExecute(moduleManager.getModules(), BuildMode.SOURCE_GEN, GradleBuilds.TestCompileContext.NONE);
    executeTasks(tasks, new Runnable() {
      @Override
      public void run() {
        Projects.notifyProjectSyncCompleted(myProject, true);
      }
    });
  }

  public void compileJava(@NotNull DataContext dataContext) {
    Module[] modules = Projects.getModulesToBuildFromSelection(myProject, dataContext);
    List<String> tasks = findTasksToExecute(modules, BuildMode.COMPILE_JAVA, GradleBuilds.TestCompileContext.NONE);
    executeTasks(tasks, null);
  }

  public void make() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    List<String> tasks = findTasksToExecute(moduleManager.getModules(), BuildMode.MAKE, GradleBuilds.TestCompileContext.NONE);
    executeTasks(tasks, null);
  }

  public void make(@NotNull DataContext dataContext) {
    Module[] modules = Projects.getModulesToBuildFromSelection(myProject, dataContext);
    List<String> tasks = findTasksToExecute(modules, BuildMode.MAKE, GradleBuilds.TestCompileContext.NONE);
    executeTasks(tasks, null);
  }

  public void rebuild() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    List<String> tasks = findTasksToExecute(moduleManager.getModules(), BuildMode.REBUILD, GradleBuilds.TestCompileContext.NONE);
    if (!tasks.isEmpty()) {
      tasks.add(0, GradleBuilds.CLEAN_TASK_NAME);
    }
    executeTasks(tasks, null);
  }

  private List<String> findTasksToExecute(@NotNull Module[] modules, @NotNull BuildMode buildMode,
                                          GradleBuilds.TestCompileContext testCompileContext) {
    List<String> tasks = Lists.newArrayList();
    for (Module module: modules) {
      AndroidGradleFacet androidGradleFacet = AndroidGradleFacet.getInstance(module);
      if (androidGradleFacet == null) {
        continue;
      }
      String gradleProjectPath = androidGradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      JpsAndroidModuleProperties properties = androidFacet != null ? androidFacet.getProperties() : null;
      GradleBuilds.findAndAddBuildTask(module.getName(), buildMode, gradleProjectPath, properties, tasks, testCompileContext);
    }

    if (tasks.isEmpty()) {
      // Unlikely to happen.
      String format = "Unable to find Gradle tasks for project '%1$s' using BuildMode %2$s";
      LOG.info(String.format(format, myProject.getName(), buildMode.name()));
    }
    return tasks;
  }

  public void executeTasks(final List<String> tasks, final @Nullable Runnable afterInvocationTask) {
    if (tasks.isEmpty()) {
      return;
    }
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        GradleTasksExecutor executor = new GradleTasksExecutor(myProject, tasks, afterInvocationTask);
        executor.queue();
      }
    });
  }
}
