/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.avd

import com.android.adblib.AdbSession
import com.android.sdklib.deviceprovisioner.DeviceIcons
import com.android.sdklib.deviceprovisioner.DeviceProvisionerPlugin
import com.android.sdklib.deviceprovisioner.LocalEmulatorContext
import com.android.sdklib.deviceprovisioner.LocalEmulatorProvisionerPlugin
import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerFactory
import com.intellij.openapi.project.Project
import icons.StudioIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.Clock

/** Builds a LocalEmulatorProvisionerPlugin with its dependencies provided by Studio. */
class LocalEmulatorProvisionerFactory : DeviceProvisionerFactory {
  override val isEnabled: Boolean
    get() = true

  override fun create(coroutineScope: CoroutineScope, project: Project): DeviceProvisionerPlugin =
    create(coroutineScope, AdbLibService.getSession(project), project)

  @Suppress("DEPRECATION")
  fun create(
    coroutineScope: CoroutineScope,
    adbSession: AdbSession,
    project: Project?,
    avdScanner: () -> List<AvdInfo> = {
      AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(true)
    },
  ): DeviceProvisionerPlugin {
    val icons =
      DeviceIcons(
        handheld = StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE,
        wear = StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR,
        tv = StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_TV,
        automotive = StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_CAR,
        headset = StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_HEADSET,
        glasses = StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_GLASS,
      )
    return StudioLocalEmulatorProvisionerPlugin(
      scope = coroutineScope,
      basePlugin =
        LocalEmulatorProvisionerPlugin(
          scope = coroutineScope,
          adbSession = adbSession,
          refreshAvds = avdScanner,
          deviceIcons = icons,
        ),
      context =
        LocalEmulatorContext(
          logger =
            adbSession.host.loggerFactory.createLogger(
              StudioLocalEmulatorProvisionerPlugin::class.java
            ),
          deviceIcons = icons,
          clock = Clock.System,
        ),
      project = project,
    )
  }
}