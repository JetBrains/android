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

public class RealizedDeployTarget implements DeployTarget {
  @Nullable private final DeployTargetProvider myDelegate;
  @Nullable private final DeployTargetState myDelegateState;
  @Nullable private final DeviceFutures myDeviceFutures;

  public RealizedDeployTarget(@Nullable DeployTargetProvider delegate,
                              @Nullable DeployTargetState delegateState,
                              @Nullable DeviceFutures deviceFutures) {
    myDelegate = delegate;
    myDelegateState = delegateState;
    myDeviceFutures = deviceFutures;
  }

  @Override
  public boolean hasCustomRunProfileState(@NotNull Executor executor) {
    return myDelegate != null && myDelegateState != null;
  }

  @Override
  public RunProfileState getRunProfileState(@NotNull Executor executor,
                                            @NotNull ExecutionEnvironment env,
                                            @NotNull DeployTargetState state) throws ExecutionException {
    assert myDelegate != null;
    assert myDelegateState != null;

    return myDelegate.getDeployTarget().getRunProfileState(executor, env, myDelegateState);
  }

  @Nullable
  @Override
  public DeviceFutures getDevices(@NotNull DeployTargetState state,
                                  @NotNull AndroidFacet facet,
                                  @NotNull DeviceCount deviceCount,
                                  boolean debug,
                                  int runConfigId) {
    return myDeviceFutures;
  }
}
