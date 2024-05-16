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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi
import com.android.sdklib.getFullApiName
import com.android.sdklib.getReleaseNameAndDetails
import com.android.tools.idea.adddevicedialog.Table
import com.android.tools.idea.adddevicedialog.TableColumn
import com.android.tools.idea.adddevicedialog.TableColumnWidth
import com.android.tools.idea.adddevicedialog.TableTextColumn
import com.android.utils.CpuArchitecture
import com.android.utils.osArchitecture
import com.intellij.icons.ExpUiIcons
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.separator

@Composable
internal fun DevicePanel(
  device: VirtualDevice,
  state: DevicePanelState,
  servicesCollection: ImmutableCollection<Services>,
  images: ImmutableList<SystemImage>,
  onDeviceChange: (VirtualDevice) -> Unit,
  onStateChange: (DevicePanelState) -> Unit,
  onDownloadButtonClick: (String) -> Unit,
) {
  Text("Name", Modifier.padding(bottom = Padding.SMALL))

  TextField(
    device.name,
    onValueChange = { onDeviceChange(device.copy(name = it)) },
    Modifier.padding(bottom = Padding.MEDIUM_LARGE),
  )

  Text("Select System Image", Modifier.padding(bottom = Padding.SMALL_MEDIUM))

  Text(
    "Available system images are displayed based on the service and ABI configuration",
    Modifier.padding(bottom = Padding.SMALL_MEDIUM),
  )

  ServicesDropdown(
    state.selectedServices,
    servicesCollection,
    onSelectedServicesChange = { onStateChange(state.copy(selectedServices = it)) },
    Modifier.padding(bottom = Padding.MEDIUM_LARGE),
  )

  SystemImageTable(
    images,
    state,
    onDownloadButtonClick,
    Modifier.height(150.dp).padding(bottom = Padding.SMALL),
  )

  ShowSdkExtensionSystemImagesCheckbox(
    state.sdkExtensionSystemImagesVisible,
    onSdkExtensionSystemImagesVisibleChange = {
      onStateChange(state.copy(sdkExtensionSystemImagesVisible = it))
    },
    Modifier.padding(bottom = Padding.SMALL),
  )

  CheckboxRow(
    "Only show system images recommended for my host CPU architecture",
    state.onlyForHostCpuArchitectureVisible,
    onCheckedChange = { onStateChange(state.copy(onlyForHostCpuArchitectureVisible = it)) },
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
  state: DevicePanelState,
  onDownloadButtonClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val columns =
    listOf(
      TableColumn("", TableColumnWidth.Weighted(1F), Comparator.comparing(SystemImage::isRemote)) {
        if (it.isRemote) DownloadButton(onClick = { onDownloadButtonClick(it.path) })
      },
      TableColumn(
        "System Image",
        TableColumnWidth.Weighted(1F),
        Comparator.comparing(SystemImage::androidVersion),
      ) {
        AndroidVersionText(it.androidVersion)
      },
      TableTextColumn(
        "Services",
        attribute = { it.services.toString() },
        comparator = Comparator.comparing(SystemImage::services),
      ),
      TableTextColumn(
        "API",
        attribute = { it.androidVersion.getFullApiName() },
        comparator = Comparator.comparing(SystemImage::androidVersion),
      ),
      TableTextColumn("ABIs", attribute = { it.abis.joinToString() }),
      TableTextColumn("Translated ABIs", attribute = { it.translatedAbis.joinToString() }),
    )

  // TODO: http://b/339247492 - Stop calling distinct
  Table(columns, images.filter(state::test).distinct(), { it }, modifier)
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
  IconButton(onClick) { Icon("expui/general/download.svg", null, ExpUiIcons::class.java) }
}

@Composable
private fun AndroidVersionText(version: AndroidVersion) {
  val nameAndDetails = version.getReleaseNameAndDetails(includeCodeName = true)
  val details = nameAndDetails.details

  if (details == null) {
    Text(nameAndDetails.name)
  } else {
    Row {
      Text(nameAndDetails.name, Modifier.padding(end = Padding.SMALL))
      Text(details, color = retrieveColorOrUnspecified("Component.infoForeground"))
    }
  }
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
