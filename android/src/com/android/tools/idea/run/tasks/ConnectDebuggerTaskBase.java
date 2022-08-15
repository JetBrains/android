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

import static com.android.tools.idea.run.debug.UtilsKt.waitForClientReadyForDebug;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.LaunchInfo;
import com.android.tools.idea.run.ProcessHandlerConsolePrinter;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Uninterruptibles;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

public abstract class ConnectDebuggerTaskBase implements ConnectDebuggerTask {
  private int myPollTimeoutSeconds = 15;
  private static final TimeUnit POLL_TIMEUNIT = TimeUnit.SECONDS;

  // The first entry in the list contains the main package name, and an optional second entry contains test package name.
  @NotNull protected final List<String> myApplicationIds;
  @NotNull protected final Project myProject;
  protected final boolean myAttachToRunningProcess;

  protected ConnectDebuggerTaskBase(@NotNull ApplicationIdProvider applicationIdProvider,
                                    @NotNull Project project,
                                    boolean attachToRunningProcess) {
    myProject = project;
    myAttachToRunningProcess = attachToRunningProcess;

    myApplicationIds = new LinkedList<>();

    try {
      String packageName = applicationIdProvider.getPackageName();
      myApplicationIds.add(packageName);
    }
    catch (ApkProvisionException e) {
      logger().error(e);
    }

    try {
      String testPackageName = applicationIdProvider.getTestPackageName();
      if (testPackageName != null) {
        myApplicationIds.add(testPackageName);
      }
    }
    catch (ApkProvisionException e) {
      // not as severe as failing to obtain package id for main application
      logger().warn("Unable to obtain test package name, will not connect debugger if tests don't instantiate main application");
    }
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Connecting Debugger";
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.CONNECT_DEBUGGER;
  }

  @Override
  public void setTimeoutSeconds(int timeoutSeconds) {
    myPollTimeoutSeconds = timeoutSeconds;
  }

  @Override
  public int getTimeoutSeconds() {
    return myPollTimeoutSeconds;
  }

  @Override
  public void perform(@NotNull final LaunchInfo launchInfo,
                      @NotNull IDevice device,
                      @NotNull final ProcessHandlerLaunchStatus state,
                      @NotNull final ProcessHandlerConsolePrinter printer) {
    final Client client;
    try {
      client = getClient(device);
    }
    catch (ExecutionException e) {
      logger().error(e);
      return;
    }

    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> launchDebugger(launchInfo, client, state, printer));
  }

  @NotNull
  protected Client getClient(@NotNull IDevice device) throws ExecutionException {
    final Client client;
    int pollTimeoutSeconds = myPollTimeoutSeconds;
    if (pollTimeoutSeconds <= 0) {
      pollTimeoutSeconds = Integer.MAX_VALUE;
    }
    client = waitForClientReadyForDebug(device, myApplicationIds, pollTimeoutSeconds);
    return client;
  }

  private static @NotNull Logger logger() {
    return Logger.getInstance(ConnectDebuggerTaskBase.class);
  }

  @VisibleForTesting // Allow unit tests to avoid actually sleeping.
  protected void sleep(long sleepFor, @NotNull TimeUnit unit) {
    Uninterruptibles.sleepUninterruptibly(sleepFor, unit);
  }

  public boolean isReadyForDebugging(@NotNull Client client, @NotNull ConsolePrinter printer) {
    return true;
  }

  public abstract void launchDebugger(@NotNull LaunchInfo currentLaunchInfo,
                                      @NotNull Client client,
                                      @NotNull ProcessHandlerLaunchStatus state,
                                      @NotNull ProcessHandlerConsolePrinter printer);
}
