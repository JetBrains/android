/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.android.ddmlib.*;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static com.intellij.execution.process.ProcessOutputTypes.STDERR;
import static com.intellij.execution.process.ProcessOutputTypes.STDOUT;

public abstract class AndroidApplicationLauncher {
  private static final Logger LOG = Logger.getInstance(AndroidApplicationLauncher.class);

  public abstract LaunchResult launch(@NotNull AndroidRunningState state, @NotNull IDevice device)
    throws IOException, AdbCommandRejectedException, TimeoutException;

  public boolean isReadyForDebugging(@NotNull ClientData data, @Nullable ProcessHandler processHandler) {
    return data.getDebuggerConnectionStatus() == ClientData.DebuggerStatus.WAITING;
  }

  public enum LaunchResult {
    SUCCESS, STOP, NOTHING_TO_DO
  }

  protected LaunchResult executeCommand(@NotNull String command, @NotNull AndroidRunningState state, @NotNull IDevice device)
    throws IOException, AdbCommandRejectedException, TimeoutException {
    ProcessHandler processHandler = state.getProcessHandler();
    ErrorMatchingReceiver receiver = new ErrorMatchingReceiver(state.getStoppedRef());
    while (true) {
      if (state.isStopped()) return LaunchResult.STOP;
      boolean deviceNotResponding = false;
      try {
        state.executeDeviceCommandAndWriteToConsole(device, command, receiver);
      }
      catch (ShellCommandUnresponsiveException e) {
        LOG.info(e);
        deviceNotResponding = true;
      }
      // TODO: What is error type 2?
      if (!deviceNotResponding && receiver.getErrorType() != 2) {
        break;
      }
      processHandler.notifyTextAvailable("Device is not ready. Waiting for " + AndroidRunningState.WAITING_TIME_SECS + " sec.\n", STDOUT);
      synchronized (state.getRunningLock()) {
        try {
          state.getRunningLock().wait(AndroidRunningState.WAITING_TIME_SECS * 1000);
        }
        catch (InterruptedException e) {
        }
      }
      receiver = new ErrorMatchingReceiver(state.getStoppedRef());
    }

    boolean success = !receiver.hasError();
    if (success) {
      processHandler.notifyTextAvailable(receiver.getOutput().toString(), STDOUT);
      return LaunchResult.SUCCESS;
    }
    else {
      processHandler.notifyTextAvailable(receiver.getOutput().toString(), STDERR);
      return LaunchResult.STOP;
    }
  }

  /** Returns the flags used to the "am start" command for launching in debug mode. */
  @NotNull
  protected String getDebugFlags(@NotNull AndroidRunningState state) {
    return state.isDebugMode() ? "-D" : "";
  }
}
