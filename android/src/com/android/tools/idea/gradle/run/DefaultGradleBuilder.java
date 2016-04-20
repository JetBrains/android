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

import com.android.tools.idea.gradle.util.BuildMode;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class DefaultGradleBuilder implements BeforeRunBuilder {
  private final List<String> myTasks;
  private final BuildMode myBuildMode;

  public DefaultGradleBuilder(@NotNull List<String> tasks, @Nullable BuildMode buildMode) {
    myTasks = tasks;
    myBuildMode = buildMode;
  }

  @Override
  public boolean build(@NotNull GradleTaskRunner taskRunner, @NotNull List<String> cmdLineArguments) throws InterruptedException,
                                                                                                            InvocationTargetException {
    if (myTasks.isEmpty()) {
      // This shouldn't happen, but if it does, then GradleInvoker with an empty list of tasks seems to hang forever, so we error out.
      Logger.getInstance(DefaultGradleBuilder.class).error("Unable to determine gradle tasks to execute");
      return false;
    }
    return taskRunner.run(myTasks, myBuildMode, cmdLineArguments);
  }
}
