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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.android.sdklib.AndroidVersion
import com.android.sdklib.DeviceSystemImageMatcher
import com.android.sdklib.ISystemImage
import com.android.sdklib.RemoteSystemImage
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.DeviceManager
import com.android.tools.adtui.device.DeviceArtDescriptor
import com.android.tools.idea.adddevicedialog.DeviceProfile
import com.android.tools.idea.adddevicedialog.DeviceSource
import com.android.tools.idea.adddevicedialog.LoadingState
import com.android.tools.idea.adddevicedialog.WizardAction
import com.android.tools.idea.adddevicedialog.WizardPageScope
import com.android.tools.idea.avdmanager.SkinUtils
import com.android.tools.idea.avdmanager.skincombobox.NoSkin
import com.android.tools.idea.avdmanager.skincombobox.Skin
import com.android.tools.idea.avdmanager.skincombobox.SkinCollector
import com.android.tools.idea.avdmanager.skincombobox.SkinComboBoxModel
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.android.tools.sdk.DeviceManagers
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.MessageDialogBuilder
import java.awt.Component
import java.nio.file.Path
import java.util.TreeSet
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.bridge.LocalComponent
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

internal class LocalVirtualDeviceSource(
  systemImages: ImmutableCollection<ISystemImage>,
  private val skins: ImmutableCollection<Skin>,
) : DeviceSource {
  private var systemImages by mutableStateOf(systemImages)

  companion object {
    internal fun create(): LocalVirtualDeviceSource {
      val skins =
        SkinComboBoxModel.merge(listOf(NoSkin.INSTANCE), SkinCollector.updateAndCollect())
          .toImmutableList()

      return LocalVirtualDeviceSource(ISystemImages.get(), skins)
    }

    private fun matches(device: VirtualDevice, image: ISystemImage): Boolean {
      return image.androidVersion.apiLevel >= SdkVersionInfo.LOWEST_ACTIVE_API &&
        DeviceSystemImageMatcher.matches(device.device, image)
    }

    private fun resolve(deviceSkin: Path, imageSkins: Iterable<Path>) =
      DeviceSkinResolver.resolve(
        deviceSkin,
        imageSkins,
        AndroidSdks.getInstance().tryToChooseSdkHandler().location,
        DeviceArtDescriptor.getBundledDescriptorsFolder()?.toPath(),
      )
  }

  override fun WizardPageScope.selectionUpdated(profile: DeviceProfile) {
    nextAction = WizardAction {
      pushPage {
        ConfigurationPage((profile as VirtualDeviceProfile).toVirtualDevice(), null, ::add)
      }
    }
    finishAction = WizardAction.Disabled
  }

  @Composable
  internal fun WizardPageScope.ConfigurationPage(
    device: VirtualDevice,
    image: ISystemImage?,
    finish: suspend (VirtualDevice, ISystemImage) -> Boolean,
  ) {
    val images = systemImages.filter { matches(device, it) }.toImmutableList()

    // TODO: http://b/342003916
    val configureDevicePanelState = remember(device) { configureDevicePanelState(device, image) }

    @OptIn(ExperimentalJewelApi::class) val parent = LocalComponent.current

    val coroutineScope = rememberCoroutineScope()

    ConfigureDevicePanel(
      configureDevicePanelState,
      images,
      onDownloadButtonClick = { coroutineScope.launch { downloadSystemImage(parent, it) } },
      onSystemImageTableRowClick = {
        configureDevicePanelState.systemImageTableSelectionState.selection = it

        val skin = resolve(configureDevicePanelState.device.skin.path(), it.skins)
        configureDevicePanelState.setSkin(skin)
      },
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
          configureDevicePanelState.setSkin(skin.toNioPath())
        }
      },
    )

    nextAction = WizardAction.Disabled

    finishAction = WizardAction {
      coroutineScope.launch {
        val selectedDevice = configureDevicePanelState.device
        val selectedImage = configureDevicePanelState.systemImageTableSelectionState.selection!!

        if (ensureSystemImageIsPresent(selectedImage, parent)) {
          if (finish(selectedDevice, selectedImage)) {
            close()
          }
        }
      }
    }
  }

  private fun configureDevicePanelState(
    device: VirtualDevice,
    image: ISystemImage?,
  ): ConfigureDevicePanelState {
    val state = ConfigureDevicePanelState(device, skins, image)

    if (image == null) {
      val skin = device.device.defaultHardware.skinFile
      state.setSkin(resolve(if (skin == null) SkinUtils.noSkin() else skin.toPath(), emptyList()))
    }

    return state
  }

  private suspend fun add(device: VirtualDevice, image: ISystemImage): Boolean {
    withContext(AndroidDispatchers.diskIoThread) { VirtualDevices().add(device, image) }
    return true
  }

  /**
   * Prompts the user to download the system image if it is not present.
   *
   * @return true if the system image is present (either because it was already there or it was
   *   downloaded successfully).
   */
  private suspend fun ensureSystemImageIsPresent(image: ISystemImage, parent: Component): Boolean {
    if (image is RemoteSystemImage) {
      val yes = MessageDialogBuilder.yesNo("Confirm Download", "Download $image?").ask(parent)

      if (!yes) {
        return false
      }

      val finish = downloadSystemImage(parent, image.`package`.path)

      if (!finish) {
        return false
      }
    }
    return true
  }

  private suspend fun downloadSystemImage(parent: Component, path: String): Boolean {
    val dialog = SdkQuickfixUtils.createDialogForPaths(parent, listOf(path), false)

    if (dialog == null) {
      thisLogger().warn("Could not create the SDK Quickfix Installation dialog")
      return false
    }

    val finish = dialog.showAndGet()

    if (!finish) {
      return false
    }

    withContext(AndroidDispatchers.workerThread) { systemImages = ISystemImages.get() }
    return true
  }

  override val profiles: Flow<LoadingState<List<VirtualDeviceProfile>>> =
    callbackFlow {
        send(LoadingState.Loading)

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
