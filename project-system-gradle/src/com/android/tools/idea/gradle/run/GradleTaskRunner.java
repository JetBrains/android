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

import com.android.annotations.concurrency.WorkerThread;
import com.android.tools.idea.gradle.project.build.invoker.AssembleInvocationResult;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.util.BuildMode;
import com.google.common.collect.ListMultimap;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface GradleTaskRunner {
  boolean run(@NotNull Module[] assembledModules, @NotNull ListMultimap<Path, String> tasks, @Nullable BuildMode buildMode, @NotNull List<String> commandLineArguments);

  @NotNull
  static DefaultGradleTaskRunner newRunner(@NotNull Project project) {
    return new DefaultGradleTaskRunner(project);
  }

  class DefaultGradleTaskRunner implements GradleTaskRunner {
    @NotNull private final Project myProject;
    @NotNull private final AtomicReference<Object> model = new AtomicReference<>();

    DefaultGradleTaskRunner(@NotNull Project project) {
      myProject = project;
    }

    /**
     * This method will deadlock if invoked on the UI thread.
     */
    @Override
    @WorkerThread
    public boolean run(@NotNull Module[] assembledModules,
                       @NotNull ListMultimap<Path, String> tasks,
                       @Nullable BuildMode buildMode,
                       @NotNull List<String> commandLineArguments) {
      assert !ApplicationManager.getApplication().isDispatchThread();
      GradleBuildInvoker gradleBuildInvoker = GradleBuildInvoker.getInstance(myProject);

      List<GradleBuildInvoker.Request> requests = tasks.keySet().stream()
        .map(path ->
               GradleBuildInvoker.Request
                 .builder(myProject, path.toFile(), tasks.get(path))
                 .setMode(buildMode)
                 .setCommandLineArguments(commandLineArguments)
                 .build())
        .collect(Collectors.toList());

      ListenableFuture<AssembleInvocationResult> future = gradleBuildInvoker.executeAssembleTasks(assembledModules, requests);

      try {
        future.get().getInvocationResult().getModels().stream()
          // Composite builds are not properly supported with AGPs 3.x and we ignore a possibility of receiving multiple models here.
          // `PostBuildModel`s were not designed to handle this.
          .findFirst()
          .ifPresent(model::set);
        return true;
      }
      catch (InterruptedException e) {
        throw new ProcessCanceledException();
      }
      catch (ExecutionException e) {
        Logger.getInstance(DefaultGradleTaskRunner.class).error(e);
        return false;
      }
    }

    @Nullable
    public Object getModel() {
      return model.get();
    }
  }
}
