/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.util;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.JavaExecutionStack;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerTask;
import com.intellij.debugger.impl.MultiProcessCommand;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.breakpoints.StackCapturingLineBreakpoint;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.xdebugger.XDebugSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;
import javax.swing.SwingUtilities;

/**
 * Helper class for the deploy task to deal with the interactions with the IntelliJ debugger.
 *
 * In particular, all the debugger interaction within IntelliJ happens in the main thread. This class takes care of queuing up debugger
 * task in that thread.
 */
public class DebuggerHelper {

  /**
   * @return True true if there is at least one debugger attached to a given project.
   */
  public static boolean hasDebuggersAttached(Project project) {
    return !DebuggerManagerEx.getInstanceEx(project).getSessions().isEmpty();
  }

  /**
   * Asynchronously disables all breakpoints in a given project.
   *
   * @return A list of DebuggerTasks that this method started. If there is no debugger session attached to a project, this list will be
   * empty.
   */
  public static List<DebuggerTask> disableBreakPoints(Project project) {
    return startDebuggerTasksOnProject(project, DebuggerHelper::disableBreakPoints);
  }

  private static void disableBreakPoints(Project project, DebuggerSession debuggerSession) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    DebugProcessImpl debugProcess = debuggerSession.getProcess();
    BreakpointManager breakpointManager = (DebuggerManagerEx.getInstanceEx(project)).getBreakpointManager();
    breakpointManager.disableBreakpoints(debugProcess);
    StackCapturingLineBreakpoint.deleteAll(debugProcess);
    VirtualMachineProxyImpl virtualMachineProxy = debugProcess.getVirtualMachineProxy();

    if (Registry.is("debugger.resume.yourkit.threads")) {
      virtualMachineProxy.allThreads().stream()
                         .filter(ThreadReferenceProxyImpl::isResumeOnHotSwap)
                         .filter(ThreadReferenceProxyImpl::isSuspended)
                         .forEach(t -> IntStream.range(0, t.getSuspendCount()).forEach(i -> t.resume()));
    }
  }

  /**
   * Asynchronously re-enables all breakpoints in a given project. This should be called after a code swap.
   *
   * @return A list of DebuggerTasks that this method started. If there is no debugger session attached to a project, this list will be
   * empty.
   */
  public static List<DebuggerTask> enableBreakPoints(Project project) {
    return startDebuggerTasksOnProject(project, DebuggerHelper::enableBreakPoints);
  }

  private static void enableBreakPoints(Project project, DebuggerSession debuggerSession) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    DebugProcessImpl debugProcess = debuggerSession.getProcess();
    BreakpointManager breakpointManager = (DebuggerManagerEx.getInstanceEx(project)).getBreakpointManager();

    debugProcess.onHotSwapFinished();

    DebuggerContextImpl context = debuggerSession.getContextManager().getContext();
    SuspendContextImpl suspendContext = context.getSuspendContext();
    if (suspendContext != null) {
      JavaExecutionStack stack = suspendContext.getActiveExecutionStack();
      if (stack != null) {
        stack.initTopFrame();
      }
    }

    final Semaphore waitSemaphore = new Semaphore();
    waitSemaphore.down();
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> {
      try {
        if (!project.isDisposed()) {
          breakpointManager.reloadBreakpoints();
          debugProcess.getRequestsManager().clearWarnings();
          debuggerSession.refresh(false);

          XDebugSession session = debuggerSession.getXDebugSession();
          if (session != null) {
            session.rebuildViews();
          }
        }
      }
      finally {
        waitSemaphore.up();
      }
    });

    waitSemaphore.waitFor();

    if (!project.isDisposed()) {
      breakpointManager.enableBreakpoints(debugProcess);
      StackCapturingLineBreakpoint.createAll(debugProcess);
    }
  }

  /**
   * Starts executing a lambda on each debugger session within a project asynchroniously.
   *
   * @return A list of debugger tasks that are started.
   */
  public static List<DebuggerTask> startDebuggerTasksOnProject(Project project, BiConsumer<Project, DebuggerSession> task) {
    MultiProcessCommand commands = new MultiProcessCommand();
    Collection<DebuggerSession> debuggerSessions = DebuggerManagerEx.getInstanceEx(project).getSessions();

    if (debuggerSessions.isEmpty()) {
      return new ArrayList<>();
    }

    List<DebuggerTask> result = new ArrayList<>(debuggerSessions.size());

    for (DebuggerSession debuggerSession : debuggerSessions) {
      DebuggerCommandImpl command = new DebuggerCommandImpl() {
        @Override
        protected void action() throws IOException {
          task.accept(project, debuggerSession);
        }

        @Override
        protected void commandCancelled() {
          debuggerSession.setModifiedClassesScanRequired(true);
        }

      };
      commands.addCommand(debuggerSession.getProcess(), command);
      result.add(command);
    }
    commands.run();
    return result;
  }

  /**
   * Block for all the DebuggerTasks. This method returns immediately should the list be empty.
   */
  public static void waitFor(List<DebuggerTask> tasks) {
    tasks.forEach(cmd -> cmd.waitFor());
  }
}
