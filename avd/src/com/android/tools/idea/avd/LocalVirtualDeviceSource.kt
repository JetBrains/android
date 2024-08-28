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

import com.android.sdklib.AndroidVersion
import com.android.sdklib.DeviceSystemImageMatcher
import com.android.sdklib.ISystemImage
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.DeviceManager
import com.android.tools.idea.adddevicedialog.DeviceProfile
import com.android.tools.idea.adddevicedialog.DeviceSource
import com.android.tools.idea.adddevicedialog.LoadingState
import com.android.tools.idea.adddevicedialog.WizardAction
import com.android.tools.idea.adddevicedialog.WizardPageScope
import com.android.tools.idea.avdmanager.skincombobox.NoSkin
import com.android.tools.idea.avdmanager.skincombobox.Skin
import com.android.tools.idea.avdmanager.skincombobox.SkinCollector
import com.android.tools.idea.avdmanager.skincombobox.SkinComboBoxModel
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.sdk.DeviceManagers
import java.util.TreeSet
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal class LocalVirtualDeviceSource(private val skins: ImmutableCollection<Skin>) :
  DeviceSource {

  companion object {
    internal fun create(): LocalVirtualDeviceSource {
      val skins =
        SkinComboBoxModel.merge(listOf(NoSkin.INSTANCE), SkinCollector.updateAndCollect())
          .toImmutableList()

      return LocalVirtualDeviceSource(skins)
    }
  }

  override fun WizardPageScope.selectionUpdated(profile: DeviceProfile) {
    nextAction = WizardAction {
      pushPage {
        ConfigurationPage((profile as VirtualDeviceProfile).toVirtualDevice(), null, skins, ::add)
      }
    }
    finishAction = WizardAction.Disabled
  }

  private suspend fun add(device: VirtualDevice, image: ISystemImage): Boolean {
    withContext(AndroidDispatchers.diskIoThread) { VirtualDevices().add(device, image) }
    return true
  }

  override val profiles: Flow<LoadingState<List<VirtualDeviceProfile>>> =
    callbackFlow {
        send(LoadingState.Loading)

        // Note that we don't care about systemImages being updated; the only way it changes is if
        // we download an image, thus converting an image from remote to local, and we only care
        // about API levels here.
        val systemImages =
          ISystemImages.systemImageFlow(AndroidSdks.getInstance().tryToChooseSdkHandler()).first()

        val deviceManager =
          DeviceManagers.getDeviceManager(AndroidSdks.getInstance().tryToChooseSdkHandler())
        fun sendDevices() {
          val profiles =
            deviceManager.getDevices(DeviceManager.ALL_DEVICES).mapNotNull { device ->
              val androidVersions =
                systemImages
                  .filter { DeviceSystemImageMatcher.matches(device, it) }
                  .mapTo(TreeSet()) { it.androidVersion }

              // If there are no system images for a device, we can't create it.
              if (androidVersions.isEmpty()) null
              else device.toVirtualDeviceProfile(androidVersions)
            }

          // Cannot fail due to conflate() below
          trySend(LoadingState.Ready(profiles))
        }

        val listener = DeviceManager.DevicesChangedListener { sendDevices() }
        deviceManager.registerListener(listener)

        sendDevices()

        awaitClose { deviceManager.unregisterListener(listener) }
      }
      .conflate()
}

internal fun Device.toVirtualDeviceProfile(
  androidVersions: Set<AndroidVersion>
): VirtualDeviceProfile =
  VirtualDeviceProfile.Builder()
    .apply { initializeFromDevice(this@toVirtualDeviceProfile, androidVersions) }
    .build()

internal fun VirtualDeviceProfile.toVirtualDevice() =
  VirtualDevice.withDefaults(device).copy(androidVersion = apiLevels.last())
