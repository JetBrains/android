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
package com.google.idea.blaze.java.run;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import javax.annotation.Nullable;

/** A runner that adapts the GenericDebuggerRunner to work with Blaze run configurations. */
public class BlazeJavaDebuggerRunner extends GenericDebuggerRunner {

  // wait 10 minutes for the blaze build to complete before connecting
  private static final long POLL_TIMEOUT_MILLIS = 10 * 60 * 1000;

  @Override
  public String getRunnerId() {
    return "Blaze-Debug";
  }

  @Override
  public boolean canRun(final String executorId, final RunProfile profile) {
    if (!executorId.equals(DefaultDebugExecutor.EXECUTOR_ID)
        || !(profile instanceof BlazeCommandRunConfiguration)) {
      return false;
    }
    BlazeCommandRunConfiguration configuration = (BlazeCommandRunConfiguration) profile;
    Kind kind = configuration.getTargetKind();
    if (kind == null || !BlazeJavaRunConfigurationHandlerProvider.supportsKind(kind)) {
      return false;
    }
    return canDebug(configuration.getHandler().getCommandName());
  }

  protected boolean canDebug(@Nullable BlazeCommandName command) {
    return BlazeCommandName.TEST.equals(command) || BlazeCommandName.RUN.equals(command);
  }

  @Override
  public void patch(
      JavaParameters javaParameters,
      RunnerSettings runnerSettings,
      RunProfile runProfile,
      final boolean beforeExecution) {
    // We don't want to support Java run configuration patching.
  }

  @Override
  @Nullable
  public RunContentDescriptor createContentDescriptor(
      RunProfileState state, ExecutionEnvironment environment) throws ExecutionException {
    if (!(state instanceof BlazeJavaDebuggableRunProfileState)) {
      return null;
    }
    BlazeJavaDebuggableRunProfileState blazeState = (BlazeJavaDebuggableRunProfileState) state;
    RemoteConnection connection = blazeState.getRemoteConnection();
    return attachVirtualMachine(state, environment, connection, POLL_TIMEOUT_MILLIS);
  }

  @Override
  protected RunContentDescriptor doExecute(RunProfileState state, ExecutionEnvironment env)
      throws ExecutionException {
    EventLoggingService.getInstance().logEvent(getClass(), "debugging-java");
    return super.doExecute(state, env);
  }
}
