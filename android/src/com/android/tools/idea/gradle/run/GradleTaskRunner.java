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

import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.gradle.util.BuildMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class GradleTaskRunner {
  private final Project myProject;

  public GradleTaskRunner(@NotNull Project project) {
    myProject = project;
  }

  public boolean run(@NotNull List<String> tasks, @Nullable BuildMode buildMode, @NotNull List<String> commandLineArguments)
    throws InvocationTargetException, InterruptedException {
    assert !ApplicationManager.getApplication().isDispatchThread();

    final GradleInvoker gradleInvoker = GradleInvoker.getInstance(myProject);

    final AtomicBoolean success = new AtomicBoolean();
    final Semaphore done = new Semaphore();
    done.down();

    final GradleInvoker.AfterGradleInvocationTask afterTask = new GradleInvoker.AfterGradleInvocationTask() {
      @Override
      public void execute(@NotNull GradleInvocationResult result) {
        success.set(result.isBuildSuccessful());
        gradleInvoker.removeAfterGradleInvocationTask(this);
        done.up();
      }
    };

    // To ensure that the "Run Configuration" waits for the Gradle tasks to be executed, we use SwingUtilities.invokeAndWait. I tried
    // using Application.invokeAndWait but it never worked. IDEA also uses SwingUtilities in this scenario (see CompileStepBeforeRun.)
    SwingUtilities.invokeAndWait(() -> {
      gradleInvoker.addAfterGradleInvocationTask(afterTask);
      gradleInvoker.executeTasks(tasks, buildMode, commandLineArguments);
    });

    done.waitFor();
    return success.get();
  }
}
