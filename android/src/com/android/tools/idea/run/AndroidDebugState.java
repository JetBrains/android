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

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RemoteState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidDebugState implements RemoteState {
  @NotNull private final Project myProject;
  @NotNull private final ProcessHandler myDebugProcessHandler;
  @NotNull private final RemoteConnection myConnection;
  @NotNull private final ConsoleProvider myConsoleProvider;

  public AndroidDebugState(@NotNull Project project,
                           @NotNull ProcessHandler processHandler,
                           @NotNull RemoteConnection connection,
                           @NotNull ConsoleProvider consoleProvider) {
    myProject = project;
    myDebugProcessHandler = processHandler;
    myConnection = connection;
    myConsoleProvider = consoleProvider;
  }

  @Override
  public RemoteConnection getRemoteConnection() {
    return myConnection;
  }

  @Nullable
  @Override
  public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    ConsoleView console = myConsoleProvider.createAndAttach(myProject, myDebugProcessHandler, executor);
    return new DefaultExecutionResult(console, myDebugProcessHandler);
  }
}
