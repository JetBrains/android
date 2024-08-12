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
package com.google.idea.blaze.java.run;

import com.intellij.debugger.DebugEnvironment;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DefaultDebugEnvironment;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebugProcessListener;
import com.intellij.debugger.impl.DebuggerManagerListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RemoteState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;

/**
 * A {@link DebuggerManagerListener} which ensures that the {@link DebugProcess} of a multi-run
 * {@link DebuggerSession} remains attached until the session terminates.
 *
 * <p>When a single Blaze invocation runs a test target multiple times (i.e. the runs_per_test flag
 * is set to something greater than 1), Blaze starts a new JVM process between test runs to ensure
 * that each run is hermetic. In such cases, MultiRunDebugSessionListener will detect when the old
 * JVM process has been detached and notify the DebugProcess about the new JVM process. Otherwise,
 * the debug session would stall between test runs.
 *
 * <p>The listener applies only to the next {@link DebuggerSession} to be created after {@link
 * MultiRunDebuggerSessionListener#startListening()} is called and remains active until either
 * {@link MultiRunDebuggerSessionListener#stopListening()} is called or the {@link DebuggerSession}
 * has ended (i.e. the process corresponding to the `blaze test` invocation stops).
 */
public class MultiRunDebuggerSessionListener implements DebuggerManagerListener {
  private ReattachJvmProcessListener reattachingListener;
  private final ExecutionEnvironment executionEnvironment;
  private final DebugEnvironment debugEnvironment;
  private final DebuggerManagerEx debuggerManager;

  public MultiRunDebuggerSessionListener(
      ExecutionEnvironment executionEnvironment, RemoteState state) {
    this.executionEnvironment = executionEnvironment;
    this.debuggerManager = DebuggerManagerEx.getInstanceEx(executionEnvironment.getProject());
    RemoteConnection remoteConnection = state.getRemoteConnection();
    debugEnvironment =
        new DefaultDebugEnvironment(executionEnvironment, state, remoteConnection, true);
  }

  public void startListening() {
    debuggerManager.addDebuggerManagerListener(this);
  }

  public void stopListening() {
    debuggerManager.removeDebuggerManagerListener(this);
  }

  @Override
  public void sessionCreated(DebuggerSession session) {
    if (reattachingListener != null) {
      return;
    }
    reattachingListener = new ReattachJvmProcessListener(debugEnvironment, executionEnvironment);
    session.getProcess().addDebugProcessListener(reattachingListener);
  }

  @Override
  public void sessionRemoved(DebuggerSession session) {
    if (reattachingListener != null) {
      session.getProcess().removeDebugProcessListener(reattachingListener);
      reattachingListener = null;
    }
    stopListening();
  }

  private static class ReattachJvmProcessListener implements DebugProcessListener {
    final DebugEnvironment debugEnvironment;
    final ExecutionEnvironment executionEnvironment;

    public ReattachJvmProcessListener(
        DebugEnvironment debugEnvironment, ExecutionEnvironment executionEnvironment) {
      this.debugEnvironment = debugEnvironment;
      this.executionEnvironment = executionEnvironment;
    }

    @Override
    public void processDetached(DebugProcess process, boolean closedByUser) {
      if (closedByUser) {
        return;
      }
      try {
        reattach((DebugProcessImpl) process, debugEnvironment);
      } catch (ExecutionException e) {
        ExecutionUtil.handleExecutionError(
            executionEnvironment.getProject(),
            executionEnvironment.getExecutor().getToolWindowId(),
            executionEnvironment.getRunProfile(),
            e);
      }
    }

    private static void reattach(DebugProcessImpl process, DebugEnvironment debugEnvironment)
        throws ExecutionException {
      // Reattaching the debug process happens in a new worker thread request.
      // Stop the existing request first to avoid an IllegalStateException in
      // InvokeThread#run.
      process.getManagerThread().getCurrentRequest().requestStop();
      process.reattach(debugEnvironment);
    }
  }
}
