/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.android.run.runner;

import com.android.tools.idea.execution.common.AndroidSessionInfo;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.editor.DeployTarget;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** Selects a device. */
public interface BlazeAndroidDeviceSelector {
  /** A device session */
  class DeviceSession {
    @Nullable public final DeployTarget deployTarget;
    @Nullable public final DeviceFutures deviceFutures;

    public DeviceSession(
        @Nullable DeployTarget deployTarget, @Nullable DeviceFutures deviceFutures) {
      this.deployTarget = deployTarget;
      this.deviceFutures = deviceFutures;
    }

    // Only for back compat
    @VisibleForTesting
    public DeviceSession(
        @Nullable DeployTarget deployTarget,
        @Nullable DeviceFutures deviceFutures,
        @Nullable AndroidSessionInfo sessionInfo) {
      this(deployTarget, deviceFutures);
    }
  }

  DeviceSession getDevice(
      Project project, Executor executor, ExecutionEnvironment env, boolean debug, int runConfigId)
      throws ExecutionException;
  /** Standard device selector */
  class NormalDeviceSelector implements BlazeAndroidDeviceSelector {
    @Override
    public DeviceSession getDevice(
        Project project,
        Executor executor,
        ExecutionEnvironment env,
        boolean debug,
        int runConfigId) {
      DeployTarget deployTarget = BlazeDeployTargetService.getInstance(project).getDeployTarget();
      if (deployTarget == null) {
        return null;
      }
      DeviceFutures deviceFutures = null;
      if (!deployTarget.hasCustomRunProfileState(executor)) {
        deviceFutures = deployTarget.getDevices(project);
      }
      return new DeviceSession(deployTarget, deviceFutures);
    }
  }
}
