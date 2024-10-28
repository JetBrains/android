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

import com.android.sdklib.ISystemImage
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.internal.avd.AvdNames
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.adddevicedialog.LoadingState
import com.android.tools.idea.adddevicedialog.WizardAction
import com.android.tools.idea.adddevicedialog.WizardPageScope
import com.android.tools.idea.avdmanager.skincombobox.NoSkin
import com.android.tools.idea.avdmanager.skincombobox.Skin
import com.android.tools.idea.avdmanager.skincombobox.SkinCollector
import com.android.tools.idea.avdmanager.skincombobox.SkinComboBoxModel
import com.android.tools.idea.avdmanager.ui.NameComparator
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeAvdManagers
import com.android.tools.sdk.DeviceManagers
import com.intellij.openapi.components.service
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

internal class LocalVirtualDeviceSource(
  private val skins: ImmutableCollection<Skin>,
  val sdkHandler: AndroidSdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler(),
  private val avdManager: AvdManager = IdeAvdManagers.getAvdManager(sdkHandler),
  val systemImageStateFlow: StateFlow<SystemImageState> =
    service<SystemImageStateService>().systemImageStateFlow,
) {

  companion object {
    internal fun create(): LocalVirtualDeviceSource {
      val skins =
        SkinComboBoxModel.merge(listOf(NoSkin.INSTANCE), SkinCollector.updateAndCollect())
          .toImmutableList()

      return LocalVirtualDeviceSource(skins)
    }
  }

  fun WizardPageScope.selectionUpdated(profile: VirtualDeviceProfile) {
    nextAction = WizardAction {
      pushPage {
        leftSideButtons = emptyList()

        val deviceNameValidator = DeviceNameValidator.createForAvdManager(avdManager)
        ConfigurationPage(
          VirtualDevice.withDefaults(profile.device)
            .copy(name = deviceNameValidator.uniquify(AvdNames.cleanDisplayName(profile.name))),
          null,
          systemImageStateFlow,
          skins,
          deviceNameValidator,
          sdkHandler,
          ::add,
        )
      }
    }
    finishAction = WizardAction.Disabled
  }

  private suspend fun add(device: VirtualDevice, image: ISystemImage): Boolean {
    withContext(AndroidDispatchers.diskIoThread) { VirtualDevices(avdManager).add(device, image) }
    return true
  }

  val profiles: Flow<LoadingState<List<VirtualDeviceProfile>>> =
    callbackFlow {
        send(LoadingState.Loading)

        val deviceManager = DeviceManagers.getDeviceManager(sdkHandler)

        fun sendDevices() {
          val profiles =
            deviceManager.getDevices(DeviceManager.ALL_DEVICES).mapTo(mutableListOf()) {
              it.toVirtualDeviceProfile()
            }
          profiles.sortWith(compareBy(NameComparator()) { it.device })

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

internal fun Device.toVirtualDeviceProfile(): VirtualDeviceProfile =
  VirtualDeviceProfile.Builder().apply { initializeFromDevice(this@toVirtualDeviceProfile) }.build()
