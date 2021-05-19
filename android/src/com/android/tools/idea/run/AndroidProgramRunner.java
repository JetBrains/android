/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.tools.idea.run.deployment.AndroidExecutionTarget;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.ExecutionTargetManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.project.Project;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

/**
 * A common base class for ProgramRunners that deal with local Android devices.
 * Note that the generics parameter to {@link GenericProgramRunner} isn't used
 * at the moment, and may be changed as needed.
 */
public abstract class AndroidProgramRunner implements ProgramRunner<RunnerSettings> {
  final @NotNull Function<@NotNull Project, @NotNull ExecutionTarget> myGetActiveTarget;

  public AndroidProgramRunner() {
    this(ExecutionTargetManager::getActiveTarget);
  }

  // @VisibleForTesting
  AndroidProgramRunner(@NotNull Function<@NotNull Project, @NotNull ExecutionTarget> getActiveTarget) {
    myGetActiveTarget = getActiveTarget;
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    if (profile instanceof RunConfiguration) {
      Project project = ((RunConfiguration)profile).getProject();
      ExecutionTarget target = myGetActiveTarget.apply(project);
      if (!(target instanceof AndroidExecutionTarget)) {
        return false;
      }
      if (((AndroidExecutionTarget)target).getAvailableDeviceCount() <= 1) {
        return true;
      }
      return canRunWithMultipleDevices(executorId);
    }
    return true;
  }

  protected abstract boolean canRunWithMultipleDevices(@NotNull String executorId);
}
