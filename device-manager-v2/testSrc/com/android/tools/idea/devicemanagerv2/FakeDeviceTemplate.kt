/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.sdklib.deviceprovisioner.DeviceAction
import com.android.sdklib.deviceprovisioner.DeviceActionException
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.sdklib.deviceprovisioner.TemplateActivationAction
import icons.StudioIcons
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Duration

internal class FakeDeviceTemplate(
  override val properties: DeviceProperties,
) : DeviceTemplate {
  constructor(
    name: String
  ) : this(
    DeviceProperties.buildForTest {
      model = name
      icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
    }
  )

  override val id = DeviceId("Fake", true, properties.title)

  override val activationAction =
    object : TemplateActivationAction {
      override suspend fun activate(duration: Duration?) =
        throw DeviceActionException("Device is unavailable")

      override val durationUsed = false
      override val presentation =
        MutableStateFlow(DeviceAction.Presentation("", StudioIcons.Avd.RUN, true))
    }
  override val editAction = null
}
