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
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.android.tools.idea.adddevicedialog.DeviceDetails
import java.util.EnumSet
import java.util.TreeSet
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
) {
  Row(Modifier.padding(top = Padding.LARGE)) {
    Column(Modifier.weight(1f)) {
      Text(
        "Configure virtual device",
        fontWeight = FontWeight.SemiBold,
        fontSize = LocalTextStyle.current.fontSize * 1.2,
        modifier =
          Modifier.padding(horizontal = Padding.EXTRA_LARGE).padding(bottom = Padding.SMALL_MEDIUM),
      )
      Tabs(
        configureDevicePanelState,
        initialSystemImage,
        images,
        deviceNameValidator,
        onDownloadButtonClick,
        onSystemImageTableRowClick,
      )
    }

    Divider(Orientation.Vertical, Modifier.fillMaxHeight())
    DeviceDetails(
      configureDevicePanelState.device.device.toVirtualDeviceProfile(),
      Modifier.padding(horizontal = Padding.SMALL_MEDIUM).width(200.dp),
      systemImage =
        configureDevicePanelState.systemImageTableSelectionState.selection?.takeIf {
          configureDevicePanelState.isSystemImageTableSelectionValid
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

  val systemImageFilterState = remember {
    if (initialSystemImage == null) {
      SystemImageFilterState(
        selectedApi =
          AndroidVersionSelection(
            androidVersions.firstOrNull { !it.isPreview } ?: AndroidVersion.DEFAULT
          ),
        selectedServices = servicesSet.firstOrNull(),
      )
    } else {
      SystemImageFilterState(
        selectedApi =
          AndroidVersionSelection(initialSystemImage.androidVersion.withBaseExtensionLevel()),
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
        systemImageFilterState,
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
    mapTo(TreeSet()) { it.withBaseExtensionLevel() }.partition { it.isPreview }
  val latestStableVersion = stableVersions.maxOrNull() ?: AndroidVersion.DEFAULT
  return (previewVersions.filter { it > latestStableVersion } + stableVersions)
    .sortedDescending()
    .toImmutableList()
}

private enum class Tab(val text: String) {
  DEVICE("Device"),
  ADDITIONAL_SETTINGS("Additional settings"),
}
