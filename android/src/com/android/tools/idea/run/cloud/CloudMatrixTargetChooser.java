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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A target chooser for picking cloud matrix test deploy targets.
 */
public class CloudMatrixTargetChooser implements TargetChooser {

  private final int myMatrixConfigurationId;
  @NotNull private final String myCloudProjectId;
  @NotNull private final ManualTargetChooser myFallback;

  public CloudMatrixTargetChooser(int matrixConfigurationId, @NotNull String cloudProjectId, @NotNull ManualTargetChooser fallback) {
    myMatrixConfigurationId = matrixConfigurationId;
    myCloudProjectId = cloudProjectId;
    myFallback = fallback;
  }

  @Nullable
  @Override
  public DeployTarget getTarget(@NotNull ConsolePrinter printer, @NotNull DeviceCount deviceCount, boolean debug) {
    if (debug) {
      // It does not make sense to debug a matrix of devices on the cloud. In debug mode, fall back to manual chooser.
      // TODO: Consider making the debug executor unavailable in this case rather than popping the extended chooser dialog.
      return myFallback.getTarget(printer, deviceCount, debug);
    }
    return new CloudMatrixTarget(myMatrixConfigurationId, myCloudProjectId);
  }

  @Override
  public boolean matchesDevice(@NotNull IDevice device) {
    return false;
  }
}
