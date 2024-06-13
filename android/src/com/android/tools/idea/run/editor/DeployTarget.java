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
package com.android.tools.idea.run.editor;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.DeviceFutures;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public interface DeployTarget {
  boolean hasCustomRunProfileState(@NotNull Executor executor);

  RunProfileState getRunProfileState(@NotNull final Executor executor, @NotNull ExecutionEnvironment env, @NotNull DeployTargetState state)
    throws ExecutionException;

  /**
   * Launches the selected deployment target devices if necessary, and returns a DeviceFutures,
   * which holds the AndroidDevices and futures of the IDevices that will arrive when the devices
   * are booted.
   */
  @NotNull
  DeviceFutures getDevices(@NotNull Project project);

  /**
   * Returns the selected deployment targets as AndroidDevices. They may or may not be running;
   * this will not launch any devices that are not already running.
   */
  default @NotNull List<AndroidDevice> getAndroidDevices(@NotNull Project project) {
    // TODO(b/323568263): Remove this default definition when no longer needed for ASwB.
    throw new UnsupportedOperationException();
  }
}
