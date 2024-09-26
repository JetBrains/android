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
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.adtui.device.DeviceArtDescriptor
import com.android.tools.idea.adddevicedialog.LocalFileSystem
import com.android.tools.idea.adddevicedialog.LocalProject
import com.android.tools.idea.adddevicedialog.WizardAction
import com.android.tools.idea.adddevicedialog.WizardDialogScope
import com.android.tools.idea.adddevicedialog.WizardPageScope
import com.android.tools.idea.avdmanager.SkinUtils
import com.android.tools.idea.avdmanager.skincombobox.Skin
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import java.awt.Component
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.jetbrains.jewel.bridge.LocalComponent
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.Text

private fun matches(device: VirtualDevice, image: ISystemImage): Boolean {
  return image.androidVersion.apiLevel >= SdkVersionInfo.LOWEST_ACTIVE_API &&
    DeviceSystemImageMatcher.matches(device.device, image)
}

private fun resolve(sdkHandler: AndroidSdkHandler, deviceSkin: Path, imageSkins: Iterable<Path>) =
  DeviceSkinResolver.resolve(
      deviceSkin,
      imageSkins,
      sdkHandler.location,
      DeviceArtDescriptor.getBundledDescriptorsFolder()?.toPath(),
    )
    .takeIf { Files.exists(it) } ?: SkinUtils.noSkin()

@Composable
internal fun WizardPageScope.ConfigurationPage(
  device: VirtualDevice,
  image: ISystemImage?,
  skins: ImmutableCollection<Skin>,
  deviceNameValidator: DeviceNameValidator,
  sdkHandler: AndroidSdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler(),
  finish: suspend (VirtualDevice, ISystemImage) -> Boolean,
) {
  val project = LocalProject.current
  val imagesState: SystemImageState by
    remember { ISystemImages.systemImageFlow(sdkHandler, project) }
      .collectAsState(SystemImageState.INITIAL)
  val images = imagesState.images.filter { matches(device, it) }.toImmutableList()
  if (!imagesState.hasLocal || (images.isEmpty() && !imagesState.hasRemote)) {
    Box(Modifier.fillMaxSize()) {
      Text("Loading system images...", modifier = Modifier.align(Alignment.Center))
    }
    return
  }
  if (images.isEmpty()) {
    Box(Modifier.fillMaxSize()) {
      Text("No system images available.", modifier = Modifier.align(Alignment.Center))
    }
    return
  }

  val fileSystem = LocalFileSystem.current
  val state =
    remember(device) {
      if (image == null) {
        // Adding a device
        val state =
          ConfigureDevicePanelState(
            device,
            skins,
            images.sortedWith(SystemImageComparator).last().takeIf { it.isRecommended() },
          )

        val skin = device.device.defaultHardware.skinFile
        state.setSkin(
          resolve(
            sdkHandler,
            if (skin == null) SkinUtils.noSkin(fileSystem) else fileSystem.getPath(skin.path),
            emptyList(),
          )
        )

        state
      } else {
        // Editing a device
        ConfigureDevicePanelState(device, skins, image)
      }
    }

  @OptIn(ExperimentalJewelApi::class) val parent = LocalComponent.current

  val coroutineScope = rememberCoroutineScope()

  ConfigureDevicePanel(
    state,
    image,
    images,
    deviceNameValidator,
    onDownloadButtonClick = { coroutineScope.launch { downloadSystemImage(parent, it) } },
    onSystemImageTableRowClick = {
      state.systemImageTableSelectionState.selection = it
      state.setSkin(resolve(sdkHandler, state.device.skin.path(), it.skins))
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

      if (skin != null) state.setSkin(skin.toNioPath())
    },
  )

  nextAction = WizardAction.Disabled

  finishAction =
    if (state.validity.isValid) {
      WizardAction {
        coroutineScope.launch {
          finish(
            state.device,
            state.systemImageTableSelectionState.selection!!,
            parent,
            finish,
            sdkHandler,
          )
        }
      }
    } else {
      WizardAction.Disabled
    }
}

private suspend fun WizardDialogScope.finish(
  device: VirtualDevice,
  image: ISystemImage,
  parent: Component,
  finish: suspend (VirtualDevice, ISystemImage) -> Boolean,
  sdkHandler: AndroidSdkHandler,
) {
  if (ensureSystemImageIsPresent(image, parent)) {
    try {
      if (finish(device, sdkHandler.toLocalImage(image))) {
        close()
      }
    } catch (e: Exception) {
      logger<LocalVirtualDeviceSource>().error(e)
      Messages.showErrorDialog(
        parent,
        "An error occurred while creating the AVD. See idea.log for details.",
        "Error Creating AVD",
      )
    }
  }
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

// TODO: http://b/367394413 - This is a hack. Find a better way.
private fun AndroidSdkHandler.toLocalImage(image: ISystemImage): ISystemImage {
  if (image !is RemoteSystemImage) return image

  val indicator = StudioLoggerProgressIndicator(AvdConfigurationPage::class.java)

  val images =
    getSystemImageManager(indicator).imageMap.get(getLocalPackage(image.`package`.path, indicator))

  if (images.size > 1) {
    logger<AvdConfigurationPage>()
      .warn("Multiple images for ${image.`package`.path}. Returning the first.")
  }

  return images.first()
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
