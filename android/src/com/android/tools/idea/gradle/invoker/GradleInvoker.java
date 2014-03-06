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
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidCommonUtils;
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

  public void cleanProject(@Nullable GradleTaskExecutionListener listener) {
    setProjectBuildMode(BuildMode.CLEAN);
    List<String> tasks = Lists.newArrayList(GradleBuilds.CLEAN_TASK_NAME);
    executeTasks(tasks, createExecutionListener(listener));
  }

  public void generateSources(@Nullable final GradleTaskExecutionListener listener) {
    BuildMode buildMode = BuildMode.SOURCE_GEN;
    setProjectBuildMode(buildMode);

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    List<String> tasks = findTasksToExecute(moduleManager.getModules(), buildMode, GradleBuilds.TestCompileContext.NONE);

    executeTasks(tasks, createExecutionListener(new GradleTaskExecutionListener() {
      @Override
      public void executionStarted(@NotNull List<String> tasks) {
      }

      @Override
      public void executionEnded(@NotNull List<String> tasks, int errorCount, int warningCount) {
        Projects.notifyProjectSyncCompleted(myProject, errorCount == 0);
      }
    }, listener));
  }

  public void compileJava(@NotNull DataContext dataContext, @Nullable GradleTaskExecutionListener listener) {
    BuildMode buildMode = BuildMode.COMPILE_JAVA;
    setProjectBuildMode(buildMode);

    Module[] modules = Projects.getModulesToBuildFromSelection(myProject, dataContext);
    List<String> tasks = findTasksToExecute(modules, buildMode, GradleBuilds.TestCompileContext.NONE);

    executeTasks(tasks, createExecutionListener(listener));
  }

  public void make(@Nullable GradleTaskExecutionListener listener, @Nullable RunConfiguration runConfiguration) {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    make(moduleManager.getModules(), listener, runConfiguration);
  }

  public void make(@NotNull DataContext dataContext,
                   @Nullable GradleTaskExecutionListener listener,
                   @Nullable RunConfiguration runConfiguration) {
    Module[] modules;
    if (runConfiguration instanceof ModuleBasedConfiguration) {
      // ModuleBasedConfiguration includes Android and JUnit run configurations.
      modules = ((ModuleBasedConfiguration)runConfiguration).getModules();
    }
    else {
      modules = Projects.getModulesToBuildFromSelection(myProject, dataContext);
    }

    make(modules, listener, runConfiguration);
  }

  private void make(@NotNull Module[] modules,
                    @Nullable GradleTaskExecutionListener listener,
                    @Nullable RunConfiguration runConfiguration) {
    BuildMode buildMode = BuildMode.MAKE;
    setProjectBuildMode(buildMode);

    GradleBuilds.TestCompileContext testCompileContext = getTestCompileContext(runConfiguration);
    List<String> tasks = findTasksToExecute(modules, buildMode, testCompileContext);
    executeTasks(tasks, createExecutionListener(listener));
  }

  @NotNull
  private static GradleBuilds.TestCompileContext getTestCompileContext(@Nullable RunConfiguration runConfiguration) {
    if (runConfiguration != null) {
      String id = runConfiguration.getType().getId();
      if (AndroidCommonUtils.isInstrumentationTestConfiguration(id)) {
        return GradleBuilds.TestCompileContext.ANDROID_TESTS;
      }
      if (AndroidCommonUtils.isTestConfiguration(id)) {
        return GradleBuilds.TestCompileContext.JAVA_TESTS;
      }
    }
    return GradleBuilds.TestCompileContext.NONE;
  }

  public void rebuild(GradleTaskExecutionListener listener) {
    BuildMode buildMode = BuildMode.REBUILD;
    setProjectBuildMode(buildMode);

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    List<String> tasks = findTasksToExecute(moduleManager.getModules(), buildMode, GradleBuilds.TestCompileContext.NONE);
    if (!tasks.isEmpty()) {
      tasks.add(0, GradleBuilds.CLEAN_TASK_NAME);
    }

    executeTasks(tasks, createExecutionListener(listener));
  }

  private void setProjectBuildMode(@NotNull BuildMode buildMode) {
    Projects.setProjectBuildMode(myProject, buildMode);
  }

  @NotNull
  private GradleTaskExecutionListener createExecutionListener(@NotNull final GradleTaskExecutionListener... original) {
    return new GradleTaskExecutionListener() {
      @Override
      public void executionStarted(@NotNull List<String> tasks) {
        for (GradleTaskExecutionListener listener : original) {
          if (listener != null) {
            listener.executionStarted(tasks);
          }
        }
      }

      @Override
      public void executionEnded(@NotNull List<String> tasks, int errorCount, int warningCount) {
        Projects.removeBuildDataFrom(myProject);
        for (GradleTaskExecutionListener listener : original) {
          if (listener != null) {
            listener.executionEnded(tasks, errorCount, warningCount);
          }
        }
      }
    };
  }

  private List<String> findTasksToExecute(@NotNull Module[] modules,
                                          @NotNull BuildMode buildMode,
                                          @NotNull GradleBuilds.TestCompileContext testCompileContext) {
    List<String> tasks = Lists.newArrayList();
    for (Module module : modules) {
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

  public void executeTasks(@NotNull final List<String> tasks, @Nullable final GradleTaskExecutionListener listener) {
    if (tasks.isEmpty()) {
      return;
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (listener != null) {
        listener.executionStarted(tasks);
        listener.executionEnded(tasks, 0, 0);
      }
      return;
    }

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
        if (listener != null) {
          listener.executionStarted(tasks);
        }
        GradleTasksExecutor executor = new GradleTasksExecutor(myProject, tasks, listener);
        executor.queue();
      }
    });
  }
}
