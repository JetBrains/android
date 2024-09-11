/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.tools.idea.projectsystem.ApplicationProjectContext;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.ConsoleProvider;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.blaze.BlazeLaunchTask;
import com.android.tools.idea.run.blaze.BlazeLaunchTasksProvider;
import com.android.tools.idea.run.editor.ProfilerState;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.xdebugger.XDebugSession;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/** Instantiated when the configuration wants to run. */
public interface BlazeAndroidRunContext {

  BlazeAndroidDeviceSelector getDeviceSelector();

  void augmentLaunchOptions(LaunchOptions.Builder options);

  ConsoleProvider getConsoleProvider();

  ApkBuildStep getBuildStep();

  ApplicationIdProvider getApplicationIdProvider();

  ApplicationProjectContext getApplicationProjectContext();

  BlazeLaunchTasksProvider getLaunchTasksProvider(LaunchOptions launchOptions)
      throws ExecutionException;

  /** Returns the tasks to deploy the application. */
  ImmutableList<BlazeLaunchTask> getDeployTasks(IDevice device, DeployOptions deployOptions)
      throws ExecutionException;

  /** Returns the task to launch the application. */
  @Nullable
  BlazeLaunchTask getApplicationLaunchTask(
      boolean isDebug, @Nullable Integer userId, @NotNull String contributorsAmStartOptions)
      throws ExecutionException;

  /** Returns the task to connect the debugger. */
  @Nullable
  XDebugSession startDebuggerSession(
      AndroidDebugger androidDebugger,
      AndroidDebuggerState androidDebuggerState,
      ExecutionEnvironment env,
      IDevice device,
      ConsoleView consoleView,
      ProgressIndicator indicator,
      String packageName);

  @Nullable
  Integer getUserId(IDevice device) throws ExecutionException;

  String getAmStartOptions();

  Executor getExecutor();

  ProfilerState getProfileState();
}
