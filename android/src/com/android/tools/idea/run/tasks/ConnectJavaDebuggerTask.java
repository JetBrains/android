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
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.logcat.AndroidLogcatFormatter;
import com.android.tools.idea.logcat.AndroidLogcatPreferences;
import com.android.tools.idea.logcat.AndroidLogcatService;
import com.android.tools.idea.logcat.AndroidLogcatService.LogcatListener;
import com.android.tools.idea.logcat.LogcatHeaderFormat;
import com.android.tools.idea.logcat.LogcatHeaderFormat.TimestampFormat;
import com.android.tools.idea.logcat.output.LogcatOutputConfigurableProvider;
import com.android.tools.idea.logcat.output.LogcatOutputSettings;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.ApplicationLogListener;
import com.android.tools.idea.run.LaunchInfo;
import com.android.tools.idea.run.ProcessHandlerConsolePrinter;
import com.android.tools.idea.run.debug.StartJavaDebuggerKt;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestSuiteConstantsKt;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

public class ConnectJavaDebuggerTask extends ConnectDebuggerTaskBase {

  public ConnectJavaDebuggerTask(@NotNull ApplicationIdProvider applicationIdProvider,
                                 @NotNull Project project,
                                 boolean attachToRunningProcess) {
    super(applicationIdProvider, project, attachToRunningProcess);
  }

  @Override
  public void launchDebugger(@NotNull LaunchInfo currentLaunchInfo,
                             @NotNull final Client client,
                             @NotNull ProcessHandlerLaunchStatus launchStatus,
                             @NotNull ProcessHandlerConsolePrinter printer) {
    Logger logger = Logger.getInstance(ConnectJavaDebuggerTask.class);

    ProcessHandler processHandler = launchStatus.getProcessHandler();
    // Reuse the current ConsoleView to retain the UI state and not to lose test results.
    Object androidTestResultListener = processHandler.getCopyableUserData(AndroidTestSuiteConstantsKt.ANDROID_TEST_RESULT_LISTENER_KEY);

    logger.info("Attaching Java debugger");
    StartJavaDebuggerKt.attachJavaDebuggerToClient(
      myProject,
      client,
      currentLaunchInfo.env,
      (ConsoleView)androidTestResultListener,
      () -> {
        processHandler.detachProcess();
        return null;
      },
      (device) -> {
        device.forceStop(myApplicationIds.get(0));
        return Unit.INSTANCE;
      }
    ).onSuccess(session -> {
      ProcessHandler debugProcessHandler = session.getDebugProcess().getProcessHandler();
      captureLogcatOutput(client, debugProcessHandler);
      session.showSessionTab();
    });
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
    private static final LogcatHeaderFormat SIMPLE_FORMAT = new LogcatHeaderFormat(TimestampFormat.NONE, false, false, true);

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
