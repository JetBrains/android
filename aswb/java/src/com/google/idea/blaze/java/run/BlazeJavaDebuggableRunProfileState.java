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
package com.google.idea.blaze.java.run;

import static com.google.common.base.Preconditions.checkState;

import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RemoteState;
import com.intellij.execution.runners.ExecutionEnvironment;

/**
 * A {@code RunProfileState} to go with {@link BlazeJavaRunConfigState}, providing debugging
 * support.
 */
public abstract class BlazeJavaDebuggableRunProfileState extends CommandLineState
    implements RemoteState {

  private static final String DEBUG_HOST_NAME = "localhost";

  private final BlazeCommandRunConfiguration cfg;
  private final ExecutorType executorType;

  protected BlazeJavaDebuggableRunProfileState(ExecutionEnvironment environment) {
    super(environment);
    this.cfg = BlazeCommandRunConfigurationRunner.getConfiguration(environment);
    this.executorType = ExecutorType.fromExecutor(environment.getExecutor());
  }

  protected BlazeCommandRunConfiguration getConfiguration() {
    return cfg;
  }

  protected ExecutorType getExecutorType() {
    return executorType;
  }

  @Override
  public RemoteConnection getRemoteConnection() {
    if (!executorType.isDebugType()) {
      return null;
    }
    BlazeJavaRunConfigState state = cfg.getHandlerStateIfType(BlazeJavaRunConfigState.class);
    checkState(state != null);
    return new RemoteConnection(
        /* useSockets= */ true,
        DEBUG_HOST_NAME,
        Integer.toString(state.getDebugPortState().port),
        /* serverMode= */ false);
  }
}
