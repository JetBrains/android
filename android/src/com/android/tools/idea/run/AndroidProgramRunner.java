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

import com.android.tools.idea.execution.common.AndroidExecutionTarget;
import com.intellij.execution.ExecutionTargetManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.openapi.project.Project;
import java.util.function.BiFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A common base class for ProgramRunners that deal with local Android devices.
 * Note that the generics parameter to {@link GenericProgramRunner} isn't used
 * at the moment, and may be changed as needed.
 */
public abstract class AndroidProgramRunner extends GenericProgramRunner<RunnerSettings> {
  final private @NotNull BiFunction<@NotNull Project, @NotNull RunConfiguration, @Nullable AndroidExecutionTarget> myGetAndroidTarget;

  public AndroidProgramRunner() {
    this(AndroidProgramRunner::getAvailableAndroidTarget);
  }

  // @VisibleForTesting
  AndroidProgramRunner(@NotNull BiFunction<@NotNull Project, @NotNull RunConfiguration, @Nullable AndroidExecutionTarget> getTarget) {
    myGetAndroidTarget = getTarget;
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    if (profile instanceof RunConfiguration) {
      Project project = ((RunConfiguration)profile).getProject();
      AndroidExecutionTarget target = myGetAndroidTarget.apply(project, (RunConfiguration)profile);
      if (target == null) {
        return false;
      }
      if (target.getAvailableDeviceCount() <= 1) {
        return true;
      }
      return canRunWithMultipleDevices(executorId);
    }
    return false;
  }

  @Nullable
  private static AndroidExecutionTarget getAvailableAndroidTarget(Project project, RunConfiguration profile) {
    return ExecutionTargetManager.getInstance(project).getTargetsFor(profile).stream()
      .filter(AndroidExecutionTarget.class::isInstance)
      .map(AndroidExecutionTarget.class::cast)
      .findFirst()
      .orElse(null);
  }

  protected abstract boolean canRunWithMultipleDevices(@NotNull String executorId);
}
