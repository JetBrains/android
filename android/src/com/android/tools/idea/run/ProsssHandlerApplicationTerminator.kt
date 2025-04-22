/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.run

import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.tools.deployer.DeployerApplicationTerminator
import com.android.tools.idea.execution.common.ApplicationTerminator
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.progress.ProgressIndicator

class ProcessHandlerApplicationTerminator(indicator: ProgressIndicator, devices: List<IDevice>, appId: String) :
  DeployerApplicationTerminator(
    devices,
    appId,
    { targetDevice, targetAppId ->
      if (StudioFlags.INSTALL_USE_PM_TERMINATE.get() && targetDevice.version.isAtLeast(AndroidVersion.VersionCodes.TIRAMISU)) {
        indicator.text = "Terminating $targetAppId"
        ApplicationTerminator(targetDevice, targetAppId).killApp()
        indicator.text = "$targetAppId }is terminated"
      }
    },
  )
