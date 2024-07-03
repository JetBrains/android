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
package com.android.tools.idea.devicemanagerv2

import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.tools.idea.adblib.AdbLibApplicationService
import com.android.tools.idea.avd.LocalEmulatorProvisionerFactory
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.deviceprovisioner.StudioDefaultDeviceIcons
import com.intellij.openapi.ui.FrameWrapper

class DeviceManagerWelcomeScreenFrame :
  FrameWrapper(
    null,
    "com.android.tools.idea.devicemanagerv2.DeviceManagerWelcomeScreenFrame",
    false,
    "Device Manager",
  ) {
  init {
    closeOnEsc()
    val scope = AndroidCoroutineScope(this)
    val session = AdbLibApplicationService.instance.session
    component =
      DeviceManagerPanel(
        scope,
        DeviceProvisioner.create(
          scope,
          session,
          listOf(LocalEmulatorProvisionerFactory().create(scope, session, null)),
          StudioDefaultDeviceIcons,
        ),
      )
  }
}
