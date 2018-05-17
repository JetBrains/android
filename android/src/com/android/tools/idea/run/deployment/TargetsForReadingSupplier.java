/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Supplies the targets read from DevicesSelectedService.State. If a target device is running, this class replaces the Target with a
 * RunningDeviceTarget.
 *
 * <p>RunningDeviceTargets are kept separately in State so the original target selection in the drop down or dialog isn't lost when the
 * device is stopped
 */
final class TargetsForReadingSupplier {
  private final @NotNull Collection<Device> myDevices;

  private final @NotNull Set<Target> myTargets;
  private final @NotNull Set<RunningDeviceTarget> myRunningDeviceTargetsToRemove;

  TargetsForReadingSupplier(@NotNull Collection<Device> devices,
                            @Nullable RunningDeviceTarget runningDeviceTarget,
                            @Nullable Target target) {
    this(devices, DeploymentCollections.toList(runningDeviceTarget), DeploymentCollections.toList(target));
  }

  TargetsForReadingSupplier(@NotNull Collection<Device> devices,
                            @NotNull Collection<RunningDeviceTarget> runningDeviceTargets,
                            @NotNull Collection<Target> targets) {
    myDevices = devices;

    int size = devices.size();
    myTargets = Sets.newHashSetWithExpectedSize(size);
    myRunningDeviceTargetsToRemove = Sets.newHashSetWithExpectedSize(size);

    runningDeviceTargets.forEach(runningDeviceTarget -> {
      if (isDeviceRunning(runningDeviceTarget)) {
        myTargets.add(runningDeviceTarget);
      }
      else {
        myRunningDeviceTargetsToRemove.add(runningDeviceTarget);
      }
    });

    targets.stream()
      .map(this::newRunningDeviceTargetIfDeviceIsRunning)
      .forEach(myTargets::add);
  }

  private @NotNull Target newRunningDeviceTargetIfDeviceIsRunning(@NotNull Target target) {
    if (isDeviceRunning(target)) {
      return new RunningDeviceTarget(target.getDeviceKey());
    }

    return target;
  }

  private boolean isDeviceRunning(@NotNull Target target) {
    Object key = target.getDeviceKey();

    return myDevices.stream()
      .filter(Device::isConnected)
      .map(Device::getKey)
      .anyMatch(key::equals);
  }

  @NotNull Optional<Target> getDropDownTarget() {
    return DeploymentCollections.toOptional(myTargets);
  }

  @NotNull Optional<RunningDeviceTarget> getDropDownRunningDeviceTargetToRemove() {
    return DeploymentCollections.toOptional(myRunningDeviceTargetsToRemove);
  }

  @NotNull Set<Target> getDialogTargets() {
    return myTargets;
  }

  @NotNull Set<RunningDeviceTarget> getDialogRunningDeviceTargetsToRemove() {
    return myRunningDeviceTargetsToRemove;
  }
}
