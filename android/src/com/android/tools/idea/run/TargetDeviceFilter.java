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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.google.common.base.Predicate;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ThreeState;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public abstract class TargetDeviceFilter implements Predicate<IDevice> {
  @Override
  public boolean apply(@Nullable IDevice device) {
    return device != null && matchesDevice(device);
  }

  public abstract boolean matchesDevice(@NotNull IDevice device);

  public static class EmulatorFilter extends TargetDeviceFilter {
    @NotNull private final AndroidFacet myFacet;
    @Nullable private final String myPreferredAvd;

    public EmulatorFilter(@NotNull AndroidFacet facet, @Nullable String preferredAvd) {
      myFacet = facet;
      myPreferredAvd = preferredAvd;
    }

    @Override
    public boolean matchesDevice(@NotNull IDevice device) {
      if (!device.isEmulator()) {
        return false;
      }
      String avdName = device.getAvdName();
      if (myPreferredAvd != null) {
        return myPreferredAvd.equals(avdName);
      }

      AndroidPlatform androidPlatform = myFacet.getConfiguration().getAndroidPlatform();
      if (androidPlatform == null) {
        Logger.getInstance(EmulatorFilter.class).warn("Target Android platform not set for module: " + myFacet.getModule().getName());
        return false;
      } else {
        AndroidDevice connectedDevice = new ConnectedAndroidDevice(device, null);
        LaunchCompatibility compatibility = connectedDevice.canRun(AndroidModuleInfo.get(myFacet).getRuntimeMinSdkVersion(),
                                                                   androidPlatform.getTarget(),
                                                                   EnumSet.noneOf(IDevice.HardwareFeature.class));
        return compatibility.isCompatible() != ThreeState.NO;
      }
    }
  }

  public static class UsbDeviceFilter extends TargetDeviceFilter {
    @Override
    public boolean matchesDevice(@NotNull IDevice device) {
      return !device.isEmulator();
    }
  }
}
