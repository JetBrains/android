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
import com.android.tools.idea.ddms.DevicePropertyUtil;
import com.android.tools.idea.run.*;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.intellij.debugger.engine.RemoteDebugProcessHandler;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfile;
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
  private final Project myProject;

  public ConnectJavaDebuggerTask(@NotNull Project project, @NotNull Set<String> applicationIds) {
    super(applicationIds);
    myProject = project;
  }

  @Override
  public ProcessHandler launchDebugger(@NotNull LaunchInfo currentLaunchInfo,
                                       @NotNull Client client,
                                       @NotNull ProcessHandlerLaunchStatus launchStatus,
                                       @NotNull ProcessHandlerConsolePrinter printer) {
    String debugPort = Integer.toString(client.getDebuggerListenPort());
    int pid = client.getClientData().getPid();
    Logger.getInstance(ConnectJavaDebuggerTask.class)
      .info(String.format(Locale.US, "Attempting to connect debugger to port %1$s [client %2$d]", debugPort, pid));

    // detach old process handler
    RunContentDescriptor descriptor = ((AndroidDebugRunner)currentLaunchInfo.runner).getDescriptor();
    final ProcessHandler processHandler = descriptor.getProcessHandler();
    assert processHandler != null;

    // create a new process handler
    RemoteConnection connection = new RemoteConnection(true, "localhost", debugPort, false);
    RemoteDebugProcessHandler debugProcessHandler = new RemoteDebugProcessHandler(myProject);

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

    // re-run the collected text from the old process handler to the new
    // TODO: is there a race between messages received once the debugger has been connected, and these messages that are printed out?
    final AndroidProcessText oldText = AndroidProcessText.get(processHandler);
    if (oldText != null) {
      oldText.printTo(debugProcessHandler);
    }

    RunProfile runProfile = currentLaunchInfo.env.getRunProfile();
    int uniqueId = runProfile instanceof AndroidRunConfigurationBase ? ((AndroidRunConfigurationBase)runProfile).getUniqueID() : -1;
    AndroidSessionInfo value = new AndroidSessionInfo(debugProcessHandler, debugDescriptor, uniqueId, currentLaunchInfo.executor.getId());
    debugProcessHandler.putUserData(AndroidSessionInfo.KEY, value);
    debugProcessHandler.putUserData(AndroidDebugRunner.ANDROID_DEBUG_CLIENT, client);
    debugProcessHandler.putUserData(AndroidDebugRunner.ANDROID_DEVICE_API_LEVEL, client.getDevice().getVersion());

    // Reverted: b/25506206
    // kill the process when the debugger is stopped
    //handler.addProcessListener(new ProcessAdapter() {
    //  @Override
    //  public void processTerminated(ProcessEvent event) {
    //    handler.removeProcessListener(this);
    //
    //    // Note: client.kill() doesn't work when the debugger is attached, we explicitly stop by package id..
    //    try {
    //      device.executeShellCommand("am force-stop " + myRunningState.getPackageName(), new NullOutputReceiver());
    //    }
    //    catch (Exception e) {
    //      // don't care..
    //    }
    //  }
    //});

    return debugProcessHandler;
  }
}
