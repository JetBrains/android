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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.util.BuildMode;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.Semaphore;
import org.gradle.tooling.BuildAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public interface GradleTaskRunner {
  boolean run(@NotNull ListMultimap<Path, String> tasks, @Nullable BuildMode buildMode, @NotNull List<String> commandLineArguments)
    throws InvocationTargetException, InterruptedException;

  @NotNull
  static DefaultGradleTaskRunner newRunner(@NotNull Project project) {
    return new DefaultGradleTaskRunner(project);
  }

  @NotNull
  static DefaultGradleTaskRunner newBuildActionRunner(@NotNull Project project, @Nullable BuildAction buildAction) {
    return new DefaultGradleTaskRunner(project, buildAction);
  }

  class DefaultGradleTaskRunner implements GradleTaskRunner {
    @NotNull final Project myProject;
    @NotNull final AtomicReference<Object> model = new AtomicReference<>();

    @Nullable final BuildAction myBuildAction;

    DefaultGradleTaskRunner(@NotNull Project project) {
      this(project, null);
    }

    DefaultGradleTaskRunner(@NotNull Project project, @Nullable BuildAction buildAction) {
      myProject = project;
      myBuildAction = buildAction;
    }

    @Override
    public boolean run(@NotNull ListMultimap<Path, String> tasks, @Nullable BuildMode buildMode, @NotNull List<String> commandLineArguments)
      throws InvocationTargetException, InterruptedException {
      assert !ApplicationManager.getApplication().isDispatchThread();

      GradleBuildInvoker gradleBuildInvoker = GradleBuildInvoker.getInstance(myProject);

      AtomicBoolean success = new AtomicBoolean();
      Semaphore done = new Semaphore();
      done.down();

      GradleBuildInvoker.AfterGradleInvocationTask afterTask = new GradleBuildInvoker.AfterGradleInvocationTask() {
        @Override
        public void execute(@NotNull GradleInvocationResult result) {
          success.set(result.isBuildSuccessful());
          model.set(result.getModel());
          gradleBuildInvoker.remove(this);
          done.up();
        }
      };

      // To ensure that the "Run Configuration" waits for the Gradle tasks to be executed, we use TransactionGuard.submitTransaction.
      // IDEA also uses TransactionGuard.submitTransaction in this scenario (see CompileStepBeforeRun.)
      TransactionGuard.submitTransaction(myProject, () -> {
        gradleBuildInvoker.add(afterTask);
        gradleBuildInvoker.executeTasks(tasks, buildMode, commandLineArguments, myBuildAction);
      });

      done.waitFor();
      return success.get();
    }

    @Nullable
    public Object getModel() {
      return model.get();
    }

    @VisibleForTesting
    @Nullable
    BuildAction getBuildAction() {
      return myBuildAction;
    }
  }
}
