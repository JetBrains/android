/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.google.idea.blaze.android.run.runner;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.execution.common.DeployOptions;
import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState;
import com.android.tools.idea.run.LaunchOptions;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.xdebugger.XDebugSession;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/** Interface for deploying and launching an Android application. */
public interface BlazeAndroidDeployAndLaunchStrategy {

  BlazeAndroidDeviceSelector getDeviceSelector();

  void augmentLaunchOptions(LaunchOptions.Builder options);

  /** Returns the tasks to deploy the application. */
  ImmutableList<BlazeLaunchTask> getDeployTasks(
      BlazeAndroidRunContext runContext, IDevice device, DeployOptions deployOptions)
      throws ExecutionException;

  /** Returns the task to launch the application. */
  @Nullable
  BlazeLaunchTask getApplicationLaunchTask(
      BlazeAndroidRunContext runContext,
      boolean isDebug,
      @Nullable Integer userId,
      @NotNull String contributorsAmStartOptions)
      throws ExecutionException;

  /** Returns the task to connect the debugger. */
  @Nullable
  XDebugSession startDebuggerSession(
      BlazeAndroidRunContext runContext,
      AndroidDebugger androidDebugger,
      AndroidDebuggerState androidDebuggerState,
      ExecutionEnvironment env,
      IDevice device,
      ConsoleView consoleView,
      ProgressIndicator indicator);

  String getAmStartOptions();

  @Nullable
  Integer getUserId(IDevice device) throws ExecutionException;

  /** Creates a {@link BlazeAndroidRunContext} for the run configuration. */
  BlazeAndroidRunContext createBlazeAndroidRunContext(
      ExecutionEnvironment env, ApkBuildStep buildStep, BlazeCommandRunConfiguration configuration)
      throws ExecutionException;
}
