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
package com.android.tools.idea.adddevicedialog.localavd

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRound
import com.android.sdklib.AndroidVersion
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.deviceprovisioner.Resolution
import com.android.sdklib.devices.Device
import com.android.sdklib.internal.avd.AvdCamera
import com.android.sdklib.internal.avd.EmulatedProperties
import com.android.sdklib.internal.avd.GpuMode
import com.android.tools.idea.adddevicedialog.DeviceProfile
import com.android.tools.idea.adddevicedialog.DeviceSource
import com.android.tools.idea.adddevicedialog.FormFactors
import com.android.tools.idea.adddevicedialog.WizardAction
import com.android.tools.idea.adddevicedialog.WizardPageScope
import com.android.tools.idea.avdmanager.DeviceManagerConnection
import com.android.tools.idea.avdmanager.skincombobox.DefaultSkin
import com.android.tools.idea.avdmanager.skincombobox.NoSkin
import com.android.tools.idea.avdmanager.skincombobox.Skin
import com.android.tools.idea.avdmanager.skincombobox.SkinCollector
import com.android.tools.idea.avdmanager.skincombobox.SkinComboBoxModel
import com.android.tools.idea.sdk.AndroidSdks
import com.google.common.collect.Range
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import java.nio.file.Path
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.toImmutableList

internal class LocalVirtualDeviceSource(
  private val systemImages: ImmutableCollection<SystemImage>,
  private val skins: ImmutableCollection<Skin>,
) : DeviceSource {
  companion object {
    fun create(): LocalVirtualDeviceSource {
      return LocalVirtualDeviceSource(
        SystemImage.getSystemImages().toImmutableList(),
        SkinComboBoxModel.merge(listOf(NoSkin.INSTANCE), SkinCollector.updateAndCollect())
          .toImmutableList(),
      )
    }
  }

  val sdk = AndroidSdks.getInstance().tryToChooseSdkHandler().location.toString()

  override fun WizardPageScope.selectionUpdated(profile: DeviceProfile) {
    nextAction = WizardAction { pushPage { configurationPage(profile) } }
    finishAction = WizardAction.Disabled
  }

  @Composable
  private fun WizardPageScope.configurationPage(device: DeviceProfile) {
    val state =
      remember(device) { LocalAvdConfigurationState(systemImages, skins, device as VirtualDevice) }

    val api = device.apiRange.upperEndpoint()

    ConfigureDevicePanel(
      state.device,
      state.systemImages.filter { it.androidVersion.apiLevel == api }.toImmutableList(),
      state.skins,
      onDeviceChange = { state.device = it },
      onImportButtonClick = {
        // TODO Validate the skin
        val skin =
          FileChooser.chooseFile(
            FileChooserDescriptorFactory.createSingleFolderDescriptor(),
            null, // TODO: add component from CompositionLocal?
            null,
            null,
          )

        if (skin != null) {
          state.importSkin(skin.toNioPath())
        }
      },
    )

    nextAction = WizardAction.Disabled
    finishAction = WizardAction {
      VirtualDevices.add(state.device)
      close()
    }
  }

  override val profiles: List<DeviceProfile> =
    DeviceManagerConnection.getDefaultDeviceManagerConnection().devices.map { it.toVirtualDevice() }

  private fun Device.toVirtualDevice(): VirtualDevice =
    // TODO: Check that these are appropriate defaults
    VirtualDevice(
      apiRange = this.apiRange,
      sdkExtensionLevel = AndroidVersion(apiRange.upperEndpoint()),
      manufacturer = this.manufacturer,
      name = this.displayName,
      resolution =
        Resolution(this.defaultHardware.screen.xDimension, this.defaultHardware.screen.yDimension),
      displayDensity = this.defaultHardware.screen.pixelDensity.dpiValue,
      displayDiagonalLength = this.defaultHardware.screen.diagonalLength,
      isRound = this.defaultHardware.screen.screenRound == ScreenRound.ROUND,
      abis = this.defaultHardware.supportedAbis + this.defaultHardware.translatedAbis,
      formFactor = this.formFactor,
      // TODO: Choose an appropriate skin
      skin = DefaultSkin(Path.of(sdk, "skins", "pixel_6")),
      frontCamera = AvdCamera.EMULATED,
      // TODO We're assuming the emulator supports this feature
      rearCamera = AvdCamera.VIRTUAL_SCENE,
      speed = EmulatedProperties.DEFAULT_NETWORK_SPEED,
      latency = EmulatedProperties.DEFAULT_NETWORK_LATENCY,
      orientation = ScreenOrientation.PORTRAIT,
      defaultBoot = Boot.QUICK,
      internalStorage = StorageCapacity(2_048, StorageCapacity.Unit.MB),
      expandedStorage = Custom(StorageCapacity(512, StorageCapacity.Unit.MB)),
      cpuCoreCount = EmulatedProperties.RECOMMENDED_NUMBER_OF_CORES,
      graphicAcceleration = GpuMode.AUTO,
      simulatedRam = StorageCapacity(2_048, StorageCapacity.Unit.MB),
      vmHeapSize = StorageCapacity(256, StorageCapacity.Unit.MB),
    )

  private val Device.apiRange: Range<Int>
    get() =
      allSoftware
        .map { Range.closed(it.minSdkLevel, it.maxSdkLevel) }
        .reduce(Range<Int>::span)
        .intersection(Range.closed(1, SdkVersionInfo.HIGHEST_KNOWN_API))

  private val Device.formFactor: String
    get() =
      when {
        Device.isWear(this) -> FormFactors.WEAR
        Device.isAutomotive(this) -> FormFactors.AUTO
        Device.isTv(this) -> FormFactors.TV
        Device.isTablet(this) -> FormFactors.TABLET
        else -> FormFactors.PHONE
      }
}
