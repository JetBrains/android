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
package com.android.tools.idea.run;

import com.android.ddmlib.Client;
import com.android.tools.idea.run.deployable.SwappableProcessHandler;
import com.android.tools.idea.run.deployment.AndroidExecutionTarget;
import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessListener;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link com.intellij.execution.process.ProcessHandler} associated with an Android app debug process.
 * When terminated, it stops the debug process and notifies all attached {@link DebugProcessListener}s of the termination.
 * It is also optionally terminated when the debug process detaches.
 * Like {@link AndroidProcessHandler}, it is destroyed when the user stops execution.
 * We use this instead of {@link com.intellij.debugger.engine.RemoteDebugProcessHandler} to retain
 * {@link AndroidProcessHandler}'s termination semantics when debugging Android processes.
 */
public class AndroidRemoteDebugProcessHandler extends ProcessHandler implements SwappableProcessHandler {

  private final Project myProject;

  public AndroidRemoteDebugProcessHandler(Project project) {
    myProject = project;

    putCopyableUserData(SwappableProcessHandler.EXTENSION_KEY, this);
  }

  // This is copied from com.intellij.debugger.engine.RemoteDebugProcessHandler#startNotify.
  @Override
  public void startNotify() {
    final DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this);
    final DebugProcessListener listener = new DebugProcessListener() {
      @Override
      public void processDetached(@NotNull DebugProcess process, boolean closedByUser) {
        debugProcess.removeDebugProcessListener(this);
        // Delay notifying process detached by 1 second to avoid race condition with ITestRunListener#testRunEnded.
        // If you debug android instrumentation test process, the test process may terminate before Ddmlib calls
        // testRunEnded callback. This results in "test framework quits unexpected" error. b/150001290.
        AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> notifyProcessDetached(), 1, TimeUnit.SECONDS);
      }
    };
    debugProcess.addDebugProcessListener(listener);
    try {
      super.startNotify();
    }
    finally {
      if (debugProcess.isDetached()) {
        debugProcess.removeDebugProcessListener(listener);
        notifyProcessDetached();
      }
    }
  }

  @Override
  protected void destroyProcessImpl() {
    DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this);
    if(debugProcess != null) {
      debugProcess.stop(true);
    }
    notifyProcessTerminated(0);
  }

  @Override
  protected void detachProcessImpl() {
    DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this);
    if(debugProcess != null) {
      debugProcess.stop(false);
    }
    notifyProcessDetached();
  }

  @Override
  public boolean detachIsDefault() {
    return false;
  }

  @Override
  public boolean isSilentlyDestroyOnClose() {
    return false;
  }

  @Override
  public OutputStream getProcessInput() {
    return null;
  }

  @Nullable
  @Override
  public Executor getExecutor() {
    AndroidSessionInfo sessionInfo = getUserData(AndroidSessionInfo.KEY);
    if (sessionInfo == null) {
      return null;
    }

    return sessionInfo.getExecutor();
  }

  @Override
  public boolean isRunningWith(@NotNull RunConfiguration runConfiguration, @NotNull ExecutionTarget executionTarget) {
    AndroidSessionInfo sessionInfo = getUserData(AndroidSessionInfo.KEY);
    if (sessionInfo == null) {
      return false;
    }

    if (sessionInfo.getRunConfiguration() != runConfiguration) {
      return false;
    }

    if (executionTarget instanceof AndroidExecutionTarget) {
      Client client = getUserData(AndroidSessionInfo.ANDROID_DEBUG_CLIENT);
      if (client == null || !client.isValid()) {
        return false;
      }

      Object device = client.getDevice();

      // The reference equality is intentional. We will only ever have a single IDevice instance for a device while it's connected. The
      // IntelliJ Platform expects a different IDevice when you disconnect and reconnect a device. If we used the equals method (based on
      // the serial number, say), a reconnected device would be the same and that would violate platform expectations.
      return ((AndroidExecutionTarget)executionTarget).getRunningDevices().stream().anyMatch(d -> d == device);
    }

    return sessionInfo.getExecutionTarget().getId().equals(executionTarget.getId());
  }
}
