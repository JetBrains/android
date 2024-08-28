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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.sdklib.DeviceSystemImageMatcher
import com.android.sdklib.ISystemImage
import com.android.sdklib.RemoteSystemImage
import com.android.sdklib.SdkVersionInfo
import com.android.tools.adtui.device.DeviceArtDescriptor
import com.android.tools.idea.adddevicedialog.LoadingState
import com.android.tools.idea.adddevicedialog.WizardAction
import com.android.tools.idea.adddevicedialog.WizardPageScope
import com.android.tools.idea.avdmanager.SkinUtils
import com.android.tools.idea.avdmanager.skincombobox.Skin
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.MessageDialogBuilder
import java.awt.Component
import java.nio.file.Path
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.jewel.bridge.LocalComponent
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.Text

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

@Composable
internal fun WizardPageScope.ConfigurationPage(
  device: VirtualDevice,
  image: ISystemImage?,
  skins: ImmutableCollection<Skin>,
  finish: suspend (VirtualDevice, ISystemImage) -> Boolean,
) {
  val allImages: LoadingState<List<ISystemImage>> by
    remember {
        ISystemImages.systemImageFlow(AndroidSdks.getInstance().tryToChooseSdkHandler()).map {
          LoadingState.Ready(it)
        }
      }
      .collectAsState(LoadingState.Loading)
  val readyImages =
    allImages as? LoadingState.Ready<List<ISystemImage>>
      ?: run {
        Box(Modifier.fillMaxSize()) {
          Text("Loading system images...", modifier = Modifier.align(Alignment.Center))
        }
        return
      }
  val images = readyImages.value.filter { matches(device, it) }.toImmutableList()
  if (images.isEmpty()) {
    Box(Modifier.fillMaxSize()) {
      Text("No system images available.", modifier = Modifier.align(Alignment.Center))
    }
  }

  // TODO: http://b/342003916
  val configureDevicePanelState =
    remember(device) { configureDevicePanelState(device, skins, image) }

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
  skins: ImmutableCollection<Skin>,
  image: ISystemImage?,
): ConfigureDevicePanelState {
  val state = ConfigureDevicePanelState(device, skins, image)

  if (image == null) {
    val skin = device.device.defaultHardware.skinFile
    state.setSkin(resolve(if (skin == null) SkinUtils.noSkin() else skin.toPath(), emptyList()))
  }

  return state
}

/**
 * Prompts the user to download the system image if it is not present.
 *
 * @return true if the system image is present (either because it was already there or it was
 *   downloaded successfully).
 */
private fun ensureSystemImageIsPresent(image: ISystemImage, parent: Component): Boolean {
  if (image is RemoteSystemImage) {
    val yes = MessageDialogBuilder.yesNo("Confirm Download", "Download $image?").ask(parent)

    if (!yes) {
      return false
    }

    return downloadSystemImage(parent, image.`package`.path)
  }
  return true
}

private fun downloadSystemImage(parent: Component, path: String): Boolean {
  val dialog = SdkQuickfixUtils.createDialogForPaths(parent, listOf(path), false)

  if (dialog == null) {
    logger<AvdConfigurationPage>().warn("Could not create the SDK Quickfix Installation dialog")
    return false
  }

  return dialog.showAndGet()
}

object AvdConfigurationPage
