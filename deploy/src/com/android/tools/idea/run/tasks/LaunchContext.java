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
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.execution.Executor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class LaunchContext {
  private final Project project;
  private final Executor executor;
  private final IDevice device;
  private final LaunchStatus launchStatus;
  private final ConsolePrinter consolePrinter;

  private final ProcessHandler processHandler;
  private boolean killBeforeLaunch = false;
  private boolean launchApp = true;

  /**
   * @param project        current project this launch is executing under
   * @param executor       a metadata of the executor of this task. Note that this is not a
   *                       {@code java.util.concurrent.Executor}
   * @param device         an Android device to perform this task against
   * @param launchStatus   a current status of this launch operation. An implementor of this method
   *                       should check the status periodically and cancel ongoing operations if it is
   *                       being terminated.
   * @param consolePrinter use this printer to output arbitrary messages
   * @param processHandler new {@link ProcessHandler} that the launch will be associated with
   */
  public LaunchContext(@NotNull Project project,
                       @NotNull Executor executor,
                       @NotNull IDevice device,
                       @NotNull LaunchStatus launchStatus,
                       @NotNull ConsolePrinter consolePrinter,
                       @NotNull ProcessHandler processHandler) {
    this.project = project;
    this.executor = executor;
    this.device = device;
    this.launchStatus = launchStatus;
    this.consolePrinter = consolePrinter;
    this.processHandler = processHandler;
  }

  @NotNull
  public Project getProject() {
    return project;
  }

  @NotNull
  public Executor getExecutor() {
    return executor;
  }

  @NotNull
  public IDevice getDevice() {
    return device;
  }

  @NotNull
  public LaunchStatus getLaunchStatus() {
    return launchStatus;
  }

  @NotNull
  public ConsolePrinter getConsolePrinter() {
    return consolePrinter;
  }

  @NotNull
  public ProcessHandler getProcessHandler() {
    return processHandler;
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
}
