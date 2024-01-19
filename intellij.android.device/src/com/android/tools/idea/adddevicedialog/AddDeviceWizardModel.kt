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
package com.android.tools.idea.adddevicedialog

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.resources.ScreenOrientation
import com.android.sdklib.AndroidVersion
import com.android.sdklib.internal.avd.AvdCamera
import com.android.sdklib.internal.avd.EmulatedProperties
import com.android.sdklib.internal.avd.GpuMode
import com.android.tools.idea.avdmanager.skincombobox.DefaultSkin
import com.android.tools.idea.avdmanager.skincombobox.Skin
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.wizard.model.WizardModel
import java.nio.file.Path
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.toImmutableList

internal class AddDeviceWizardModel
internal constructor(
  internal val systemImages: ImmutableCollection<SystemImage>,
  skins: ImmutableCollection<Skin>,
) : WizardModel() {
  internal var device by initDevice()
  internal var skins by mutableStateOf(skins)

  private fun initDevice(): MutableState<VirtualDevice> {
    // TODO Stop hard coding these defaults and use the values from the selected device definition
    val sdk = AndroidSdks.getInstance().tryToChooseSdkHandler().location.toString()

    return mutableStateOf(
      VirtualDevice(
        "Pixel 7 API 34",
        AndroidVersion(34, null, 7, true),
        DefaultSkin(Path.of(sdk, "skins", "pixel_7")),
        AvdCamera.EMULATED,
        AvdCamera.VIRTUAL_SCENE, // TODO We're assuming the emulator supports this feature
        EmulatedProperties.DEFAULT_NETWORK_SPEED,
        EmulatedProperties.DEFAULT_NETWORK_LATENCY,
        ScreenOrientation.PORTRAIT,
        Boot.QUICK,
        StorageCapacity(2_048, StorageCapacity.Unit.MB),
        EmulatedProperties.RECOMMENDED_NUMBER_OF_CORES,
        GpuMode.AUTO,
      )
    )
  }

  internal fun importSkin(path: Path) {
    var skin = skins.find { it.path() == path }

    if (skin == null) {
      skin = DefaultSkin(path)
      skins = (skins + skin).sorted().toImmutableList()
    }

    device = device.copy(skin = skin)
  }

  override fun handleFinished() {
    VirtualDevices.add(device)
  }
}
