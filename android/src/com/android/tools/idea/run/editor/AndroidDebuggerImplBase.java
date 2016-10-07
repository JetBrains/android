/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.run.editor;

import com.android.annotations.concurrency.GuardedBy;
import com.android.ddmlib.Client;
import com.android.sdklib.AndroidVersion;
import com.google.common.collect.Sets;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public abstract class AndroidDebuggerImplBase<S extends AndroidDebuggerState> implements AndroidDebugger<S> {
  @GuardedBy("this")
  private Set<XBreakpointType<?, ?>> mySupportedBreakpointTypes;
  private final Set<Class<? extends XBreakpointType<?, ?>>> breakpointTypeClasses;

  protected AndroidDebuggerImplBase(Set<Class<? extends XBreakpointType<?, ?>>> breakpointTypeClasses) {
    this.breakpointTypeClasses = breakpointTypeClasses;
  }

  @NotNull
  protected static String getClientDebugPort(@NotNull Client client) {
    return Integer.toString(client.getDebuggerListenPort()).trim();
  }

  @Nullable
  protected static DebuggerSession findJdwpDebuggerSession(@NotNull Project project, @NotNull String debugPort) {
    for (DebuggerSession session : DebuggerManagerEx.getInstanceEx(project).getSessions()) {
      if (debugPort.equals(session.getProcess().getConnection().getAddress().trim())) {
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

    if (processHandler.isProcessTerminated()) {
      ExecutionManager.getInstance(project).getContentManager().removeRunContent(executor, descriptor);
      return false;
    }
    content.getManager().setSelectedContent(content);
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(executor.getToolWindowId());
    window.activate(null, false, true);
    return true;
  }

  @Override
  @NotNull
  public synchronized Set<XBreakpointType<?, ?>> getSupportedBreakpointTypes(@NotNull Project project, @NotNull AndroidVersion version) {
    if (mySupportedBreakpointTypes == null) {
      XDebuggerUtil debuggerUtil = XDebuggerUtil.getInstance();
      mySupportedBreakpointTypes = Sets.newHashSet();
      for (Class bpTypeCls : breakpointTypeClasses) {
        mySupportedBreakpointTypes.add(debuggerUtil.findBreakpointType(bpTypeCls));
      }
    }
    return mySupportedBreakpointTypes;
  }

  @Override
  public boolean shouldBeDefault() {
    return false;
  }

  @Override
  @NotNull
  public String getAmStartOptions(@NotNull S state, @NotNull Project project, @NotNull AndroidVersion version) {

    return "";
  }
}
