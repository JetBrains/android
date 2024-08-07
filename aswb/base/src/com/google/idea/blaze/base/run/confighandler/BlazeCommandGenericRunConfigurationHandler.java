/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import javax.annotation.Nullable;

/**
 * Generic handler for {@link BlazeCommandRunConfiguration}s, used as a fallback in the case where
 * no other handlers are more relevant.
 */
public final class BlazeCommandGenericRunConfigurationHandler
    implements BlazeCommandRunConfigurationHandler {

  private final BuildSystemName buildSystemName;
  private final BlazeCommandRunConfigurationCommonState state;

  public BlazeCommandGenericRunConfigurationHandler(BlazeCommandRunConfiguration configuration) {
    this.buildSystemName = Blaze.getBuildSystemName(configuration.getProject());
    this.state = new BlazeCommandRunConfigurationCommonState(buildSystemName);
  }

  @Override
  public BlazeCommandRunConfigurationCommonState getState() {
    return state;
  }

  @Override
  public BlazeCommandRunConfigurationRunner createRunner(
      Executor executor, ExecutionEnvironment environment) {
    return new BlazeCommandGenericRunConfigurationRunner();
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    state.validate(buildSystemName);
  }

  @Override
  @Nullable
  public String suggestedName(BlazeCommandRunConfiguration configuration) {
    if (configuration.getTargets().isEmpty()) {
      return null;
    }
    return new BlazeConfigurationNameBuilder(configuration).build();
  }

  @Override
  @Nullable
  public BlazeCommandName getCommandName() {
    return state.getCommandState().getCommand();
  }

  @Override
  public String getHandlerName() {
    return "Generic Handler";
  }
}
