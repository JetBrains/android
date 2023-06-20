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
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AndroidDebuggerImplBase<S extends AndroidDebuggerState> implements AndroidDebugger<S> {

  @NotNull
  protected static String getClientDebugPort(@NotNull Client client) {
    return Integer.toString(client.getDebuggerListenPort()).trim();
  }

  @Nullable
  protected static DebuggerSession findJdwpDebuggerSession(@NotNull Project project, @NotNull String debugPort) {
    for (DebuggerSession session : DebuggerManagerEx.getInstanceEx(project).getSessions()) {
      if (debugPort.equals(session.getProcess().getConnection().getDebuggerAddress().trim())) {
        return session;
      }
    }
    return null;
  }

  protected static boolean activateDebugSessionWindow(@NotNull Project project, @NotNull RunContentDescriptor descriptor) {
    final ProcessHandler processHandler = descriptor.getProcessHandler();
    final Content content = descriptor.getAttachedContent();

    if (processHandler == null || content == null) {
      return false;
    }

    final Executor executor = DefaultDebugExecutor.getDebugExecutorInstance();

    boolean processTerminated = processHandler.isProcessTerminated();
    ApplicationManager.getApplication().invokeLater(() -> {
      if (processTerminated) {
        RunContentManager.getInstance(project).removeRunContent(executor, descriptor);
      } else {
        // Switch to the debug tab associated with the existing debug session, and open the debug tool window.
        content.getManager().setSelectedContent(content);
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(executor.getToolWindowId());
        window.activate(null, false, true);
      }
    });
    return !processTerminated;
  }

  @Override
  public boolean shouldBeDefault() {
    return false;
  }
}
