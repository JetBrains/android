/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.test;

import com.android.tools.idea.execution.common.AndroidConfigurationExecutor;
import com.android.tools.idea.execution.common.AndroidConfigurationProgramRunner;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationHandler;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Program runner for configurations from {@link BlazeAndroidTestRunConfigurationHandler}. */
public class BlazeAndroidTestProgramRunner extends AndroidConfigurationProgramRunner {
  @Override
  public boolean canRun(String executorId, RunProfile profile) {
    BlazeAndroidRunConfigurationHandler handler =
        BlazeAndroidRunConfigurationHandler.getHandlerFrom(profile);
    if (!(handler instanceof BlazeAndroidTestRunConfigurationHandler)) {
      return false;
    }
    return super.canRun(executorId, profile) && DefaultRunExecutor.EXECUTOR_ID.equals(executorId)
        || DefaultDebugExecutor.EXECUTOR_ID.equals(executorId);
  }

  @Override
  public String getRunnerId() {
    return "AndroidTestProgramRunner";
  }

  @Override
  protected boolean canRunWithMultipleDevices(@NotNull String executorId) {
    return true;
  }

  @NotNull
  @Override
  protected List<String> getSupportedConfigurationTypeIds() {
    return Collections.singletonList(BlazeCommandRunConfigurationType.getInstance().getId());
  }

  @NotNull
  @Override
  protected RunContentDescriptor run(
      @NotNull ExecutionEnvironment environment,
      @NotNull AndroidConfigurationExecutor executor,
      @NotNull ProgressIndicator indicator)
      throws ExecutionException {
    if (DefaultDebugExecutor.EXECUTOR_ID.equals(environment.getExecutor().getId())) {
      return executor.debug(indicator);
    }
    if (DefaultRunExecutor.EXECUTOR_ID.equals(environment.getExecutor().getId())) {
      return executor.run(indicator);
    }
    throw new RuntimeException("Unsupported executor");
  }
}
