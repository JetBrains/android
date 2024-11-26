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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.annotations.concurrency.UiThread
import com.android.sdklib.DeviceSystemImageMatcher
import com.android.sdklib.ISystemImage
import com.android.sdklib.RemoteSystemImage
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.adtui.compose.catchAndShowErrors
import com.android.tools.adtui.device.DeviceArtDescriptor
import com.android.tools.idea.adddevicedialog.EmptyStatePanel
import com.android.tools.idea.adddevicedialog.LocalFileSystem
import com.android.tools.idea.adddevicedialog.TableSelectionState
import com.android.tools.idea.adddevicedialog.WizardAction
import com.android.tools.idea.adddevicedialog.WizardDialogScope
import com.android.tools.idea.adddevicedialog.WizardPageScope
import com.android.tools.idea.avdmanager.SkinUtils
import com.android.tools.idea.avdmanager.skincombobox.Skin
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import java.awt.Component
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.bridge.LocalComponent
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

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
  systemImageStateFlow: StateFlow<SystemImageState>,
  skins: ImmutableCollection<Skin>,
  deviceNameValidator: DeviceNameValidator,
  sdkHandler: AndroidSdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler(),
  finish: @UiThread suspend (VirtualDevice, ISystemImage) -> Boolean,
) {
  val systemImageState by systemImageStateFlow.collectAsState()

  // Wait a bit for remote images to arrive before we proceed, so that we make our initial
  // system image selection based on the full list, if possible.
  val isTimedOut by
    produceState(false) {
      delay(1.seconds)
      value = true
    }
  if (
    !systemImageState.hasLocal ||
      (!isTimedOut && !systemImageState.hasRemote && systemImageState.error == null)
  ) {
    EmptyStatePanel("Loading system images...", Modifier.fillMaxSize())
    return
  }

  val filteredImageState =
    systemImageState.copy(
      images = systemImageState.images.filter { matches(device, it) }.toImmutableList()
    )
  if (filteredImageState.images.isEmpty()) {
    EmptyStatePanel("No system images available.", Modifier.fillMaxSize())
    return
  }

  val fileSystem = LocalFileSystem.current
  val state =
    remember(device) {
      if (image == null) {
        val state =
          ConfigureDevicePanelState(
            device,
            skins,
            filteredImageState.images.sortedWith(SystemImageComparator).last().takeIf {
              it.isSupported()
            },
            fileSystem,
          )

        state.setSkin(resolveDefaultSkin(device, sdkHandler, fileSystem))
        state
      } else {
        val copy =
          if (device.expandedStorage is Custom) {
            device.copy(existingCustomExpandedStorage = device.expandedStorage.withMaxUnit())
          } else {
            device
          }

        ConfigureDevicePanelState(copy, skins, image, fileSystem)
      }
    }

  updateSystemImageSelection(state.systemImageTableSelectionState, filteredImageState)

  @OptIn(ExperimentalJewelApi::class) val parent = LocalComponent.current

  val coroutineScope = rememberCoroutineScope()
  val component = LocalComponent.current

  Column {
    if (!state.validity.isPreferredAbiValid) {
      ErrorBanner(
        "Preferred ABI \"${state.device.preferredAbi}\" is not available with selected system image",
        Modifier.padding(vertical = 6.dp),
      )
    }

    ConfigureDevicePanel(
      state,
      image,
      filteredImageState,
      deviceNameValidator,
      onDownloadButtonClick = { coroutineScope.launch { downloadSystemImage(parent, it) } },
      onSystemImageTableRowClick = {
        state.setSystemImageSelection(it)
        state.setSkin(resolve(sdkHandler, state.device.skin.path(), it.skins))
      },
    )
  }
  nextAction = WizardAction.Disabled

  finishAction =
    if (state.isValid) {
      WizardAction {
        runWithModalProgressBlocking(
          ModalTaskOwner.component(component),
          "Creating AVD",
          TaskCancellation.nonCancellable(),
        ) {
          state.resetPlayStoreFields(resolveDefaultSkin(state.device, sdkHandler, fileSystem))

          withContext(AndroidDispatchers.uiThread) {
            finish(
              state.device,
              state.systemImageTableSelectionState.selection!!,
              parent,
              finish,
              sdkHandler,
            )
          }
        }
      }
    } else {
      WizardAction.Disabled
    }
}

/**
 * Updates the system image selection based on the currently-available images: if a
 * RemoteSystemImage is selected, and is downloaded, it becomes a SystemImage, and we should select
 * it.
 */
private fun updateSystemImageSelection(
  state: TableSelectionState<ISystemImage>,
  images: SystemImageState,
) {
  val selectedImage = state.selection
  if (selectedImage is RemoteSystemImage && selectedImage !in images.images) {
    images.images
      .find { it.`package`.path == selectedImage.`package`.path }
      ?.let { state.selection = it }
  }
}

private fun resolveDefaultSkin(
  device: VirtualDevice,
  sdkHandler: AndroidSdkHandler,
  fileSystem: FileSystem,
): Path {
  val skin = device.device.defaultHardware.skinFile

  return resolve(
    sdkHandler,
    if (skin == null) SkinUtils.noSkin(fileSystem) else fileSystem.getPath(skin.path),
    emptyList(),
  )
}

private suspend fun WizardDialogScope.finish(
  device: VirtualDevice,
  image: ISystemImage,
  parent: Component,
  finish: @UiThread suspend (VirtualDevice, ISystemImage) -> Boolean,
  sdkHandler: AndroidSdkHandler,
) {
  if (ensureSystemImageIsPresent(image, parent)) {
    catchAndShowErrors<AvdConfigurationPage>(
      parent,
      message = "An error occurred while creating the AVD. See idea.log for details.",
      title = "Error Creating AVD",
    ) {
      if (finish(device, sdkHandler.toLocalImage(image))) {
        close()
      }
    }
  }
}

/**
 * Prompts the user to download the system image if it is not present.
 *
 * @return true if the system image is present (either because it was already there or it was
 *   downloaded successfully).
 */
@UiThread
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

@UiThread
private fun downloadSystemImage(parent: Component, path: String): Boolean {
  catchAndShowErrors<AvdConfigurationPage>(
    parent = parent,
    message = "An unexpected error occurred downloading the system image. See idea.log for details.",
  ) {
    val dialog = SdkQuickfixUtils.createDialogForPaths(parent, listOf(path), false)

    if (dialog == null) {
      logger<AvdConfigurationPage>().warn("Could not create the SDK Quickfix Installation dialog")
      return false
    }

    return dialog.showAndGet()
  }
  return false
}

object AvdConfigurationPage
