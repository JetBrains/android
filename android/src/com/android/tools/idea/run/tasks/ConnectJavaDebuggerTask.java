/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.NullOutputReceiver;
import com.android.tools.idea.fd.InstantRunUtils;
import com.android.tools.idea.run.*;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.intellij.debugger.engine.RemoteDebugProcessHandler;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Set;

import static com.intellij.execution.process.ProcessOutputTypes.STDERR;

public class ConnectJavaDebuggerTask extends ConnectDebuggerTask {

  public ConnectJavaDebuggerTask(@NotNull Set<String> applicationIds,
                                 @NotNull AndroidDebugger debugger,
                                 @NotNull Project project) {
    super(applicationIds, debugger, project);
  }

  @Override
  public ProcessHandler launchDebugger(@NotNull LaunchInfo currentLaunchInfo,
                                       @NotNull final Client client,
                                       @NotNull ProcessHandlerLaunchStatus launchStatus,
                                       @NotNull ProcessHandlerConsolePrinter printer) {
    String debugPort = Integer.toString(client.getDebuggerListenPort());
    final int pid = client.getClientData().getPid();
    Logger.getInstance(ConnectJavaDebuggerTask.class)
      .info(String.format(Locale.US, "Attempting to connect debugger to port %1$s [client %2$d]", debugPort, pid));

    // detach old process handler
    RunContentDescriptor descriptor = ((AndroidProgramRunner)currentLaunchInfo.runner).getDescriptor();
    final ProcessHandler processHandler = descriptor.getProcessHandler();
    assert processHandler != null;

    // create a new process handler
    RemoteConnection connection = new RemoteConnection(true, "localhost", debugPort, false);
    final RemoteDebugProcessHandler debugProcessHandler = new RemoteDebugProcessHandler(myProject);

    // switch the launch status and console printers to point to the new process handler
    // this is required, esp. for AndroidTestListener which holds a reference to the launch status and printers, and those should
    // be updated to point to the new process handlers, otherwise test results will not be forwarded appropriately
    launchStatus.setProcessHandler(debugProcessHandler);
    printer.setProcessHandler(debugProcessHandler);

    // detach after the launch status has been updated to point to the new process handler
    processHandler.detachProcess();

    AndroidDebugState debugState = new AndroidDebugState(myProject, debugProcessHandler, connection, currentLaunchInfo.consoleProvider);

    RunContentDescriptor debugDescriptor;
    try {
      // @formatter:off
      ExecutionEnvironment debugEnv = new ExecutionEnvironmentBuilder(currentLaunchInfo.env)
        .executor(currentLaunchInfo.executor)
        .runner(currentLaunchInfo.runner)
        .contentToReuse(descriptor)
        .build();
      debugDescriptor = DebuggerPanelsManager.getInstance(myProject).attachVirtualMachine(debugEnv, debugState, connection, false);
      // @formatter:on
    }
    catch (ExecutionException e) {
      processHandler.notifyTextAvailable("ExecutionException: " + e.getMessage() + '.', STDERR);
      return null;
    }

    if (debugDescriptor == null) {
      processHandler.notifyTextAvailable("Unable to connect debugger to Android application", STDERR);
      return null;
    }

    // re-run the collected text from the old process handler to the new
    // TODO: is there a race between messages received once the debugger has been connected, and these messages that are printed out?
    final AndroidProcessText oldText = AndroidProcessText.get(processHandler);
    if (oldText != null) {
      oldText.printTo(debugProcessHandler);
    }

    RunProfile runProfile = currentLaunchInfo.env.getRunProfile();
    int uniqueId = runProfile instanceof AndroidRunConfigurationBase ? ((AndroidRunConfigurationBase)runProfile).getUniqueID() : -1;
    AndroidSessionInfo value = new AndroidSessionInfo(debugProcessHandler, debugDescriptor, uniqueId, currentLaunchInfo.executor.getId(),
                                                      InstantRunUtils.isInstantRunEnabled(currentLaunchInfo.env));
    debugProcessHandler.putUserData(AndroidSessionInfo.KEY, value);
    debugProcessHandler.putUserData(AndroidProgramRunner.ANDROID_DEBUG_CLIENT, client);
    debugProcessHandler.putUserData(AndroidProgramRunner.ANDROID_DEVICE_API_LEVEL, client.getDevice().getVersion());

    final String pkgName = client.getClientData().getClientDescription();
    final IDevice device = client.getDevice();

    // kill the process when the debugger is stopped
    debugProcessHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(ProcessEvent event) {
        debugProcessHandler.removeProcessListener(this);

        Client currentClient = device.getClient(pkgName);
        if (currentClient != null && currentClient.getClientData().getPid() != pid) {
          // a new process has been launched for the same package name, we aren't interested in killing this
          return;
        }

        Logger.getInstance(ConnectJavaDebuggerTask.class).info("Debugger terminating, so terminating process: " + pkgName);
        // Note: client.kill() doesn't work when the debugger is attached, we explicitly stop by package id..
        try {
          device.executeShellCommand("am force-stop " + pkgName, new NullOutputReceiver());
        }
        catch (Exception e) {
          // don't care..
        }
      }
    });

    return debugProcessHandler;
  }
}
