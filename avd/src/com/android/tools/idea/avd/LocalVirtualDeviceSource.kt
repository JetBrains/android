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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.android.tools.idea.adddevicedialog.TableSelectionState
import com.android.tools.idea.adddevicedialog.WizardAction
import com.android.tools.idea.adddevicedialog.WizardPageScope
import com.android.tools.idea.avdmanager.DeviceManagerConnection
import com.android.tools.idea.avdmanager.skincombobox.NoSkin
import com.android.tools.idea.avdmanager.skincombobox.Skin
import com.android.tools.idea.avdmanager.skincombobox.SkinCollector
import com.android.tools.idea.avdmanager.skincombobox.SkinComboBoxModel
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.google.common.collect.Range
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import java.awt.Component
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.android.AndroidPluginDisposable
import org.jetbrains.jewel.bridge.LocalComponent
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

internal class LocalVirtualDeviceSource(
  private val project: Project?,
  systemImages: ImmutableCollection<SystemImage>,
  private val skins: ImmutableCollection<Skin>,
) : DeviceSource {
  private var systemImages by mutableStateOf(systemImages)

  companion object {
    internal fun create(project: Project?): LocalVirtualDeviceSource {
      val images = SystemImage.getSystemImages().toImmutableList()

      val skins =
        SkinComboBoxModel.merge(listOf(NoSkin.INSTANCE), SkinCollector.updateAndCollect())
          .toImmutableList()

      return LocalVirtualDeviceSource(project, images, skins)
    }
  }

  val sdk = AndroidSdks.getInstance().tryToChooseSdkHandler().location.toString()

  override fun WizardPageScope.selectionUpdated(profile: DeviceProfile) {
    nextAction = WizardAction { pushPage { ConfigurationPage(profile) } }
    finishAction = WizardAction.Disabled
  }

  @Composable
  private fun WizardPageScope.ConfigurationPage(device: DeviceProfile) {
    device as VirtualDevice

    val configureDevicePanelState = remember(device) { ConfigureDevicePanelState(skins, device) }
    val images = systemImages.filter { it.matches(device) }.toImmutableList()

    // TODO: http://b/342003916
    val systemImageTableSelectionState = remember { TableSelectionState(images.first()) }

    @OptIn(ExperimentalJewelApi::class) val parent = LocalComponent.current

    ConfigureDevicePanel(
      configureDevicePanelState,
      images,
      systemImageTableSelectionState,
      onDownloadButtonClick = { downloadSystemImage(parent, it) },
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
          configureDevicePanelState.importSkin(skin.toNioPath())
        }
      },
    )

    nextAction = WizardAction.Disabled

    finishAction =
      add(configureDevicePanelState.device, systemImageTableSelectionState.selection!!, parent)
  }

  private fun add(device: VirtualDevice, image: SystemImage, parent: Component) = WizardAction {
    if (image.isRemote) {
      val yes = MessageDialogBuilder.yesNo("Confirm Download", "Download $image?").ask(parent)

      if (!yes) {
        return@WizardAction
      }

      val finish = downloadSystemImage(parent, image.path)

      if (!finish) {
        return@WizardAction
      }
    }

    VirtualDevices().add(device, image)
    close()
  }

  private fun downloadSystemImage(parent: Component, path: String): Boolean {
    val dialog = SdkQuickfixUtils.createDialogForPaths(parent, listOf(path), false)

    if (dialog == null) {
      thisLogger().warn("Could not create the SDK Quickfix Installation dialog")
      return false
    }

    val finish = dialog.showAndGet()

    if (!finish) {
      return false
    }

    val parentDisposable =
      if (project == null) {
        AndroidPluginDisposable.getApplicationInstance()
      } else {
        AndroidPluginDisposable.getProjectInstance(project)
      }

    AndroidCoroutineScope(parentDisposable, AndroidDispatchers.uiThread).launch {
      systemImages =
        withContext(AndroidDispatchers.workerThread) {
          SystemImage.getSystemImages().toImmutableList()
        }
    }

    return true
  }

  override val profiles: List<DeviceProfile> =
    DeviceManagerConnection.getDefaultDeviceManagerConnection().devices.map { it.toVirtualDevice() }
}

internal fun Device.toVirtualDevice(): VirtualDevice =
  // TODO: Check that these are appropriate defaults
  VirtualDevice(
    deviceId = id,
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
    // TODO(b/335267252): Set the skin appropriately.
    skin = NoSkin.INSTANCE,
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
