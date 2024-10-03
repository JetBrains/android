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

import androidx.compose.runtime.Composable
import com.android.sdklib.ISystemImage
import com.android.sdklib.devices.Device
import com.android.sdklib.internal.avd.AvdBuilder
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.internal.avd.AvdNames
import com.android.sdklib.internal.avd.uniquifyAvdFolder
import com.android.sdklib.internal.avd.uniquifyAvdName
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.adddevicedialog.ComposeWizard
import com.android.tools.idea.adddevicedialog.WizardPageScope
import com.android.tools.idea.avdmanager.DeviceManagerConnection
import com.android.tools.idea.avdmanager.skincombobox.NoSkin
import com.android.tools.idea.avdmanager.skincombobox.Skin
import com.android.tools.idea.avdmanager.skincombobox.SkinCollector
import com.android.tools.idea.avdmanager.skincombobox.SkinComboBoxModel
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeAvdManagers
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.withContext

internal class EditVirtualDeviceDialog(
  private val avdInfo: AvdInfo,
  baseDevice: Device,
  private val mode: Mode,
  private val skins: ImmutableList<Skin>,
  private val sdkHandler: AndroidSdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler(),
  private val avdManager: AvdManager = IdeAvdManagers.getAvdManager(sdkHandler),
) {
  enum class Mode {
    EDIT,
    DUPLICATE,
  }

  private val deviceNameValidator =
    DeviceNameValidator.createForAvdManager(
      avdManager,
      currentName = avdInfo.displayName.takeIf { mode == Mode.EDIT },
    )
  private val avdBuilder =
    AvdBuilder.createForExistingDevice(baseDevice, avdInfo).apply {
      if (mode == Mode.DUPLICATE) {
        displayName = deviceNameValidator.uniquify(displayName)
        avdName = avdManager.uniquifyAvdName(AvdNames.cleanAvdName(displayName))
        avdFolder = avdManager.uniquifyAvdFolder(avdName)
      }
    }

  val device = VirtualDevice.withDefaults(baseDevice).copyFrom(avdBuilder)

  @Composable
  fun WizardPageScope.Page() {
    ConfigurationPage(device, avdInfo.systemImage, skins, deviceNameValidator, sdkHandler, ::finish)
  }

  private suspend fun finish(device: VirtualDevice, image: ISystemImage): Boolean {
    avdBuilder.copyFrom(device, image)

    // At this point, builder.avdName still reflects its on-disk location. If the user
    // updated the display name, try to update avdName to reflect the new display name.
    if (avdInfo.displayName != avdBuilder.displayName) {
      avdBuilder.avdName = avdManager.uniquifyAvdName(AvdNames.cleanAvdName(avdBuilder.displayName))
      // We don't update the AVD folder in EDIT mode; the device might be running.
      if (mode == Mode.DUPLICATE) {
        avdBuilder.avdFolder = avdManager.uniquifyAvdFolder(avdBuilder.avdName)
      }
    }
    val newAvdInfo =
      withContext(AndroidDispatchers.diskIoThread) {
        when (mode) {
          Mode.EDIT -> avdManager.editAvd(avdInfo, avdBuilder)
          Mode.DUPLICATE -> avdManager.duplicateAvd(avdInfo, avdBuilder)
        }
      }
    return newAvdInfo != null
  }

  companion object {
    suspend fun show(project: Project?, avdInfo: AvdInfo, mode: Mode): Boolean {
      val skins =
        withContext(AndroidDispatchers.workerThread) {
          SkinComboBoxModel.merge(listOf(NoSkin.INSTANCE), SkinCollector.updateAndCollect())
            .toImmutableList()
        }
      val baseDevice =
        DeviceManagerConnection.getDefaultDeviceManagerConnection()
          .getDevice(avdInfo.deviceName, avdInfo.deviceManufacturer)

      if (baseDevice == null) {
        withContext(AndroidDispatchers.uiThread) {
          Messages.showErrorDialog(
            project,
            "The hardware profile for this device is no longer present. Please create a new device.",
            "Edit Device",
          )
        }
        return false
      }

      val dialog = EditVirtualDeviceDialog(avdInfo, baseDevice, mode, skins)

      return withContext(AndroidDispatchers.uiThread) {
        val wizard = with(dialog) { ComposeWizard(project, "Edit Device") { Page() } }
        wizard.showAndGet()
      }
    }
  }
}
