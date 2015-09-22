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

import com.android.tools.idea.run.DeployTarget;
import org.jetbrains.annotations.NotNull;

/**
 * A target for launching a cloud device.
 * TODO: Eliminate this class when we have proper cloud device selection support. See {@link CloudDeviceLaunchRunningState}.
 */
public class CloudDeviceLaunchTarget implements DeployTarget {

  private final int myCloudDeviceConfigurationId;
  @NotNull private final String myCloudDeviceProjectId;

  public CloudDeviceLaunchTarget(int cloudDeviceConfigurationId, @NotNull String cloudDeviceProjectId) {
    myCloudDeviceConfigurationId = cloudDeviceConfigurationId;
    myCloudDeviceProjectId = cloudDeviceProjectId;
  }

  public int getCloudDeviceConfigurationId() {
    return myCloudDeviceConfigurationId;
  }

  @NotNull
  public String getCloudDeviceProjectId() {
    return myCloudDeviceProjectId;
  }
}
