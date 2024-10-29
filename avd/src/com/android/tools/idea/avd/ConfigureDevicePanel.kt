/*
 * Copyright (C) 2023 The Android Open Source Project
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.sdklib.AndroidVersion
import com.android.sdklib.ISystemImage
import com.android.sdklib.internal.avd.EmulatedProperties
import com.android.tools.idea.adddevicedialog.DeviceDetails
import com.android.tools.idea.adddevicedialog.TableSelectionState
import com.android.tools.idea.avdmanager.skincombobox.DefaultSkin
import com.android.tools.idea.avdmanager.skincombobox.Skin
import java.nio.file.Path
import java.util.EnumSet
import java.util.TreeSet
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabStrip
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.theme.defaultTabStyle

@Composable
internal fun ConfigureDevicePanel(
  configureDevicePanelState: ConfigureDevicePanelState,
  initialSystemImage: ISystemImage?,
  images: SystemImageState,
  deviceNameValidator: DeviceNameValidator,
  onDownloadButtonClick: (String) -> Unit,
  onSystemImageTableRowClick: (ISystemImage) -> Unit,
  onImportButtonClick: () -> Unit,
) {
  Row(Modifier.padding(top = Padding.LARGE)) {
    Column(Modifier.weight(1f)) {
      Column(Modifier.padding(horizontal = Padding.EXTRA_LARGE)) {
        Text(
          "Configure virtual device",
          fontWeight = FontWeight.SemiBold,
          fontSize = LocalTextStyle.current.fontSize * 1.2,
          modifier = Modifier.padding(bottom = Padding.SMALL_MEDIUM),
        )
        Text(
          "Select the system image you'd like to use with the device profile you selected. You can " +
            "also change additional settings that affect the emulated device.",
          color = JewelTheme.globalColors.text.info,
          modifier = Modifier.padding(bottom = Padding.SMALL),
        )
      }
      Tabs(
        configureDevicePanelState,
        initialSystemImage,
        images,
        deviceNameValidator,
        onDownloadButtonClick,
        onSystemImageTableRowClick,
        onImportButtonClick,
      )
    }

    Divider(Orientation.Vertical)
    DeviceDetails(
      configureDevicePanelState.device.device.toVirtualDeviceProfile(),
      Modifier.padding(horizontal = Padding.SMALL_MEDIUM).width(200.dp),
      systemImage =
        configureDevicePanelState.systemImageTableSelectionState.selection?.takeIf {
          configureDevicePanelState.validity.isSystemImageTableSelectionValid
        },
    )
  }
}

@Composable
private fun Tabs(
  configureDevicePanelState: ConfigureDevicePanelState,
  initialSystemImage: ISystemImage?,
  imageState: SystemImageState,
  deviceNameValidator: DeviceNameValidator,
  onDownloadButtonClick: (String) -> Unit,
  onSystemImageTableRowClick: (ISystemImage) -> Unit,
  onImportButtonClick: () -> Unit,
) {
  var selectedTab by remember { mutableStateOf(Tab.DEVICE) }

  TabStrip(
    Tab.values().map { tab ->
      TabData.Default(
        selectedTab == tab,
        { Text(tab.text) },
        onClick = { selectedTab = tab },
        closable = false,
      )
    },
    JewelTheme.defaultTabStyle,
    Modifier.padding(start = Padding.EXTRA_LARGE),
  )

  val servicesSet =
    imageState.images
      .mapTo(EnumSet.noneOf(Services::class.java), ISystemImage::getServices)
      .toImmutableSet()

  val androidVersions = imageState.images.map { it.androidVersion }.relevantVersions()

  val devicePanelState = remember {
    if (initialSystemImage == null) {
      DevicePanelState(
        selectedApi =
          AndroidVersionSelection(
            androidVersions.firstOrNull { !it.isPreview } ?: AndroidVersion.DEFAULT
          ),
        selectedServices = servicesSet.firstOrNull(),
      )
    } else {
      DevicePanelState(
        selectedApi =
          AndroidVersionSelection(AndroidVersion(initialSystemImage.androidVersion.apiLevel)),
        selectedServices = initialSystemImage.getServices(),
        showSdkExtensionSystemImages = !initialSystemImage.androidVersion.isBaseExtension,
        showUnsupportedSystemImages = !initialSystemImage.isSupported(),
      )
    }
  }

  when (selectedTab) {
    Tab.DEVICE ->
      DevicePanel(
        configureDevicePanelState,
        devicePanelState,
        imageState,
        androidVersions,
        servicesSet,
        deviceNameValidator,
        onDownloadButtonClick,
        onSystemImageTableRowClick,
        Modifier.padding(horizontal = Padding.EXTRA_LARGE, vertical = Padding.SMALL_MEDIUM),
      )
    Tab.ADDITIONAL_SETTINGS -> {
      if (configureDevicePanelState.hasPlayStore()) {
        WarningBanner(
          "Some device settings cannot be configured when using a Google Play Store image"
        )
      }

      VerticallyScrollableContainer {
        AdditionalSettingsPanel(
          configureDevicePanelState,
          onImportButtonClick,
          Modifier.padding(horizontal = Padding.EXTRA_LARGE, vertical = Padding.SMALL_MEDIUM),
        )
      }
    }
  }
}

/**
 * Reduce this set of versions to the stable versions, plus any preview versions that are newer than
 * the latest stable, sorted newest first. Strip extension levels.
 */
private fun Collection<AndroidVersion>.relevantVersions(): ImmutableList<AndroidVersion> {
  val (previewVersions, stableVersions) =
    mapTo(TreeSet()) { AndroidVersion(it.apiLevel, it.codename) }.partition { it.isPreview }
  val latestStableVersion = stableVersions.maxOrNull() ?: AndroidVersion.DEFAULT
  return (previewVersions.filter { it > latestStableVersion } + stableVersions)
    .sortedDescending()
    .toImmutableList()
}

internal class ConfigureDevicePanelState
internal constructor(
  device: VirtualDevice,
  skins: ImmutableCollection<Skin>,
  image: ISystemImage?,
) {
  internal var device by mutableStateOf(device)

  internal var skins by mutableStateOf(skins)
    private set

  internal val systemImageTableSelectionState = TableSelectionState(image)
  internal val storageGroupState = StorageGroupState(device)
  internal val emulatedPerformanceGroupState = EmulatedPerformanceGroupState(device)

  internal val isValid
    get() =
      device.internalStorage != null &&
        device.ram != null &&
        device.vmHeapSize != null &&
        validity.isValid

  internal var validity by mutableStateOf(Validity())
    private set

  init {
    setExpandedStorage(device.expandedStorage)
  }

  internal fun hasPlayStore(): Boolean {
    val image = systemImageTableSelectionState.selection
    return if (image == null) false else device.hasPlayStore(image)
  }

  internal fun setDeviceName(deviceName: String) {
    device = device.copy(name = deviceName)
  }

  internal fun setSystemImageSelection(systemImage: ISystemImage) {
    systemImageTableSelectionState.selection = systemImage

    validity =
      validity.copy(isExpandedStorageValid = device.expandedStorage.isValid(hasPlayStore()))

    updatePreferredAbiValidity()
  }

  internal fun setPreferredAbi(preferredAbi: String?) {
    device = device.copy(preferredAbi = preferredAbi)
    updatePreferredAbiValidity()
  }

  private fun updatePreferredAbiValidity() {
    validity =
      validity.copy(
        isPreferredAbiValid =
          device.preferredAbi == null ||
            systemImageTableSelectionState.selection == null ||
            systemImageTableSelectionState.selection.allAbiTypes().contains(device.preferredAbi)
      )
  }

  internal fun setIsSystemImageTableSelectionValid(isSystemImageTableSelectionValid: Boolean) {
    validity = validity.copy(isSystemImageTableSelectionValid = isSystemImageTableSelectionValid)
  }

  internal fun setIsDeviceNameValid(isDeviceNameValid: Boolean) {
    validity = validity.copy(isDeviceNameValid = isDeviceNameValid)
  }

  internal fun setSkin(path: Path) {
    device = device.copy(skin = getSkin(path))
  }

  private fun getSkin(path: Path): Skin {
    var skin = skins.firstOrNull { it.path() == path }

    if (skin == null) {
      skin = DefaultSkin(path)
      skins = (skins + skin).sorted().toImmutableList()
    }

    return skin
  }

  internal fun setExpandedStorage(expandedStorage: ExpandedStorage?) {
    if (expandedStorage != null) {
      device = device.copy(expandedStorage = expandedStorage)
    }

    validity =
      validity.copy(
        isExpandedStorageValid = expandedStorage != null && expandedStorage.isValid(hasPlayStore())
      )
  }

  internal fun resetPlayStoreFields(skin: Path) {
    if (!hasPlayStore()) return

    device =
      device.copy(
        skin = getSkin(skin),
        expandedStorage = Custom(storageGroupState.custom.valid().storageCapacity.withMaxUnit()),
        cpuCoreCount = EmulatedProperties.RECOMMENDED_NUMBER_OF_CORES,
        graphicsMode = GraphicsMode.AUTO,
        ram = EmulatedProperties.defaultRamSize(device.device).toStorageCapacity(),
        vmHeapSize = EmulatedProperties.defaultVmHeapSize(device.device).toStorageCapacity(),
      )
  }
}

internal data class Validity
internal constructor(
  private val isDeviceNameValid: Boolean = true,
  internal val isSystemImageTableSelectionValid: Boolean = true,
  internal val isExpandedStorageValid: Boolean = true,
  val isPreferredAbiValid: Boolean = true,
) {
  internal val isValid
    get() =
      isDeviceNameValid &&
        isSystemImageTableSelectionValid &&
        isExpandedStorageValid &&
        isPreferredAbiValid
}

private enum class Tab(val text: String) {
  DEVICE("Device"),
  ADDITIONAL_SETTINGS("Additional settings"),
}
