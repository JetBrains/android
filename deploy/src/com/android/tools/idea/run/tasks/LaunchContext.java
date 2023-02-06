/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.ddmlib.IDevice;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

public class LaunchContext {
  private final ExecutionEnvironment env;
  private final IDevice device;
  private final ConsoleView consolePrinter;

  private final ProcessHandler processHandler;
  private final ProgressIndicator progressIndicator;
  private boolean killBeforeLaunch = false;
  private boolean launchApp = true;

  /**
   * @param env
   * @param device         an Android device to perform this task against
   * @param consolePrinter use this printer to output arbitrary messages
   * @param processHandler new {@link ProcessHandler} that the launch will be associated with
   */
  public LaunchContext(@NotNull ExecutionEnvironment env,
                       @NotNull IDevice device,
                       @NotNull ConsoleView consoleView,
                       @NotNull ProcessHandler processHandler,
                       @NotNull ProgressIndicator progressIndicator) {
    this.env = env;
    this.device = device;
    this.consolePrinter = consoleView;
    this.processHandler = processHandler;
    this.progressIndicator = progressIndicator;
  }

  @NotNull
  public IDevice getDevice() {
    return device;
  }

  @NotNull
  public ConsoleView getConsoleView() {
    return consolePrinter;
  }

  @NotNull
  public ProcessHandler getProcessHandler() {
    return processHandler;
  }

  public @NotNull ProgressIndicator getProgressIndicator() {
    return progressIndicator;
  }

  public void setKillBeforeLaunch(boolean killBeforeLaunch) {
    this.killBeforeLaunch = killBeforeLaunch;
  }

  public boolean getKillBeforeLaunch() {
    return killBeforeLaunch;
  }

  public void setLaunchApp(boolean launchApp) {
    this.launchApp = launchApp;
  }

  public boolean getLaunchApp() {
    return launchApp;
  }

  public ExecutionEnvironment getEnv() {
    return env;
  }
}
