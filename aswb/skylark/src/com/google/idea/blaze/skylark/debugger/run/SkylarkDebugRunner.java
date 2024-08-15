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
package com.google.idea.blaze.skylark.debugger.run;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandGenericRunConfigurationRunner.BlazeCommandRunProfileState;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.skylark.debugger.SkylarkDebuggingUtils;
import com.google.idea.blaze.skylark.debugger.impl.SkylarkDebugProcess;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import javax.annotation.Nullable;

/** Activates the 'debug' button, and delegates debugging to {@link SkylarkDebugProcess}. */
class SkylarkDebugRunner extends GenericProgramRunner<RunnerSettings> {

  private static final Logger logger = Logger.getInstance(SkylarkDebugRunner.class);

  private static final String RUNNER_ID = "SkylarkDebugRunner";

  @Override
  public String getRunnerId() {
    return RUNNER_ID;
  }

  @Override
  public boolean canRun(String executorId, RunProfile profile) {
    if (!DefaultDebugExecutor.EXECUTOR_ID.equals(executorId)) {
      return false;
    }
    BlazeCommandRunConfiguration config =
        BlazeCommandRunConfigurationRunner.getBlazeConfig(profile);
    if (config == null || !SkylarkDebuggingUtils.debuggingEnabled(config.getProject())) {
      return false;
    }
    BlazeCommandRunConfigurationCommonState handlerState =
        config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    BlazeCommandName command =
        handlerState != null ? handlerState.getCommandState().getCommand() : null;
    return BlazeCommandName.BUILD.equals(command);
  }

  @Nullable
  @Override
  protected RunContentDescriptor doExecute(RunProfileState state, ExecutionEnvironment env)
      throws ExecutionException {
    if (!DefaultDebugExecutor.EXECUTOR_ID.equals(env.getExecutor().getId())) {
      logger.error("Unexpected executor id: " + env.getExecutor().getId());
      return null;
    }
    BlazeCommandRunConfiguration config =
        BlazeCommandRunConfigurationRunner.getBlazeConfig(env.getRunProfile());
    if (config == null || !(state instanceof BlazeCommandRunProfileState)) {
      throw new ExecutionException("Debugging is not supported for this blaze run configuration");
    }

    ExecutionResult result = state.execute(env.getExecutor(), this);

    FileDocumentManager.getInstance().saveAllDocuments();
    XDebuggerManager manager = XDebuggerManager.getInstance(env.getProject());
    XDebugSession session =
        manager.startSession(
            env,
            new XDebugProcessStarter() {
              @Override
              public XDebugProcess start(XDebugSession session) {
                EventLoggingService.getInstance().logEvent(getClass(), "skylark-debugging");
                return new SkylarkDebugProcess(
                    session, result, SkylarkDebugBuildFlagsProvider.SERVER_PORT);
              }
            });

    return session.getRunContentDescriptor();
  }
}
