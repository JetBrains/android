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
package com.android.tools.idea.run.cloud;


import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.*;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CloudDebuggingTargetChooser implements TargetChooser {
  @NotNull
  private final String myCloudDeviceSerialNumber;

  public CloudDebuggingTargetChooser(@NotNull String cloudDeviceSerialNumber) {
    myCloudDeviceSerialNumber = cloudDeviceSerialNumber;
  }


  @Override
  public boolean matchesDevice(@NotNull IDevice device) {
    return device.getSerialNumber().equals(myCloudDeviceSerialNumber);
  }

  @NotNull
  @Override
  public DeviceTarget getTarget(@NotNull ConsolePrinter printer, @NotNull DeviceCount deviceCount, boolean debug) {
    // TODO: Prompt the user to launch a cloud device if none found.
    // TODO: Assert that we don't get multiple devices out here?
    return DeviceTarget.forDevices(DeviceSelectionUtils.getAllCompatibleDevices(new TargetDeviceFilter(this)));
  }

  @NotNull
  @Override
  public List<ValidationError> validate() {
    return ImmutableList.of();
  }
}
