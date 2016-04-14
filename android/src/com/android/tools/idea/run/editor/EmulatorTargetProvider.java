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

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.tools.idea.run.*;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EmulatorTargetProvider extends DeployTargetProvider<EmulatorTargetProvider.State> {
  public static final class State extends DeployTargetState {
    public String PREFERRED_AVD = "";

    @NotNull
    @Override
    public List<ValidationError> validate(@NotNull AndroidFacet facet) {
      if (StringUtil.isEmpty(PREFERRED_AVD)) {
        return ImmutableList.of();
      }

      AvdManager avdManager = facet.getAvdManagerSilently();
      if (avdManager == null) {
        return ImmutableList.of(ValidationError.fatal(AndroidBundle.message("avd.cannot.be.loaded.error")));
      }

      AvdInfo avdInfo = avdManager.getAvd(PREFERRED_AVD, false);
      if (avdInfo == null) {
        return ImmutableList.of(ValidationError.fatal(AndroidBundle.message("avd.not.found.error", PREFERRED_AVD)));
      }

      if (avdInfo.getStatus() != AvdInfo.AvdStatus.OK) {
        String message = avdInfo.getErrorMessage();
        message = AndroidBundle.message("avd.not.valid.error", PREFERRED_AVD) +
                  (message != null ? ": " + message: "") + ". Try to repair it through AVD manager";
        return ImmutableList.of(ValidationError.fatal(message));
      }

      return ImmutableList.of();
    }
  }

  @NotNull
  @Override
  public String getId() {
    return TargetSelectionMode.EMULATOR.name();
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Emulator";
  }

  @NotNull
  @Override
  public State createState() {
    return new State();
  }

  @Override
  public DeployTargetConfigurable createConfigurable(@NotNull Project project,
                                                     Disposable parentDisposable,
                                                     @NotNull DeployTargetConfigurableContext context) {
    return new EmulatorTargetConfigurable(project, parentDisposable, context);
  }

  @Override
  public DeployTarget<State> getDeployTarget() {
    return new DeployTarget<State>() {
      @Override
      public boolean hasCustomRunProfileState(@NotNull Executor executor) {
        return false;
      }

      @Override
      public RunProfileState getRunProfileState(@NotNull Executor executor, @NotNull ExecutionEnvironment env, @NotNull State state)
        throws ExecutionException {
        return null;
      }

      @Nullable
      @Override
      public DeviceFutures getDevices(@NotNull State state,
                                      @NotNull AndroidFacet facet,
                                      @NotNull DeviceCount deviceCount,
                                      boolean debug,
                                      int runConfigId) {
        return new EmulatorTargetChooser(facet, Strings.emptyToNull(state.PREFERRED_AVD)).getDevices(deviceCount);
      }
    };
  }
}
