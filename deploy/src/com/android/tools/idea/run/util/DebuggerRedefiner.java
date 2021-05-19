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
import com.android.tools.deployer.DeployerException;
import com.android.tools.deployer.JdiBasedClassRedefiner;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.JavaExecutionStack;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.MultiProcessCommand;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.breakpoints.StackCapturingLineBreakpoint;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.VirtualMachine;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

/**
 * Helper class for the deploy task to deal with the interactions with the IntelliJ debugger.
 *
 * In particular, all the debugger interaction within IntelliJ happens in the main thread. This class takes care of queuing up debugger
 * task in that thread.
 */
public class DebuggerRedefiner implements ClassRedefiner {

  private final Project project;

  // This is the port that the IntelliJ talks to (port opended by ddmlib).
  private final int debuggerPort;
  private final boolean fallback;

  private RedefineClassSupportState supportState = null;

  public DebuggerRedefiner(Project project, int debuggerPort, boolean fallback) {
    this.project = project;
    this.debuggerPort = debuggerPort;
    this.fallback = fallback;
  }

  @Override
  public RedefineClassSupportState canRedefineClass() throws DeployerException{
    if (supportState != null) {
      return supportState;
    }
    MultiProcessCommand commands = new MultiProcessCommand();
    DebuggerSession debuggerSession = getDebuggerSession(project, debuggerPort);
    if (debuggerSession == null) {
      throw DeployerException.noDebuggerSession(debuggerPort);
    }
    final AtomicReference<RedefineClassSupportState> result = new AtomicReference<>();
    DebuggerCommandImpl task = new DebuggerCommandImpl() {
          @Override
          protected void action() {
            result.set(canRedefineClassInternal(debuggerSession));
          }
        };
    commands.addCommand(debuggerSession.getProcess(), task);
    commands.run();
    task.waitFor();
    supportState = result.get();
    return supportState;
  }

  private RedefineClassSupportState canRedefineClassInternal(DebuggerSession debuggerSession) {
    // We use the IntelliJ abstraction of the debugger here since it is available.
    DebuggerManagerThreadImpl.assertIsManagerThread();
    DebugProcessImpl debugProcess = debuggerSession.getProcess();
    VirtualMachineProxyImpl virtualMachineProxy = debugProcess.getVirtualMachineProxy();

    // Simple case, debugger has the capability to all is good.
    if (virtualMachineProxy.canRedefineClasses()) {
      return new RedefineClassSupportState(RedefineClassSupport.FULL, null);
    }

    Collection<ThreadReferenceProxyImpl> allThreads = virtualMachineProxy.allThreads();

    // Prioritize on MAIN_THREAD_RUNNING since this is the safest option.
    for (ThreadReferenceProxyImpl thread : allThreads) {
      if (thread.name().equals("main")) {
        if (!thread.isSuspended()) {
          return new RedefineClassSupportState(RedefineClassSupport.MAIN_THREAD_RUNNING, "main");
        }
      }
    }

    // Just hope for a thread on a breakpoint after.
    for (ThreadReferenceProxyImpl thread : allThreads) {
      if (thread.isAtBreakpoint()) {
        RedefineClassSupportState state = new RedefineClassSupportState(RedefineClassSupport.NEEDS_AGENT_SERVER, thread.name());
        return state;
      }
    }

    return new RedefineClassSupportState(RedefineClassSupport.NONE, null);
  }

  @Override
  public Deploy.SwapResponse redefine(Deploy.SwapRequest request) throws DeployerException {
    MultiProcessCommand commands = new MultiProcessCommand();
    DebuggerSession debuggerSession = getDebuggerSession(project, debuggerPort);
    if (debuggerSession == null) {
      throw DeployerException.noDebuggerSession(debuggerPort);
    }

    // A bit of a hack. Exceptions posted to background tasks ends up on the log only. We are going to gather
    // as much of these as possible and present it to the user.
    final AtomicReference<DeployerException> exception = new AtomicReference<>();
    DebuggerCommandImpl task = new DebuggerCommandImpl() {
      @Override
      protected void action() {
        try {
          redefine(project, debuggerSession, request);
        }
        catch (DeployerException e) {
          exception.set(e);
        }
      }

      @Override
      protected void commandCancelled() {
        debuggerSession.setModifiedClassesScanRequired(true);
      }
    };
    commands.addCommand(debuggerSession.getProcess(), task);
    commands.run();
    task.waitFor();

    if (exception.get() != null) {
      DeployerException e = exception.get();
      if (fallback && e.getError() == DeployerException.Error.JDWP_REDEFINE_CLASSES_EXCEPTION) {
        return Deploy.SwapResponse.newBuilder().setStatus(Deploy.SwapResponse.Status.SWAP_FAILED_BUT_OVERLAY_UPDATED).build();
      }
      throw exception.get();
    }

    return Deploy.SwapResponse.newBuilder().setStatus(Deploy.SwapResponse.Status.OK).build();
  }

  private void redefine(Project project, DebuggerSession session, Deploy.SwapRequest request) throws DeployerException {
    try {
      disableBreakPoints(project, session);
      VirtualMachine vm = session.getProcess().getVirtualMachineProxy().getVirtualMachine();
      new JdiBasedClassRedefiner(vm, canRedefineClass()).redefine(request);
    } finally {
      enableBreakPoints(project, session);
    }
  }

  @Override
  public Deploy.SwapResponse redefine(Deploy.OverlaySwapRequest request) throws DeployerException {
    MultiProcessCommand commands = new MultiProcessCommand();
    DebuggerSession debuggerSession = getDebuggerSession(project, debuggerPort);
    if (debuggerSession == null) {
      throw DeployerException.noDebuggerSession(debuggerPort);
    }

    // A bit of a hack. Exceptions posted to background tasks ends up on the log only. We are going to gather
    // as much of these as possible and present it to the user.
    final AtomicReference<DeployerException> exception = new AtomicReference<>();
    DebuggerCommandImpl task = new DebuggerCommandImpl() {
      @Override
      protected void action() {
        try {
          redefine(project, debuggerSession, request);
        } catch (DeployerException e) {
          exception.set(e);
        }
      }

      @Override
      protected void commandCancelled() {
        debuggerSession.setModifiedClassesScanRequired(true);
      }
    };
    commands.addCommand(debuggerSession.getProcess(), task);
    commands.run();
    task.waitFor();

    if (exception.get() != null) {
      if (fallback) {
        return Deploy.SwapResponse.newBuilder().setStatus(Deploy.SwapResponse.Status.SWAP_FAILED_BUT_OVERLAY_UPDATED).build();
      }
      else {
        throw exception.get();
      }
    }

    return Deploy.SwapResponse.newBuilder().setStatus(Deploy.SwapResponse.Status.OK).build();
  }

  private void redefine(Project project, DebuggerSession session, Deploy.OverlaySwapRequest request) throws DeployerException {
    try {
      disableBreakPoints(project, session);
      VirtualMachine vm = session.getProcess().getVirtualMachineProxy().getVirtualMachine();
      RedefineClassSupportState state = new RedefineClassSupportState(RedefineClassSupport.FULL, null);
      new JdiBasedClassRedefiner(vm, state).redefine(request);
    } finally {
      enableBreakPoints(project, session);
    }
  }

  /**
   * Give a project return the DebugggerSession object associated with it at the port should a debugger is connected there. Otherwise,
   * return null.
   */
  public static DebuggerSession getDebuggerSession(Project project, int port) {
    Collection<DebuggerSession> debuggerSessions = DebuggerManagerEx.getInstanceEx(project).getSessions();
    for (DebuggerSession debuggerSession : debuggerSessions) {
      RemoteConnection s = debuggerSession.getProcess().getConnection();
      String address = s.getAddress();
      int projectDebuggerPort = Integer.parseInt(address);
      if (port == projectDebuggerPort) {
        return debuggerSession;
      }
    }
    return null;
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
    ApplicationManager.getApplication().invokeLater(() -> {
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
