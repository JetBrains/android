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
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.DeployTarget;
import com.android.tools.idea.run.DeviceCount;
import com.android.tools.idea.run.TargetChooser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A target chooser for selecting and launching a cloud device.
 */
public class CloudDeviceTargetChooser implements TargetChooser {

  private final int myCloudDeviceConfigurationId;
  @NotNull private final String myCloudDeviceProjectId;

  public CloudDeviceTargetChooser(int cloudDeviceConfigurationId, @NotNull String cloudDeviceProjectId) {
    myCloudDeviceConfigurationId = cloudDeviceConfigurationId;
    myCloudDeviceProjectId = cloudDeviceProjectId;
  }

  @Nullable
  @Override
  public DeployTarget getTarget(@NotNull ConsolePrinter printer, @NotNull DeviceCount deviceCount, boolean debug) {
    // TODO: Actually launch the chosen device in here and return a DeviceTarget.
    return new CloudDeviceLaunchTarget(myCloudDeviceConfigurationId, myCloudDeviceProjectId);
  }

  @Override
  public boolean matchesDevice(@NotNull IDevice device) {
    return false;
  }
}
