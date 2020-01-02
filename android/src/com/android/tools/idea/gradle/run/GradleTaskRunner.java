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

import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.util.BuildMode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.IncorrectOperationException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.gradle.tooling.BuildAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface GradleTaskRunner {
  boolean run(@NotNull ListMultimap<Path, String> tasks, @Nullable BuildMode buildMode, @NotNull List<String> commandLineArguments)
    throws InvocationTargetException, InterruptedException;

  @NotNull
  static DefaultGradleTaskRunner newRunner(@NotNull Project project, @Nullable BuildAction<?> buildAction) {
    return new DefaultGradleTaskRunner(project, buildAction);
  }

  class DefaultGradleTaskRunner implements GradleTaskRunner {
    @NotNull private final Project myProject;
    @NotNull private final AtomicReference<Object> model = new AtomicReference<>();

    @Nullable final BuildAction<?> myBuildAction;

    DefaultGradleTaskRunner(@NotNull Project project, @Nullable BuildAction<?> buildAction) {
      myProject = project;
      myBuildAction = buildAction;
    }

    @Override
    public boolean run(@NotNull ListMultimap<Path, String> tasks, @Nullable BuildMode buildMode,
                       @NotNull List<String> commandLineArguments) {
      assert !ApplicationManager.getApplication().isDispatchThread();
      GradleBuildInvoker gradleBuildInvoker = GradleBuildInvoker.getInstance(myProject);

      AtomicBoolean success = new AtomicBoolean();
      CountDownLatch done = new CountDownLatch(1);
      Disposable signaller = () -> done.countDown();

      GradleBuildInvoker.AfterGradleInvocationTask afterTask = new GradleBuildInvoker.AfterGradleInvocationTask() {
        @Override
        public void execute(@NotNull GradleInvocationResult result) {
          success.set(result.isBuildSuccessful());
          model.set(result.getModel());
          gradleBuildInvoker.remove(this);
          Disposer.dispose(signaller); // Signal completion.
        }
      };

      try {
        // Signal the semaphore if the project is disposed before the transaction has chance to run.
        Disposer.register(myProject, signaller);
      }
      catch (IncorrectOperationException e) {
        return false; // The project is already disposed - bail out.
      }

      // To ensure that the "Run Configuration" waits for the Gradle tasks to be executed, we use TransactionGuard.submitTransaction.
      // IDEA also uses TransactionGuard.submitTransaction in this scenario (see CompileStepBeforeRun.)
      TransactionGuard.submitTransaction(myProject, () -> {
        gradleBuildInvoker.add(afterTask);
        gradleBuildInvoker.executeTasks(tasks, buildMode, commandLineArguments, myBuildAction);
      });

      try {
        done.await();
      }
      catch (InterruptedException e) {
        throw new ProcessCanceledException();
      }
      return success.get();
    }

    @Nullable
    public Object getModel() {
      return model.get();
    }

    @VisibleForTesting
    @Nullable
    BuildAction<?> getBuildAction() {
      return myBuildAction;
    }
  }
}
