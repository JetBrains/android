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

import com.android.tools.idea.run.DeviceCount;
import com.android.tools.idea.run.DeviceFutures;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DeployTarget<S extends DeployTargetState> {
  boolean hasCustomRunProfileState(@NotNull Executor executor);

  RunProfileState getRunProfileState(@NotNull final Executor executor, @NotNull ExecutionEnvironment env, @NotNull S state)
    throws ExecutionException;

  /**
   * @return the target to use, or null if the user cancelled (or there was an error). Null return values will end the launch quietly -
   * if an error needs to be displayed, the target chooser should surface it.
   */
  @Nullable
  DeviceFutures getDevices(@NotNull S state,
                           @NotNull AndroidFacet facet,
                           @NotNull DeviceCount deviceCount,
                           boolean debug,
                           int runConfigId);
}
