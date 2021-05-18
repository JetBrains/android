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
package com.android.tools.idea.gradle.project.build.invoker;

import static com.android.tools.idea.gradle.util.GradleUtil.hasCause;

import java.util.List;
import org.gradle.tooling.BuildCancelledException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class GradleInvocationResult {
  @NotNull private final List<String> myTasks;
  @Nullable private final Object myModel;
  @Nullable private final Throwable myBuildError;
  private final boolean myBuildCancelled;

  public GradleInvocationResult(@NotNull List<String> tasks, @Nullable Throwable buildError) {
    this(tasks, buildError, null);
  }

  public GradleInvocationResult(@NotNull List<String> tasks,
                                @Nullable Throwable buildError,
                                @Nullable Object model) {
    myTasks = tasks;
    myBuildError = buildError;
    myBuildCancelled = (buildError != null && hasCause(buildError, BuildCancelledException.class));
    myModel = model;
  }

  @NotNull
  public List<String> getTasks() {
    return myTasks;
  }

  public boolean isBuildSuccessful() {
    return myBuildError == null;
  }

  public boolean isBuildCancelled() {
    return myBuildCancelled;
  }

  @Nullable
  public Object getModel() {
    return myModel;
  }

  /**
   * In production, the build error is intentionally wrapped, with relevant information exposed
   * only through a public API, but for tests it could be useful to access it directly.
   */
  @TestOnly
  @Nullable
  public Throwable getBuildError() {
    return myBuildError;
  }
}
