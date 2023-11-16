/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.idea.execution.common;

import com.android.sdklib.AndroidVersion;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidSessionInfo {
  public static final Key<AndroidSessionInfo> KEY = new Key<>("KEY");
  public static final Key<AndroidVersion> ANDROID_DEVICE_API_LEVEL = new Key<>("ANDROID_DEVICE_API_LEVEL");

  @Nullable private final RunConfiguration myRunConfiguration;
  @NotNull private final ExecutionTarget myExecutionTarget;

  @NotNull
  public static AndroidSessionInfo create(@NotNull ProcessHandler processHandler,
                                          @Nullable RunConfiguration runConfiguration,
                                          @NotNull ExecutionTarget executionTarget) {
    AndroidSessionInfo result = new AndroidSessionInfo(runConfiguration, executionTarget);
    processHandler.putUserData(KEY, result);
    return result;
  }

  private AndroidSessionInfo(@Nullable RunConfiguration runConfiguration,
                             @NotNull ExecutionTarget executionTarget) {
    myRunConfiguration = runConfiguration;
    myExecutionTarget = executionTarget;
  }

  @NotNull
  public ExecutionTarget getExecutionTarget() {
    return myExecutionTarget;
  }

  @Nullable
  public RunConfiguration getRunConfiguration() {
    return myRunConfiguration;
  }

  /**
   * Find all the actively running session in the a given progject.
   */
  @Nullable
  public static List<AndroidSessionInfo> findActiveSession(@NotNull Project project) {
    return Arrays.stream(ExecutionManager.getInstance(project).getRunningProcesses()).map(
      handler -> handler.getUserData(KEY)).filter(Objects::nonNull).collect(Collectors.toList());
  }
}
