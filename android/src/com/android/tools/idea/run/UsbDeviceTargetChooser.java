/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * A target chooser for selecting a connected USB device (any non-emulator).
 */
public class UsbDeviceTargetChooser implements TargetChooser {
  @NotNull private final AndroidFacet myFacet;
  private final boolean mySupportMultipleDevices;

  public UsbDeviceTargetChooser(@NotNull AndroidFacet facet, boolean supportMultipleDevices) {
    myFacet = facet;
    mySupportMultipleDevices = supportMultipleDevices;
  }

  @Override
  public boolean matchesDevice(@NotNull IDevice device) {
    return !device.isEmulator();
  }

  @Nullable
  @Override
  public DeviceTarget getTarget() {
    Collection<IDevice> runningDevices = DeviceSelectionUtils
      .chooseRunningDevice(myFacet, new TargetDeviceFilter(this), mySupportMultipleDevices);
    if (runningDevices == null) {
      // The user canceled.
      return null;
    }
    return DeviceTarget.forDevices(runningDevices);
  }
}
