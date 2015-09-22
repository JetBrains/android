/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

/** A simple container that maintains the state of devices at the time of a particular launch. */
public class DeviceStateAtLaunch {
  /** devices used in a particular launch. */
  private final Set<String> myDevicesUsedInLaunch;

  /** all the devices available at the time of the launch. */
  private final Set<String> myDevicesAvailableAtLaunch;

  public DeviceStateAtLaunch(@NotNull Collection<IDevice> usedDevices, @NotNull Collection<IDevice> allDevices) {
    myDevicesUsedInLaunch = serialize(usedDevices);
    myDevicesAvailableAtLaunch = serialize(allDevices);
  }

  /** Filters the given set of devices by only selecting those devices that were actually used in this launch. */
  public Collection<IDevice> filterByUsed(@NotNull Collection<IDevice> devices) {
    Set<IDevice> used = Sets.newHashSetWithExpectedSize(myDevicesUsedInLaunch.size());

    for (IDevice d : devices) {
      if (myDevicesUsedInLaunch.contains(d.getSerialNumber())) {
        used.add(d);
      }
    }

    return used;
  }

  /** Whether the devices available now were the same set of devices available at the time of this launch. */
  public boolean matchesCurrentAvailableDevices(@NotNull Collection<IDevice> devices) {
    if (myDevicesAvailableAtLaunch.size() != devices.size()) {
      return false;
    }

    for (IDevice d : devices) {
      if (!myDevicesAvailableAtLaunch.contains(d.getSerialNumber())) {
        return false;
      }
    }

    return true;
  }

  /** Whether the given device was used in this launch. */
  public boolean usedDevice(@NotNull IDevice device) {
    return myDevicesUsedInLaunch.contains(device.getSerialNumber());
  }

  private static Set<String> serialize(Collection<IDevice> usedDevices) {
    Set<String> s = Sets.newHashSetWithExpectedSize(usedDevices.size());
    for (IDevice d : usedDevices) {
      s.add(d.getSerialNumber());
    }
    return s;
  }
}
