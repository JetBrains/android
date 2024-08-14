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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.sdklib.devices.Abi
import com.android.sdklib.getFullApiName
import com.android.tools.idea.adddevicedialog.Table
import com.android.tools.idea.adddevicedialog.TableColumn
import com.android.tools.idea.adddevicedialog.TableColumnWidth
import com.android.tools.idea.adddevicedialog.TableSelectionState
import com.android.tools.idea.adddevicedialog.TableTextColumn
import com.android.utils.CpuArchitecture
import com.android.utils.osArchitecture
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.icon.PathIconKey

@Composable
internal fun DevicePanel(
  configureDevicePanelState: ConfigureDevicePanelState,
  devicePanelState: DevicePanelState,
  servicesCollection: ImmutableCollection<Services>,
  images: ImmutableList<SystemImage>,
  onDevicePanelStateChange: (DevicePanelState) -> Unit,
  onDownloadButtonClick: (String) -> Unit,
) {
  Text("Name", Modifier.padding(bottom = Padding.SMALL))

  TextField(
    configureDevicePanelState.device.name,
    onValueChange = configureDevicePanelState::setDeviceName,
    Modifier.padding(bottom = Padding.MEDIUM_LARGE),
  )

  Text("Select System Image", Modifier.padding(bottom = Padding.SMALL_MEDIUM))

  Text(
    "Available system images are displayed based on the service and ABI configuration",
    Modifier.padding(bottom = Padding.SMALL_MEDIUM),
  )

  ServicesDropdown(
    devicePanelState.selectedServices,
    servicesCollection,
    onSelectedServicesChange = {
      onDevicePanelStateChange(devicePanelState.copy(selectedServices = it))
    },
    Modifier.padding(bottom = Padding.MEDIUM_LARGE),
  )

  SystemImageTable(
    images,
    devicePanelState,
    configureDevicePanelState.systemImageTableSelectionState,
    onDownloadButtonClick,
    Modifier.height(150.dp).padding(bottom = Padding.SMALL),
  )

  ShowSdkExtensionSystemImagesCheckbox(
    devicePanelState.sdkExtensionSystemImagesVisible,
    onSdkExtensionSystemImagesVisibleChange = {
      onDevicePanelStateChange(devicePanelState.copy(sdkExtensionSystemImagesVisible = it))
    },
    Modifier.padding(bottom = Padding.SMALL),
  )

  CheckboxRow(
    "Only show system images recommended for my host CPU architecture",
    devicePanelState.onlyForHostCpuArchitectureVisible,
    onCheckedChange = {
      onDevicePanelStateChange(devicePanelState.copy(onlyForHostCpuArchitectureVisible = it))
    },
  )
}

@Composable
private fun ServicesDropdown(
  selectedServices: Services?,
  servicesCollection: ImmutableCollection<Services>,
  onSelectedServicesChange: (Services?) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    Text("Services", Modifier.padding(bottom = Padding.SMALL))

    Row {
      Dropdown(
        Modifier.padding(end = Padding.MEDIUM),
        menuContent = {
          servicesCollection.forEach {
            selectableItem(selectedServices == it, onClick = { onSelectedServicesChange(it) }) {
              Text(it.toString())
            }
          }

          separator()

          selectableItem(selectedServices == null, onClick = { onSelectedServicesChange(null) }) {
            Text("Show All")
          }
        },
      ) {
        Text(selectedServices?.toString() ?: "Show All")
      }

      InfoOutlineIcon(Modifier.align(Alignment.CenterVertically))
    }
  }
}

@Composable
private fun SystemImageTable(
  images: ImmutableList<SystemImage>,
  devicePanelState: DevicePanelState,
  selectionState: TableSelectionState<SystemImage>,
  onDownloadButtonClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val columns =
    listOf(
      TableColumn("", TableColumnWidth.Fixed(16.dp), Comparator.comparing(SystemImage::isRemote)) {
        if (it.isRemote) DownloadButton(onClick = { onDownloadButtonClick(it.path) })
      },
      TableTextColumn("System Image", attribute = SystemImage::name),
      TableTextColumn(
        "Services",
        TableColumnWidth.Fixed(132.dp),
        attribute = { it.services.toString() },
        Comparator.comparing(SystemImage::services),
      ),
      TableTextColumn(
        "API",
        attribute = {
          it.androidVersion.getFullApiName(includeReleaseName = true, includeCodeName = true)
        },
        comparator = Comparator.comparing(SystemImage::androidVersion),
      ),
      TableTextColumn(
        "ABIs",
        TableColumnWidth.Fixed(77.dp),
        attribute = { it.abis.joinToString() },
      ),
      TableTextColumn(
        "Translated ABIs",
        TableColumnWidth.Fixed(77.dp),
        attribute = { it.translatedAbis.joinToString() },
      ),
    )

  Table(columns, images.filter(devicePanelState::test), { it }, modifier, selectionState)
}

internal data class DevicePanelState
internal constructor(
  internal val selectedServices: Services?,
  internal val sdkExtensionSystemImagesVisible: Boolean = false,
  internal val onlyForHostCpuArchitectureVisible: Boolean = true,
) {
  internal fun test(image: SystemImage): Boolean {
    val servicesMatch = selectedServices == null || image.services == selectedServices

    val androidVersionMatches =
      sdkExtensionSystemImagesVisible || image.androidVersion.isBaseExtension

    val abisMatch =
      !onlyForHostCpuArchitectureVisible ||
        image.abis.contains(valueOfCpuArchitecture(osArchitecture))

    return servicesMatch && androidVersionMatches && abisMatch
  }

  private companion object {
    private fun valueOfCpuArchitecture(architecture: CpuArchitecture) =
      when (architecture) {
        CpuArchitecture.X86_64 -> Abi.X86_64
        CpuArchitecture.ARM -> Abi.ARM64_V8A
        else -> throw IllegalArgumentException(architecture.toString())
      }
  }
}

@Composable
private fun DownloadButton(onClick: () -> Unit) {
  IconButton(onClick) { Icon(PathIconKey("expui/general/download.svg"), null) }
}

@Composable
private fun ShowSdkExtensionSystemImagesCheckbox(
  sdkExtensionSystemImagesVisible: Boolean,
  onSdkExtensionSystemImagesVisibleChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(modifier) {
    CheckboxRow(
      "Show SDK extension system images",
      sdkExtensionSystemImagesVisible,
      onSdkExtensionSystemImagesVisibleChange,
      Modifier.padding(end = Padding.MEDIUM),
    )

    InfoOutlineIcon(Modifier.align(Alignment.CenterVertically))
  }
}
