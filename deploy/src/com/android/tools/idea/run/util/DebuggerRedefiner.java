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

import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.ClassRedefiner;
import com.android.tools.deployer.JdiBasedClassRedefiner;
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
import com.sun.jdi.VirtualMachine;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import javax.swing.SwingUtilities;

/**
 * Helper class for the deploy task to deal with the interactions with the IntelliJ debugger.
 *
 * In particular, all the debugger interaction within IntelliJ happens in the main thread. This class takes care of queuing up debugger
 * task in that thread.
 */
public class DebuggerRedefiner implements ClassRedefiner {

  private final Project project;

  public DebuggerRedefiner(Project project) {
    this.project = project;
  }

  @Override
  public Deploy.SwapResponse redefine(Deploy.SwapRequest request) {
    MultiProcessCommand commands = new MultiProcessCommand();
    Collection<DebuggerSession> debuggerSessions = DebuggerManagerEx.getInstanceEx(project).getSessions();
    List<DebuggerTask> tasks = new ArrayList<>(debuggerSessions.size());

    if (debuggerSessions.isEmpty()) {
      return Deploy.SwapResponse.newBuilder().setStatus(Deploy.SwapResponse.Status.ERROR).build();
    }

    for (DebuggerSession debuggerSession : debuggerSessions) {
      DebuggerCommandImpl command = new DebuggerCommandImpl() {
        @Override
        protected void action() {
          redefine(project, debuggerSession, request);
        }

        @Override
        protected void commandCancelled() {
          debuggerSession.setModifiedClassesScanRequired(true);
        }
      };
      commands.addCommand(debuggerSession.getProcess(), command);
      tasks.add(command);
    }
    commands.run();
    tasks.forEach(cmd -> cmd.waitFor());

    return Deploy.SwapResponse.newBuilder().setStatus(Deploy.SwapResponse.Status.OK).build();
  }

  private static void redefine(Project project, DebuggerSession session, Deploy.SwapRequest request) {
    disableBreakPoints(project, session);
    VirtualMachine vm = session.getProcess().getVirtualMachineProxy().getVirtualMachine();
    new JdiBasedClassRedefiner(vm).redefine(request);
    enableBreakPoints(project, session);
  }

  /**
   * @return True true if there is at least one debugger attached to a given project.
   */
  public static boolean hasDebuggersAttached(Project project) {
    return !DebuggerManagerEx.getInstanceEx(project).getSessions().isEmpty();
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
}
