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
package com.android.tools.idea.run;

import com.android.annotations.concurrency.WorkerThread;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.deployable.SwappableProcessHandler;
import com.google.common.base.Preconditions;
import com.intellij.execution.KillableProcess;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * AndroidProcessHandler is a {@link ProcessHandler} that corresponds to a single Android app potentially running on multiple connected
 * devices after it's launched from Studio. This handler monitors the target processes running on the devices and it terminates once
 * all the monitored processes are terminated. When you manually terminate this process handler, it will kill all running monitored
 * processes on devices.
 *
 * <p>Use {@link AndroidProcessHandler.Builder} to construct a new instance. You must specify the target process name (application name)
 * by {@link AndroidProcessHandler.Builder#setApplicationId}.
 *
 * TODO(hummer): Merge this class with AndroidProcessHandlerImpl and delete Builder since all constructor params are required. Builder
 *               pattern is not suitable anymore.
 */
public abstract class AndroidProcessHandler extends ProcessHandler implements KillableProcess, SwappableProcessHandler {

  /**
   * A builder to construct {@link AndroidProcessHandler}.
   */
  public static class Builder {
    @NotNull private final Project myProject;
    private String myApplicationId;

    public Builder(@NotNull Project project) {
      myProject = project;
    }

    @NotNull
    public Builder setApplicationId(@NotNull String appId) {
      myApplicationId = appId;
      return this;
    }

    /**
     * @deprecated Not supported anymore. To be deleted soon.
     */
    @NotNull
    @Deprecated
    public Builder monitorRemoteProcesses(boolean shouldMonitorRemoteProcesses) {
      return this;
    }

    /**
     * @throws IllegalStateException if setApplicationId was not called
     */
    @NotNull
    public AndroidProcessHandler build() {
      Preconditions.checkState(myApplicationId != null, "myApplicationId not set");
      return new AndroidProcessHandlerImpl(myProject, myApplicationId);
    }
  }

  /**
   * Adds a target device to this handler.
   */
  @WorkerThread
  public abstract void addTargetDevice(@NotNull IDevice device);

  /**
   * Checks if a given device is monitored by this handler. Returns true if it is monitored otherwise false.
   */
  @WorkerThread
  public abstract boolean isAssociated(@NotNull IDevice device);

  /**
   * Returns jdwp client of a target application running on a given device, or null if the device is not monitored by
   * this handler or the process is not running on a device.
   */
  @WorkerThread
  @Nullable
  public abstract Client getClient(@NotNull IDevice device);
}
