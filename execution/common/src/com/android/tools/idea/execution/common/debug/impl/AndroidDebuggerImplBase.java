/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.execution.common.debug.impl;

import com.android.ddmlib.Client;
import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AndroidDebuggerImplBase<S extends AndroidDebuggerState> implements AndroidDebugger<S> {

  @NotNull
  protected static String getClientDebugPort(@NotNull Client client) {
    return Integer.toString(client.getDebuggerListenPort()).trim();
  }

  @Nullable
  protected static XDebugSession findJdwpDebuggerSession(@NotNull Project project, @NotNull Client client) {
    String debugPort = getClientDebugPort(client);
    for (DebuggerSession session : DebuggerManagerEx.getInstanceEx(project).getSessions()) {
      if (debugPort.equals(session.getProcess().getConnection().getDebuggerAddress().trim())) {
        return session.getXDebugSession();
      }
    }
    return null;
  }


  @Override
  public boolean shouldBeDefault() {
    return false;
  }
}
