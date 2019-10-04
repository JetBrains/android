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
package com.android.tools.idea.run.util;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link ProcessHandler} based implementation of {@link LaunchStatus}.
 *
 * When an associated process handler's state becomes terminated, {@link #isLaunchTerminated()} also starts returning true.
 */
final public class ProcessHandlerLaunchStatus implements LaunchStatus {

  /**
   * A process handler of this launch. When this handler is terminated, the launch should be considered as terminated.
   *
   * Client may override the master process by {@link #setProcessHandler}.
   */
  @NotNull private ProcessHandler myProcessHandler;

  /**
   * Indicates whether the process has been terminated or is in the process of termination.
   * Ideally, we'd rely solely on the Process Handler's termination status, but it turns out that calls to terminate a non-started
   * process to terminate never have any effect until after the process is started.
   */
  private boolean myTerminated;

  private List<BooleanSupplier> launchTerminationConditions = Lists.newCopyOnWriteArrayList();

  /**
   * Constructs with a given process handler.
   *
   * @param processHandler a master process handler to be monitored
   */
  public ProcessHandlerLaunchStatus(@NotNull ProcessHandler processHandler) {
    myProcessHandler = processHandler;
  }

  /**
   * Returns the current master process handler.
   */
  @NotNull
  public ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  /**
   * Replaces the associated process handler with the given handler.
   *
   * @param processHandler a new master process handler to be used
   */
  public void setProcessHandler(@NotNull ProcessHandler processHandler) {
    myProcessHandler = processHandler;
  }

  @Override
  public boolean isLaunchTerminated() {
    if (myTerminated) {
      return true;
    }
    if (launchTerminationConditions.stream().anyMatch((condition) -> !condition.getAsBoolean())) {
      return false;
    }
    return myProcessHandler.isProcessTerminated() || myProcessHandler.isProcessTerminating();
  }

  @Override
  public void addLaunchTerminationCondition(BooleanSupplier launchTerminatedCondition) {
    launchTerminationConditions.add(launchTerminatedCondition);
  }

  @Override
  public void terminateLaunch(@Nullable String errorMessage, boolean destroyProcess) {
    myTerminated = true;
    if (!Strings.isNullOrEmpty(errorMessage)) {
      myProcessHandler.notifyTextAvailable(errorMessage + "\n", ProcessOutputTypes.STDERR);
    }
    if (destroyProcess) {
      myProcessHandler.destroyProcess();
    }
  }
}
