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
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public final class GradleTaskRunner {
  @WorkerThread
  public static AssembleInvocationResult run(@NotNull Project project,
                                             @NotNull Module[] assembledModules,
                                             @NotNull Map<Path, Collection<String>> tasks,
                                             @NotNull BuildMode buildMode,
                                             @NotNull List<String> commandLineArguments) {
    assert !ApplicationManager.getApplication().isDispatchThread();
    GradleBuildInvoker gradleBuildInvoker = GradleBuildInvoker.getInstance(project);

    List<GradleBuildInvoker.Request> requests = tasks.keySet().stream()
      .map(path ->
             GradleBuildInvoker.Request
               .builder(project, path.toFile(), tasks.get(path))
               .setMode(buildMode)
               .setCommandLineArguments(commandLineArguments)
               .build())
      .collect(Collectors.toList());

    ListenableFuture<AssembleInvocationResult> future = gradleBuildInvoker.executeAssembleTasks(assembledModules, requests);

    try {
      return future.get();
    }
    catch (InterruptedException e) {
      throw new ProcessCanceledException();
    }
    catch (ExecutionException e) {
      // Build failures are not returned this way.
      throw new RuntimeException(e);
    }
  }
}

