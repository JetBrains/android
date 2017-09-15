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
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public interface GradleTaskRunner {
  String USE_SPLIT_APK = "USE_SPLIT_APK";

  boolean run(@NotNull ListMultimap<Path, String> tasks, @Nullable BuildMode buildMode, @NotNull List<String> commandLineArguments)
    throws InvocationTargetException, InterruptedException;

  static GradleTaskRunner newRunner(@NotNull Project project) {
    return new GradleTaskRunner() {
      @Override
      public boolean run(@NotNull ListMultimap<Path, String> tasks,
                         @Nullable BuildMode buildMode,
                         @NotNull List<String> commandLineArguments) {
        assert !ApplicationManager.getApplication().isDispatchThread();

        final GradleBuildInvoker gradleBuildInvoker = GradleBuildInvoker.getInstance(project);

        final AtomicBoolean success = new AtomicBoolean();
        final Semaphore done = new Semaphore();
        done.down();

        final GradleBuildInvoker.AfterGradleInvocationTask afterTask = new GradleBuildInvoker.AfterGradleInvocationTask() {
          @Override
          public void execute(@NotNull GradleInvocationResult result) {
            success.set(result.isBuildSuccessful());
            gradleBuildInvoker.remove(this);
            done.up();
          }
        };

        // To ensure that the "Run Configuration" waits for the Gradle tasks to be executed, we use SwingUtilities.invokeAndWait. I tried
        // using Application.invokeAndWait but it never worked. IDEA also uses SwingUtilities in this scenario (see CompileStepBeforeRun.)
        TransactionGuard.submitTransaction(project, () -> {
          gradleBuildInvoker.add(afterTask);
          gradleBuildInvoker.executeTasks(tasks, buildMode, commandLineArguments);
        });

        done.waitFor();
        return success.get();
      }
    };
  }
}
