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
package com.android.tools.idea.execution.common.processhandler;


import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessListener;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.io.OutputStream;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link com.intellij.execution.process.ProcessHandler} associated with an Android app debug process for specific CLient.
 * <p>
 * When terminated, it stops the debug process and notifies all attached {@link DebugProcessListener}s of the termination.
 * We use this instead of {@link com.intellij.debugger.engine.RemoteDebugProcessHandler} to gracefully terminate Android process (app)
 * if needed by passing finishAndroidProcess param. Default implementations calls `am force-stop`.
 * See {@link AndroidRemoteDebugProcessHandler#destroyProcessImpl()}
 * <p>
 * Additionally, restore the connection between Client and DDMLib if we detach from the process".
 * See {@link AndroidRemoteDebugProcessHandler#detachProcessImpl()}
 */
final public class AndroidRemoteDebugProcessHandler extends ProcessHandler implements DeviceAwareProcessHandler {

  private final Project myProject;
  private final Client myClient;
  private final boolean myDetachIsDefault;

  public AndroidRemoteDebugProcessHandler(Project project,
                                          Client client,
                                          boolean detachIsDefault) {
    myProject = project;
    myClient = client;
    myDetachIsDefault = detachIsDefault;
  }


  // This is partially copied from com.intellij.debugger.engine.RemoteDebugProcessHandler#startNotify.
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
        ScheduledFuture<?> future =
          AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> notifyProcessDetached(), 1, TimeUnit.SECONDS);
        Disposer.register(myProject, () -> future.cancel(false));
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
    if (debugProcess != null) {
      // killing target debug VM doesn't work nice with all Android processes, invoke terminateAndroidProcess instead.
      debugProcess.stop(/*forceTerminate*/false);
    }
    notifyProcessTerminated(0);
  }

  @Override
  protected void detachProcessImpl() {
    DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this);
    if (debugProcess != null) {
      debugProcess.stop(false);
    }
    /*
      When the remote debugger terminates, it signals to the remote VM to shut down the connection,
      which is the very same JDWP connection used by DDMLib to communicate to the remote process.
      The fix is to notify the Client to reopen its communication channel to the process.

      For more information see http://b/37104675

      We do it only on detach and not destroy because after destroy client should be dead.
     */
    myClient.notifyVmMirrorExited();
    notifyProcessDetached();
  }

  @Override
  public boolean detachIsDefault() {
    return myDetachIsDefault;
  }

  @Override
  public OutputStream getProcessInput() {
    return null;
  }

  public boolean isPackageRunning(@Nullable IDevice device, @NotNull String packageName) {
    return (device == null || device == myClient.getDevice()) && packageName.equals(myClient.getClientData().getPackageName());
  }

  @Override
  public boolean isAssociated(@NotNull IDevice device) {
    return device == myClient.getDevice();
  }
}
