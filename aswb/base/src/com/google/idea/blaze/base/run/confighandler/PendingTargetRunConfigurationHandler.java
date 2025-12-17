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
package com.google.idea.blaze.base.run.confighandler;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.application.ApplicationManager;
import javax.annotation.Nullable;

public class PendingTargetRunConfigurationHandler implements BlazeCommandRunConfigurationHandler {

  private final BuildSystemName buildSystemName;
  private final BlazeCommandRunConfigurationCommonState state;

  PendingTargetRunConfigurationHandler(BlazeCommandRunConfiguration config) {
    this.buildSystemName = Blaze.getBuildSystemName(config.getProject());
    this.state = new BlazeCommandRunConfigurationCommonState(buildSystemName);
  }

  @Override
  public RunConfigurationState getState() {
    return state;
  }

  @Nullable
  @Override
  public BlazeCommandRunConfigurationRunner createRunner(
      Executor executor, ExecutionEnvironment environment) {
    return null;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    state.validate(buildSystemName);
  }

  @Nullable
  @Override
  public String suggestedName(BlazeCommandRunConfiguration configuration) {
    return null;
  }

  @Nullable
  @Override
  public BlazeCommandName getCommandName() {
    return state.getCommandState().getCommand();
  }

  @Override
  public String getHandlerName() {
    return "Pending target handler";
  }
}
