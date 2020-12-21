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

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.editor.DeployTarget;
import com.android.tools.idea.run.editor.DeployTargetState;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

final class DeviceAndSnapshotComboBoxTarget implements DeployTarget {
  private final @NotNull Set<@NotNull Target> myTargets;
  private final @NotNull Function<@NotNull Project, @NotNull AsyncDevicesGetter> myAsyncDevicesGetterGetInstance;

  DeviceAndSnapshotComboBoxTarget(@NotNull Set<@NotNull Target> targets) {
    this(targets, AsyncDevicesGetter::getInstance);
  }

  @VisibleForTesting
  DeviceAndSnapshotComboBoxTarget(@NotNull Set<@NotNull Target> targets,
                                  @NotNull Function<@NotNull Project, @NotNull AsyncDevicesGetter> asyncDevicesGetterGetInstance) {
    myTargets = targets;
    myAsyncDevicesGetterGetInstance = asyncDevicesGetterGetInstance;
  }

  @VisibleForTesting
  @NotNull Set<@NotNull Target> getTargets() {
    return myTargets;
  }

  @Override
  public boolean hasCustomRunProfileState(@NotNull Executor executor) {
    return false;
  }

  @NotNull
  @Override
  public RunProfileState getRunProfileState(@NotNull Executor executor,
                                            @NotNull ExecutionEnvironment environment,
                                            @NotNull DeployTargetState state) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public DeviceFutures getDevices(@NotNull AndroidFacet facet) {
    Project project = facet.getModule().getProject();

    return myAsyncDevicesGetterGetInstance.apply(project).get()
      .map(devices -> newDeviceFutures(devices, project))
      .orElse(new DeviceFutures(Collections.emptyList()));
  }

  private @NotNull DeviceFutures newDeviceFutures(@NotNull List<@NotNull Device> devices, @NotNull Project project) {
    devices = Target.filterDevices(myTargets, devices);
    Map<Key, Target> map = myTargets.stream().collect(Collectors.toMap(Target::getDeviceKey, target -> target));
    List<AndroidDevice> androidDevices = new ArrayList<>(devices.size());

    for (Device device : devices) {
      if (!device.isConnected()) {
        map.get(device.getKey()).boot((VirtualDevice)device, project);
      }

      androidDevices.add(device.getAndroidDevice());
    }

    return new DeviceFutures(androidDevices);
  }
}
