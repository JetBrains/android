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
  private final @NotNull Collection<@NotNull Device> myDevices;
  private final @NotNull Set<@NotNull Target> myTargets;

  TargetsForReadingSupplier(@NotNull Collection<@NotNull Device> devices,
                            @Nullable RunningDeviceTarget runningDeviceTarget,
                            @Nullable Target target) {
    this(devices, DeploymentCollections.toList(runningDeviceTarget), DeploymentCollections.toList(target));
  }

  TargetsForReadingSupplier(@NotNull Collection<@NotNull Device> devices,
                            @NotNull Collection<@NotNull RunningDeviceTarget> runningDeviceTargets,
                            @NotNull Collection<@NotNull Target> targets) {
    myDevices = devices;
    myTargets = Sets.newHashSetWithExpectedSize(devices.size());

    runningDeviceTargets.stream()
      .filter(runningDeviceTarget -> isDeviceRunning(runningDeviceTarget) || noTargetMatches(targets, runningDeviceTarget))
      .forEach(myTargets::add);

    targets.stream()
      .map(this::newRunningDeviceTargetIfDeviceIsRunning)
      .forEach(myTargets::add);
  }

  private static boolean noTargetMatches(@NotNull Collection<@NotNull Target> targets, @NotNull RunningDeviceTarget runningDeviceTarget) {
    Object key = runningDeviceTarget.getDeviceKey();

    return targets.stream()
      .map(Target::getDeviceKey)
      .noneMatch(key::equals);
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

  @NotNull Optional<@NotNull Target> getDropDownTarget() {
    return DeploymentCollections.toOptional(myTargets);
  }

  @NotNull Set<@NotNull Target> getDialogTargets() {
    return myTargets;
  }
}
