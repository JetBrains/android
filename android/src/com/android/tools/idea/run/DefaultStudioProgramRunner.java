/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.run;

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * {@link com.intellij.execution.runners.ProgramRunner} for the default {@link com.intellij.execution.Executor}
 * {@link com.intellij.openapi.actionSystem.AnAction}, such as {@link DefaultRunExecutor} and {@link DefaultDebugExecutor}.
 */
public class DefaultStudioProgramRunner extends StudioProgramRunner {
  @Override
  @NotNull
  public String getRunnerId() {
    return "DefaultStudioProgramRunner";
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    if (!super.canRun(executorId, profile)) {
      return false;
    }
    if (!DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) && !DefaultRunExecutor.EXECUTOR_ID.equals(executorId)) {
      return false;
    }

    return profile instanceof AndroidRunConfigurationBase;
  }

  @Override
  protected boolean canRunWithMultipleDevices(@NotNull String executorId) {
    return DefaultRunExecutor.EXECUTOR_ID.equals(executorId);
  }
}
