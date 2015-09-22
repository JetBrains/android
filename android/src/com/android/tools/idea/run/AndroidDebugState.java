/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.ddmlib.IDevice;
import com.google.common.collect.ImmutableList;
import com.intellij.debugger.engine.RemoteDebugProcessHandler;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RemoteState;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class AndroidDebugState implements RemoteState, AndroidExecutionState {
  private final Project myProject;
  private final RemoteConnection myConnection;
  private final AndroidRunningState myState;
  private final IDevice myDevice;

  private volatile ConsoleView myConsoleView;

  public AndroidDebugState(Project project,
                           RemoteConnection connection,
                           AndroidRunningState state,
                           IDevice device) {
    myProject = project;
    myConnection = connection;
    myState = state;
    myDevice = device;
  }

  @Override
  public ExecutionResult execute(final Executor executor, @NotNull final ProgramRunner runner) throws ExecutionException {
    RemoteDebugProcessHandler process = new RemoteDebugProcessHandler(myProject);
    myState.setProcessHandler(process);
    myConsoleView = myState.getConfiguration().attachConsole(myState, executor);
    return new DefaultExecutionResult(myConsoleView, process);
  }

  @Override
  public RemoteConnection getRemoteConnection() {
    return myConnection;
  }

  @NotNull
  @Override
  public Collection<IDevice> getDevices() {
    return ImmutableList.of(myDevice);
  }

  @Nullable
  @Override
  public ConsoleView getConsoleView() {
    return myConsoleView;
  }

  @NotNull
  @Override
  public AndroidRunConfigurationBase getConfiguration() {
    return myState.getConfiguration();
  }
}
