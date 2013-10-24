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

import com.android.builder.model.ArtifactInfo;
import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.util.Projects;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GradleInvoker {
  private static final Logger LOG = Logger.getInstance(GradleInvoker.class);
  @NotNull private static final String CLEAN_TASK_NAME = "clean";

  @NotNull private final Project myProject;

  public static GradleInvoker getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleInvoker.class);
  }

  public GradleInvoker(@NotNull Project project) {
    myProject = project;
  }

  public void generateSources() {
    List<String> tasks = findTasksToExecute(new Function<ArtifactInfo, String>() {
      @Override
      public String fun(ArtifactInfo artifactInfo) {
        return artifactInfo.getSourceGenTaskName();
      }
    }, "generate sources");
    executeTasks(tasks, new Runnable() {
      @Override
      public void run() {
        Projects.notifyProjectSyncCompleted(myProject, true);
      }
    });
  }

  public void compileJava() {
    List<String> tasks = findTasksToExecute(new Function<ArtifactInfo, String>() {
      @Override
      public String fun(ArtifactInfo artifactInfo) {
        return artifactInfo.getJavaCompileTaskName();
      }
    }, "compile Java code");
    executeTasks(tasks, null);
  }

  public void make() {
    List<String> tasks = findTasksToExecute(new Function<ArtifactInfo, String>() {
      @Override
      public String fun(ArtifactInfo artifactInfo) {
        return artifactInfo.getAssembleTaskName();
      }
    }, "make project");
    executeTasks(tasks, null);
  }

  public void rebuild() {
    List<String> tasks = findTasksToExecute(new Function<ArtifactInfo, String>() {
      @Override
      public String fun(ArtifactInfo artifactInfo) {
        return artifactInfo.getAssembleTaskName();
      }
    }, "rebuild project");
    if (!tasks.isEmpty()) {
      tasks.add(0, CLEAN_TASK_NAME);
    }
    executeTasks(tasks, null);
  }

  @NotNull
  private List<String> findTasksToExecute(@NotNull Function<ArtifactInfo, String> f, @NotNull String actionToPerform) {
    List<String> tasks = Lists.newArrayList();

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && facet.getIdeaAndroidProject() != null) {
        Variant selectedVariant = facet.getIdeaAndroidProject().getSelectedVariant();
        ArtifactInfo mainArtifactInfo = selectedVariant.getMainArtifactInfo();
        String task = f.fun(mainArtifactInfo);
        if (!Strings.isNullOrEmpty(task)) {
          tasks.add(task);
        }
      }
    }

    if (tasks.isEmpty()) {
      // Unlikely to happen.
      String msg = String.format("Unable to find Gradle tasks that %1$s for project '%2$s'", actionToPerform, myProject.getName());
      LOG.info(msg);
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
