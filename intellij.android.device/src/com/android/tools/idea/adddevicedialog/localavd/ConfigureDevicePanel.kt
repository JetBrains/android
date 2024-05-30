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
package com.android.tools.idea.adddevicedialog.localavd

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.tools.idea.adddevicedialog.TableSelectionState
import com.android.tools.idea.avdmanager.skincombobox.DefaultSkin
import com.android.tools.idea.avdmanager.skincombobox.Skin
import java.nio.file.Path
import java.util.EnumSet
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabStrip
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun ConfigureDevicePanel(
  configureDevicePanelState: ConfigureDevicePanelState,
  images: ImmutableList<SystemImage>,
  systemImageTableSelectionState: TableSelectionState<SystemImage>,
  onDownloadButtonClick: (String) -> Unit,
  onImportButtonClick: () -> Unit,
) {
  Column {
    Text("Configure device")
    Text("Add a device to device manager")

    Tabs(
      configureDevicePanelState,
      images,
      systemImageTableSelectionState,
      onDownloadButtonClick,
      onImportButtonClick,
    )
  }
}

@Composable
private fun Tabs(
  configureDevicePanelState: ConfigureDevicePanelState,
  images: ImmutableList<SystemImage>,
  systemImageTableSelectionState: TableSelectionState<SystemImage>,
  onDownloadButtonClick: (String) -> Unit,
  onImportButtonClick: () -> Unit,
) {
  var selectedTab by remember { mutableStateOf(Tab.DEVICE) }

  TabStrip(
    Tab.values().map { tab ->
      TabData.Default(selectedTab == tab, { Text(tab.text) }, onClick = { selectedTab = tab })
    }
  )

  val servicesSet =
    images.mapTo(EnumSet.noneOf(Services::class.java), SystemImage::services).toImmutableSet()

  // TODO: http://b/335494340
  var devicePanelState by remember { mutableStateOf(DevicePanelState(servicesSet.first())) }

  val additionalSettingsPanelState = remember {
    AdditionalSettingsPanelState(configureDevicePanelState.device)
  }

  when (selectedTab) {
    Tab.DEVICE ->
      DevicePanel(
        configureDevicePanelState.device,
        devicePanelState,
        servicesSet,
        images,
        systemImageTableSelectionState,
        configureDevicePanelState::device::set,
        onStateChange = { devicePanelState = it },
        onDownloadButtonClick,
      )
    Tab.ADDITIONAL_SETTINGS ->
      AdditionalSettingsPanel(
        configureDevicePanelState,
        additionalSettingsPanelState,
        onImportButtonClick,
      )
  }
}

internal class ConfigureDevicePanelState
internal constructor(skins: ImmutableCollection<Skin>, device: VirtualDevice) {
  internal var skins by mutableStateOf(skins)
    private set

  internal var device by mutableStateOf(device)

  internal fun importSkin(path: Path) {
    var skin = skins.find { it.path() == path }

    if (skin == null) {
      skin = DefaultSkin(path)
      skins = (skins + skin).sorted().toImmutableList()
    }

    device = device.copy(skin = skin)
  }
}

private enum class Tab(val text: String) {
  DEVICE("Device"),
  ADDITIONAL_SETTINGS("Additional settings"),
}
