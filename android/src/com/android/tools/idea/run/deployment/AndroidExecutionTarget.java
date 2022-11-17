/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import com.android.ddmlib.IDevice;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.execution.ExecutionTarget;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;

public abstract class AndroidExecutionTarget extends ExecutionTarget {
  public abstract boolean isApplicationRunning(@NotNull String packageName);

  /**
   * @return the number of (potentially) launch-able devices in this execution target
   */
  public abstract int getAvailableDeviceCount();

  /**
   * @return the collection of running devices to run a configuration on, apply changes to, etc
   */
  public @NotNull ListenableFuture<@NotNull Collection<@NotNull IDevice>> getRunningDevicesAsync() {
    throw new UnsupportedOperationException();
  }

  /**
   * @return the collection of live running devices to run a configuration on, apply changes to, etc
   * @deprecated Use {@link #getRunningDevicesAsync}
   */
  @Deprecated
  @NotNull
  public abstract Collection<IDevice> getRunningDevices();

  @Override
  public boolean isExternallyManaged() {
    return true;
  }
}
