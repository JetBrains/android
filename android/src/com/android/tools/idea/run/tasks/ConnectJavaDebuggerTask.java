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

import static com.android.tools.idea.run.debug.StartDebuggerKt.attachDebugger;
import static com.android.tools.idea.run.debug.StartJavaDebuggerKt.getDebugProcessStarter;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.LaunchInfo;
import com.android.tools.idea.run.ProcessHandlerConsolePrinter;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestSuiteConstantsKt;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

public class ConnectJavaDebuggerTask extends ConnectDebuggerTaskBase {

  public ConnectJavaDebuggerTask(@NotNull ApplicationIdProvider applicationIdProvider,
                                 @NotNull Project project) {
    super(applicationIdProvider, project);
  }

  @Override
  public void launchDebugger(@NotNull LaunchInfo currentLaunchInfo,
                             @NotNull final Client client,
                             @NotNull ProcessHandlerLaunchStatus launchStatus,
                             @NotNull ProcessHandlerConsolePrinter printer) {
    Logger logger = Logger.getInstance(ConnectJavaDebuggerTask.class);

    ProcessHandler processHandler = launchStatus.getProcessHandler();
    // Reuse the current ConsoleView to retain the UI state and not to lose test results.
    ConsoleView androidTestResultListener =
      (ConsoleView)processHandler.getCopyableUserData(AndroidTestSuiteConstantsKt.ANDROID_TEST_RESULT_LISTENER_KEY);

    logger.info("Attaching Java debugger");

    Function0<Unit> onDebugProcessStarted = () -> {
      processHandler.detachProcess();
      return null;
    };
    Function1<IDevice, Unit> onDebugProcessDestroyed = (device) -> {
      device.forceStop(myApplicationIds.get(0));
      return Unit.INSTANCE;
    };

    ExecutionEnvironment env = currentLaunchInfo.env;
    Function0<Promise<XDebugProcessStarter>> debugProcessStarter = () ->
      getDebugProcessStarter(env.getProject(),
                             client,
                             androidTestResultListener,
                             onDebugProcessStarted,
                             onDebugProcessDestroyed,
                             false
      );
    attachDebugger(env.getProject(), client, env, debugProcessStarter).onSuccess(XDebugSessionImpl::showSessionTab);
  }
}
