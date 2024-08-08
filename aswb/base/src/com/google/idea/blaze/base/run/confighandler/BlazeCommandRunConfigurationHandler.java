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
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import javax.annotation.Nullable;

/**
 * Supports the run configuration flow for {@link BlazeCommandRunConfiguration}s.
 *
 * <p>Provides rule-specific configuration state, validation, presentation, and runner.
 */
public interface BlazeCommandRunConfigurationHandler {
  RunConfigurationState getState();

  /** @return A {@link BlazeCommandRunConfigurationRunner} for running the configuration. */
  @Nullable
  BlazeCommandRunConfigurationRunner createRunner(
      Executor executor, ExecutionEnvironment environment) throws ExecutionException;

  /**
   * Checks whether the handler settings are valid.
   *
   * @throws RuntimeConfigurationException for configuration errors the user should be warned about.
   */
  void checkConfiguration() throws RuntimeConfigurationException;

  /**
   * @return The default name of the run configuration based on its settings and this handler's
   *     state.
   */
  @Nullable
  String suggestedName(BlazeCommandRunConfiguration configuration);

  /**
   * @return The {@link BlazeCommandName} associated with this state. May be null if no command is
   *     set.
   */
  @Nullable
  BlazeCommandName getCommandName();

  /** @return The name of this handler. Shown in the UI. */
  String getHandlerName();
}
