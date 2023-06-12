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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Consumes the selection from the drop down or dialog and splits it up into RunningDeviceTargets and nonRunningDeviceTargets
 * (ColdBootTarget, QuickBootTarget, BootWithSnapshotTarget) to write to DevicesSelectedService.State. For each RunningDeviceTarget in the
 * selection, the corresponding nonRunningDeviceTarget is kept from oldTargets. So the original target selection isn't lost when the device
 * is stopped.
 */
final class TargetsForWritingSupplier {
  private final @NotNull Collection<RunningDeviceTarget> myRunningDeviceTargets;
  private final @NotNull Collection<Target> myTargets;

  TargetsForWritingSupplier(@Nullable Target oldTarget, @Nullable Target newTarget) {
    this(DeploymentCollections.toList(oldTarget), DeploymentCollections.toList(newTarget));
  }

  /**
   * @param oldTargets either the list from DevicesSelectedService.State.targetSelectedWithDropDown or targetsSelectedWithDialog. None of
   *                   these will be RunningDeviceTargets.
   * @param newTargets the new selection from the drop down or dialog. May contain RunningDeviceTargets.
   */
  TargetsForWritingSupplier(@NotNull Collection<Target> oldTargets, @NotNull Collection<Target> newTargets) {
    Map<Key, Target> keyToTargetMap = oldTargets.stream().collect(Collectors.toMap(Target::getDeviceKey, target -> target));

    int size = newTargets.size();
    myRunningDeviceTargets = new ArrayList<>(size);
    myTargets = new ArrayList<>(size);

    newTargets.forEach(newTarget -> {
      if (newTarget instanceof RunningDeviceTarget) {
        Target oldTarget = keyToTargetMap.get(newTarget.getDeviceKey());

        if (oldTarget != null) {
          myTargets.add(oldTarget);
        }

        myRunningDeviceTargets.add((RunningDeviceTarget)newTarget);
      }
      else {
        myTargets.add(newTarget);
      }
    });
  }

  @NotNull Optional<RunningDeviceTarget> getDropDownRunningDeviceTarget() {
    return DeploymentCollections.toOptional(myRunningDeviceTargets);
  }

  @NotNull Optional<Target> getDropDownTarget() {
    return DeploymentCollections.toOptional(myTargets);
  }

  @NotNull Collection<RunningDeviceTarget> getDialogRunningDeviceTargets() {
    return myRunningDeviceTargets;
  }

  @NotNull Collection<Target> getDialogTargets() {
    return myTargets;
  }
}
