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
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.android.sdklib.AndroidVersion
import com.android.sdklib.ISystemImage
import com.android.tools.idea.adddevicedialog.AndroidVersionSelection
import com.android.tools.idea.adddevicedialog.TableSelectionState
import com.android.tools.idea.avdmanager.skincombobox.DefaultSkin
import com.android.tools.idea.avdmanager.skincombobox.Skin
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabStrip
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.defaultTabStyle
import java.nio.file.Path
import java.util.EnumSet
import java.util.TreeSet

@Composable
internal fun ConfigureDevicePanel(
  configureDevicePanelState: ConfigureDevicePanelState,
  initialSystemImage: ISystemImage?,
  images: ImmutableList<ISystemImage>,
  onDownloadButtonClick: (String) -> Unit,
  onSystemImageTableRowClick: (ISystemImage) -> Unit,
  onImportButtonClick: () -> Unit,
) {
  Column {
    Text(
      "Configure virtual device",
      fontWeight = FontWeight.SemiBold,
      fontSize = LocalTextStyle.current.fontSize * 1.2,
    )
    Tabs(
      configureDevicePanelState,
      initialSystemImage,
      images,
      onDownloadButtonClick,
      onSystemImageTableRowClick,
      onImportButtonClick,
    )
  }
}

@Composable
private fun Tabs(
  configureDevicePanelState: ConfigureDevicePanelState,
  initialSystemImage: ISystemImage?,
  images: ImmutableList<ISystemImage>,
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
    style = JewelTheme.defaultTabStyle
  )

  val servicesSet =
    images.mapTo(EnumSet.noneOf(Services::class.java), ISystemImage::getServices).toImmutableSet()

  val androidVersions = images.map { it.androidVersion }.relevantVersions()

  // TODO: http://b/335494340
  var devicePanelState by remember {
    mutableStateOf(
      if (initialSystemImage == null) {
        DevicePanelState(
          AndroidVersionSelection(
            androidVersions.firstOrNull { !it.isPreview } ?: AndroidVersion.DEFAULT
          ),
          servicesSet.firstOrNull(),
        )
      } else {
        DevicePanelState(
          AndroidVersionSelection(AndroidVersion(initialSystemImage.androidVersion.apiLevel)),
          initialSystemImage.getServices(),
          sdkExtensionSystemImagesVisible = !initialSystemImage.androidVersion.isBaseExtension,
          onlyRecommendedSystemImages = initialSystemImage.isRecommended(),
        )
      }
    )
  }

  val additionalSettingsPanelState = remember {
    AdditionalSettingsPanelState(configureDevicePanelState.device)
  }

  when (selectedTab) {
    Tab.DEVICE ->
      DevicePanel(
        configureDevicePanelState,
        devicePanelState,
        androidVersions,
        servicesSet,
        images,
        onDevicePanelStateChange = { devicePanelState = it },
        onDownloadButtonClick,
        onSystemImageTableRowClick,
        Modifier.padding(Padding.SMALL),
      )
    Tab.ADDITIONAL_SETTINGS ->
      AdditionalSettingsPanel(
        configureDevicePanelState,
        additionalSettingsPanelState,
        onImportButtonClick,
        Modifier.padding(Padding.SMALL),
      )
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

  internal var isValid by mutableStateOf(device.expandedStorage.isValid())
    private set

  internal fun setDeviceName(deviceName: String) {
    device = device.copy(name = deviceName)
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

  internal fun setExpandedStorage(expandedStorage: ExpandedStorage) {
    device = device.copy(expandedStorage = expandedStorage)
    isValid = expandedStorage.isValid()
  }
}

private enum class Tab(val text: String) {
  DEVICE("Device"),
  ADDITIONAL_SETTINGS("Additional settings"),
}
