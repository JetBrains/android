/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.sdklib.internal.avd.AvdBuilder
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.internal.avd.AvdNames
import com.android.sdklib.internal.avd.uniquifyAvdName
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.adddevicedialog.ComposeWizard
import com.android.tools.idea.avdmanager.DeviceManagerConnection
import com.android.tools.idea.avdmanager.skincombobox.NoSkin
import com.android.tools.idea.avdmanager.skincombobox.SkinCollector
import com.android.tools.idea.avdmanager.skincombobox.SkinComboBoxModel
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeAvdManagers
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.withContext

internal class EditVirtualDeviceDialog(
  val project: Project?,
  val sdkHandler: AndroidSdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler(),
  private val avdManager: AvdManager = IdeAvdManagers.getAvdManager(sdkHandler),
) {
  suspend fun show(avdInfo: AvdInfo): Boolean {
    val skins =
      withContext(AndroidDispatchers.workerThread) {
        SkinComboBoxModel.merge(listOf(NoSkin.INSTANCE), SkinCollector.updateAndCollect())
          .toImmutableList()
      }

    return withContext(AndroidDispatchers.uiThread) {
      val baseDevice =
        DeviceManagerConnection.getDefaultDeviceManagerConnection()
          .getDevice(avdInfo.deviceName, avdInfo.deviceManufacturer)
      if (baseDevice == null) {
        // TODO: The old dialog lets you pick a different profile.
        Messages.showErrorDialog(
          project,
          "The hardware profile for this device is no longer present. Please create a new device.",
          "Edit Device",
        )
        return@withContext false
      }

      val builder = AvdBuilder.createForExistingDevice(baseDevice, avdInfo)
      val device = VirtualDevice.withDefaults(baseDevice).copyFrom(builder)

      val wizard =
        ComposeWizard(project, "Edit Device") {
          ConfigurationPage(
            device,
            avdInfo.systemImage,
            skins,
            DeviceNameValidatorImpl(avdManager, currentName = avdInfo.displayName),
            sdkHandler,
          ) { device, image ->
            builder.copyFrom(device, image)

            // At this point, builder.avdName still reflects its on-disk location. If the user
            // updated the display name, try to update avdName to reflect the new display name.
            // We don't update the AVD folder; the device might be running.
            if (avdInfo.displayName != builder.displayName) {
              builder.avdName =
                avdManager.uniquifyAvdName(AvdNames.cleanAvdName(builder.displayName))
            }
            val success =
              withContext(AndroidDispatchers.diskIoThread) {
                avdManager.editAvd(avdInfo, builder) != null
              }
            return@ConfigurationPage success
          }
        }
      return@withContext wizard.showAndGet()
    }
  }
}
