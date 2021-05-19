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

import static com.intellij.execution.process.ProcessOutputTypes.STDERR;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.NullOutputReceiver;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.logcat.AndroidLogcatFormatter;
import com.android.tools.idea.logcat.AndroidLogcatPreferences;
import com.android.tools.idea.logcat.AndroidLogcatService;
import com.android.tools.idea.logcat.AndroidLogcatService.LogcatListener;
import com.android.tools.idea.logcat.output.LogcatOutputConfigurableProvider;
import com.android.tools.idea.logcat.output.LogcatOutputSettings;
import com.android.tools.idea.run.AndroidDebugState;
import com.android.tools.idea.run.AndroidProcessText;
import com.android.tools.idea.run.AndroidRemoteDebugProcessHandler;
import com.android.tools.idea.run.AndroidSessionInfo;
import com.android.tools.idea.run.ApplicationLogListener;
import com.android.tools.idea.run.LaunchInfo;
import com.android.tools.idea.run.ProcessHandlerConsolePrinter;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestSuiteConstantsKt;
import com.google.common.base.Preconditions;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;

public class ConnectJavaDebuggerTask extends ConnectDebuggerTask {

  public ConnectJavaDebuggerTask(@NotNull Set<String> applicationIds,
                                 @NotNull AndroidDebugger debugger,
                                 @NotNull Project project,
                                 boolean attachToRunningProcess) {
    super(applicationIds, debugger, project, attachToRunningProcess);
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

    ProcessHandler processHandler = launchStatus.getProcessHandler();
    RunContentDescriptor descriptor = Preconditions.checkNotNull(processHandler.getUserData(AndroidSessionInfo.KEY)).getDescriptor();

    // create a new process handler
    RemoteConnection connection = new RemoteConnection(true, "localhost", debugPort, false);
    ProcessHandler debugProcessHandler = new AndroidRemoteDebugProcessHandler(myProject);

    // switch the launch status and console printers to point to the new process handler
    // this is required, esp. for AndroidTestListener which holds a reference to the launch status and printers, and those should
    // be updated to point to the new process handlers, otherwise test results will not be forwarded appropriately
    launchStatus.setProcessHandler(debugProcessHandler);
    printer.setProcessHandler(debugProcessHandler);

    // detach after the launch status has been updated to point to the new process handler
    processHandler.detachProcess();

    final AndroidDebugState debugState;

    // Reuse the current ConsoleView to retain the UI state and not to lose test results.
    Object androidTestResultListener = processHandler.getCopyableUserData(AndroidTestSuiteConstantsKt.ANDROID_TEST_RESULT_LISTENER_KEY);
    if (androidTestResultListener instanceof ConsoleView) {
      ConsoleView consoleViewToReuse = (ConsoleView) androidTestResultListener;
      debugState = new AndroidDebugState(myProject, debugProcessHandler, connection, (parent, handler, executor) -> {
        consoleViewToReuse.attachToProcess(handler);
        return consoleViewToReuse;
      });
    } else {
      debugState = new AndroidDebugState(myProject, debugProcessHandler, connection, currentLaunchInfo.consoleProvider);
    }

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
    RunConfiguration runConfiguration = runProfile instanceof RunConfiguration ? (RunConfiguration)runProfile : null;
    AndroidSessionInfo.create(debugProcessHandler, debugDescriptor, runConfiguration, currentLaunchInfo.executor.getId(),
                              currentLaunchInfo.executor.getActionName(),
                              currentLaunchInfo.env.getExecutionTarget()
    );
    debugProcessHandler.putUserData(AndroidSessionInfo.ANDROID_DEBUG_CLIENT, client);
    debugProcessHandler.putUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL, client.getDevice().getVersion());

    final String pkgName = client.getClientData().getClientDescription();
    final IDevice device = client.getDevice();

    captureLogcatOutput(client, debugProcessHandler);

    // kill the process when the debugger is stopped
    debugProcessHandler.addProcessListener(new ProcessAdapter() {
      private int myTerminationCount = 0;
      private boolean myWillBeDestroyed = true;

      @Override
      public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
        Logger.getInstance(ConnectJavaDebuggerTask.class).info("Debugger-processWillTerminate: " + pkgName +
                                                               ", willBeDestroyed=" + willBeDestroyed);
        myWillBeDestroyed = willBeDestroyed;
        processTerminationCallback();
      }

      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        Logger.getInstance(ConnectJavaDebuggerTask.class).info("Debugger-processTerminated: " + pkgName);
        processTerminationCallback();
      }

      /**
       * In some cases (e.g. see b/37119032), processWillTerminate is called before processTerminated.
       * Since we need to know if the process termination is a disconnect or a terminate, we forward both calls to this method,
       * which perform its action only on the 2nd call (whichever it is).
       */
      private void processTerminationCallback() {
        myTerminationCount++;
        if (myTerminationCount != 2) {
          return;
        }
        debugProcessHandler.removeProcessListener(this);

        Client currentClient = device.getClient(pkgName);
        if (currentClient != null && currentClient.getClientData().getPid() != pid) {
          // a new process has been launched for the same package name, we aren't interested in killing this
          return;
        }

        if (myWillBeDestroyed) {
          Logger.getInstance(ConnectJavaDebuggerTask.class).info("Debugger terminating, so terminating process: " + pkgName);
          ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Stopping Application...") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              // Note: client.kill() doesn't work when the debugger is attached, we explicitly stop by package id.
              try {
                device.executeShellCommand("am force-stop " + pkgName, new NullOutputReceiver());
              }
              catch (Exception e) {
                // don't care..
              }
            }
          });
        }
        else {
          Logger.getInstance(ConnectJavaDebuggerTask.class).info("Debugger detaching, leaving process alive: " + pkgName);
        }
      }
    });

    return debugProcessHandler;
  }

  private static void captureLogcatOutput(@NotNull Client client,
                                          @NotNull ProcessHandler debugProcessHandler) {
    if (!StudioFlags.RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED.get()) {
      return;
    }
    if (!LogcatOutputSettings.getInstance().isDebugOutputEnabled()) {
      return;
    }

    final IDevice device = client.getDevice();
    LogcatListener logListener = new MyLogcatListener(client, debugProcessHandler);

    Logger.getInstance(ConnectJavaDebuggerTask.class).info(String.format("captureLogcatOutput(\"%s\")", device.getName()));
    AndroidLogcatService.getInstance().addListener(device, logListener, true);

    // Remove listener when process is terminated
    debugProcessHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        Logger.getInstance(ConnectJavaDebuggerTask.class)
          .info(String.format("captureLogcatOutput(\"%s\"): remove listener", device.getName()));
        AndroidLogcatService.getInstance().removeListener(device, logListener);
      }
    });
  }

  private static final class MyLogcatListener extends ApplicationLogListener {
    private static final String SIMPLE_FORMAT = AndroidLogcatFormatter.createCustomFormat(false, false, false, true);

    private final AndroidLogcatFormatter myFormatter;
    private final AtomicBoolean myIsFirstMessage;
    private final ProcessHandler myDebugProcessHandler;

    private MyLogcatListener(@NotNull Client client, @NotNull ProcessHandler debugProcessHandler) {
      // noinspection ConstantConditions
      super(client.getClientData().getClientDescription(), client.getClientData().getPid());

      myFormatter = new AndroidLogcatFormatter(ZoneId.systemDefault(), new AndroidLogcatPreferences());
      myIsFirstMessage = new AtomicBoolean(true);
      myDebugProcessHandler = debugProcessHandler;
    }

    @NotNull
    @Override
    protected String formatLogLine(@NotNull LogCatMessage line) {
      return myFormatter.formatMessage(SIMPLE_FORMAT, line.getHeader(), line.getMessage());
    }

    @Override
    protected void notifyTextAvailable(@NotNull String message, @NotNull Key key) {
      if (myIsFirstMessage.compareAndSet(true, false)) {
        myDebugProcessHandler.notifyTextAvailable(LogcatOutputConfigurableProvider.BANNER_MESSAGE + '\n', ProcessOutputTypes.STDOUT);
      }

      myDebugProcessHandler.notifyTextAvailable(message, key);
    }
  }
}
