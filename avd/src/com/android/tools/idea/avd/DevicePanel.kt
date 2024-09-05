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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.sdklib.AndroidVersion
import com.android.sdklib.ISystemImage
import com.android.sdklib.RemoteSystemImage
import com.android.sdklib.getFullApiName
import com.android.tools.idea.adddevicedialog.AndroidVersionSelection
import com.android.tools.idea.adddevicedialog.ApiFilter
import com.android.tools.idea.adddevicedialog.SortOrder
import com.android.tools.idea.adddevicedialog.Table
import com.android.tools.idea.adddevicedialog.TableColumn
import com.android.tools.idea.adddevicedialog.TableColumnWidth
import com.android.tools.idea.adddevicedialog.TableSelectionState
import com.android.tools.idea.adddevicedialog.TableSortState
import com.android.tools.idea.adddevicedialog.TableTextColumn
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.icon.PathIconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
internal fun DevicePanel(
  configureDevicePanelState: ConfigureDevicePanelState,
  devicePanelState: DevicePanelState,
  androidVersions: ImmutableList<AndroidVersion>,
  servicesCollection: ImmutableCollection<Services>,
  images: ImmutableList<ISystemImage>,
  onDevicePanelStateChange: (DevicePanelState) -> Unit,
  onDownloadButtonClick: (String) -> Unit,
  onSystemImageTableRowClick: (ISystemImage) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    Text("Name", Modifier.padding(bottom = Padding.SMALL))

    TextField(
      configureDevicePanelState.device.name,
      onValueChange = configureDevicePanelState::setDeviceName,
      Modifier.padding(bottom = Padding.MEDIUM_LARGE),
    )

    Text(
      "Select system image",
      fontWeight = FontWeight.SemiBold,
      fontSize = LocalTextStyle.current.fontSize * 1.1,
      modifier = Modifier.padding(bottom = Padding.SMALL_MEDIUM),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(Padding.MEDIUM_LARGE)) {
      ApiFilter(
        androidVersions,
        selectedApiLevel = devicePanelState.selectedApiLevel,
        onApiLevelChange = {
          onDevicePanelStateChange(devicePanelState.copy(selectedApiLevel = it))
        },
        Modifier.padding(bottom = Padding.MEDIUM_LARGE),
      )

      ServicesDropdown(
        devicePanelState.selectedServices,
        servicesCollection,
        onSelectedServicesChange = {
          onDevicePanelStateChange(devicePanelState.copy(selectedServices = it))
        },
        Modifier.padding(bottom = Padding.MEDIUM_LARGE),
      )
    }

    Box(Modifier.weight(1f).padding(bottom = Padding.SMALL)) {
      val filteredImages = images.filter(devicePanelState::test)
      if (filteredImages.isEmpty()) {
        EmptyStatePanel(
          "No system images available matching the current set of filters.",
          Modifier.fillMaxSize(),
        )
      } else {
        SystemImageTable(
          filteredImages,
          configureDevicePanelState.systemImageTableSelectionState,
          onDownloadButtonClick,
          onSystemImageTableRowClick,
        )
      }
    }

    ShowSdkExtensionSystemImagesCheckbox(
      devicePanelState.sdkExtensionSystemImagesVisible,
      onSdkExtensionSystemImagesVisibleChange = {
        onDevicePanelStateChange(devicePanelState.copy(sdkExtensionSystemImagesVisible = it))
      },
      Modifier.padding(bottom = Padding.SMALL),
    )

    CheckboxRow(
      "Show only recommended system images",
      devicePanelState.onlyRecommendedSystemImages,
      onCheckedChange = {
        onDevicePanelStateChange(devicePanelState.copy(onlyRecommendedSystemImages = it))
      },
    )
  }
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
  images: List<ISystemImage>,
  selectionState: TableSelectionState<ISystemImage>,
  onDownloadButtonClick: (String) -> Unit,
  onRowClick: (ISystemImage) -> Unit,
  modifier: Modifier = Modifier,
) {
  val sortedImages = images.sortedWith(SystemImageComparator)
  val starredImage by rememberUpdatedState(sortedImages.last().takeIf { it.isRecommended() })
  val starColumn = remember {
    TableColumn("", TableColumnWidth.Fixed(16.dp), comparator = SystemImageComparator) {
      if (it == starredImage) {
        Icon(
          AllIconsKeys.Nodes.Favorite,
          contentDescription = "Recommended",
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
  val columns =
    listOf(
      starColumn,
      TableColumn(
        "",
        TableColumnWidth.Fixed(16.dp),
        Comparator.comparing { it is RemoteSystemImage },
      ) {
        if (it is RemoteSystemImage) {
          DownloadButton(onClick = { onDownloadButtonClick(it.`package`.path) })
        }
      },
      TableTextColumn("System Image", attribute = { it.`package`.displayName }),
      TableTextColumn(
        "API",
        TableColumnWidth.Fixed(250.dp),
        { it.androidVersion.getFullApiName(includeReleaseName = true, includeCodeName = true) },
        Comparator.comparing(ISystemImage::getAndroidVersion),
      ),
    )

  Table(
    columns,
    images,
    { it },
    modifier,
    tableSortState =
      remember {
        TableSortState<ISystemImage>().apply {
          sortColumn = starColumn
          sortOrder = SortOrder.DESCENDING
        }
      },
    tableSelectionState = selectionState,
    onRowClick = onRowClick,
  )
}

internal data class DevicePanelState
internal constructor(
  internal val selectedApiLevel: AndroidVersionSelection,
  internal val selectedServices: Services?,
  internal val sdkExtensionSystemImagesVisible: Boolean = false,
  internal val onlyRecommendedSystemImages: Boolean = true,
) {
  internal fun test(image: ISystemImage): Boolean {
    val servicesMatch = selectedServices == null || image.getServices() == selectedServices

    val androidVersionMatches =
      (sdkExtensionSystemImagesVisible || image.androidVersion.isBaseExtension) &&
        selectedApiLevel.matches(image.androidVersion)

    return servicesMatch &&
      androidVersionMatches &&
      (!onlyRecommendedSystemImages || image.isRecommended())
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

@Composable
private fun EmptyStatePanel(text: String, modifier: Modifier = Modifier) {
  Box(modifier) { Text(text, Modifier.align(Alignment.Center)) }
}
