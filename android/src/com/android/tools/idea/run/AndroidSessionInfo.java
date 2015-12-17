/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import org.jetbrains.annotations.NotNull;

public class AndroidSessionInfo {
  @NotNull private final ProcessHandler myProcessHandler;
  private final RunContentDescriptor myDescriptor;
  private final AndroidExecutionState myState;
  private final String myExecutorId;

  public AndroidSessionInfo(@NotNull ProcessHandler processHandler,
                            @NotNull RunContentDescriptor descriptor,
                            @NotNull AndroidExecutionState state,
                            @NotNull String executorId) {
    myProcessHandler = processHandler;
    myDescriptor = descriptor;
    myState = state;
    myExecutorId = executorId;
  }

  @NotNull
  public ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  @NotNull
  public RunContentDescriptor getDescriptor() {
    return myDescriptor;
  }

  @NotNull
  public AndroidExecutionState getState() {
    return myState;
  }

  @NotNull
  public String getExecutorId() {
    return myExecutorId;
  }

  public boolean isEmbeddable() {
    // TODO: Maybe also check that descriptor process handler is still running?
    return myExecutorId == DefaultDebugExecutor.EXECUTOR_ID && // We only embed to debug sessions.
           myState.getConsoleView() != null &&
           myState.getDevices().size() == 1;
  }
}
