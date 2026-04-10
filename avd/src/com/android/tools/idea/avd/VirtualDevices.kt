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
package com.android.tools.idea.avd

import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.internal.avd.AvdNames
import com.android.sdklib.internal.avd.uniquifyAvdFolder
import com.android.sdklib.internal.avd.uniquifyAvdName
import com.android.tools.idea.adddevicedialog.FormFactors
import com.android.tools.idea.avdmanager.EnvironmentsUpdater

internal class VirtualDevices(private val avdManager: AvdManager) {
  internal suspend fun add(device: VirtualDevice): AvdInfo {
    val avdBuilder = avdManager.createAvdBuilder(device.deviceProfile)
    avdBuilder.copyFrom(device)
    avdBuilder.avdName = avdManager.uniquifyAvdName(AvdNames.cleanAvdName(device.name))
    avdBuilder.avdFolder = avdManager.uniquifyAvdFolder(avdBuilder.avdName)
    if (device.formFactor == FormFactors.AI_GLASSES) {
      avdBuilder.environment = EnvironmentsUpdater.getInstance().getUpdatedFile("indoor-study-dark.jpg")
    }

    return avdManager.createAvd(avdBuilder)
  }
}
