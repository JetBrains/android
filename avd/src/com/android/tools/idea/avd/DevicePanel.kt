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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.sdklib.AndroidVersion
import com.android.sdklib.ISystemImage
import com.android.sdklib.RemoteSystemImage
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
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DevicePanel(
  configureDevicePanelState: ConfigureDevicePanelState,
  devicePanelState: DevicePanelState,
  androidVersions: ImmutableList<AndroidVersion>,
  servicesCollection: ImmutableCollection<Services>,
  deviceNameValidator: DeviceNameValidator,
  onDownloadButtonClick: (String) -> Unit,
  onSystemImageTableRowClick: (ISystemImage) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    Text("Name", Modifier.padding(bottom = Padding.SMALL))

    var nameError by remember { mutableStateOf<String?>(null) }
    val nameState = rememberTextFieldState(configureDevicePanelState.device.name)
    LaunchedEffect(Unit) {
      snapshotFlow { nameState.text.toString() }
        .collect {
          configureDevicePanelState.setDeviceName(it)
          nameError = deviceNameValidator.validate(it)
          configureDevicePanelState.setIsDeviceNameValid(nameError == null)
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(Padding.MEDIUM_LARGE)) {
      ErrorTooltip(nameError) {
        TextField(
          nameState,
          Modifier.padding(bottom = Padding.MEDIUM_LARGE).alignByBaseline(),
          outline = if (nameError == null) Outline.None else Outline.Error,
        )
      }
    }

    Text(
      "Select system image",
      fontWeight = FontWeight.SemiBold,
      fontSize = LocalTextStyle.current.fontSize * 1.1,
      modifier = Modifier.padding(bottom = Padding.SMALL_MEDIUM),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(Padding.MEDIUM_LARGE)) {
      ApiFilter(
        androidVersions,
        devicePanelState.selectedApi,
        devicePanelState::setSelectedApi,
        Modifier.padding(bottom = Padding.MEDIUM_LARGE),
      )

      ServicesDropdown(
        devicePanelState.selectedServices,
        servicesCollection,
        devicePanelState::setSelectedServices,
        Modifier.padding(bottom = Padding.MEDIUM_LARGE),
      )
    }

    Box(Modifier.weight(1f).padding(bottom = Padding.SMALL)) {
      if (devicePanelState.filteredSystemImages.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
          Text(
            "No system images available matching the current set of filters.",
            Modifier.align(Alignment.Center),
          )
        }
      } else {
        SystemImageTable(
          devicePanelState.filteredSystemImages,
          configureDevicePanelState.systemImageTableSelectionState,
          configureDevicePanelState::setIsSystemImageTableSelectionValid,
          onDownloadButtonClick,
          onSystemImageTableRowClick,
        )
      }
    }

    ShowSdkExtensionSystemImagesCheckbox(
      devicePanelState.showSdkExtensionSystemImages,
      devicePanelState::setShowSdkExtensionSystemImages,
      Modifier.padding(bottom = Padding.SMALL),
    )

    CheckboxRow(
      "Show only recommended system images",
      devicePanelState.showOnlyRecommendedSystemImages,
      devicePanelState::setShowOnlyRecommendedSystemImages,
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

      InfoOutlineIcon(
        "Filter by images that include full support for the Google Play Store, only Google APIs and services, or the base version of " +
          "Android (AOSP) without any Google apps or services",
        Modifier.align(Alignment.CenterVertically),
      )
    }
  }
}

@Composable
private fun SystemImageTable(
  images: List<ISystemImage>,
  selectionState: TableSelectionState<ISystemImage>,
  onIsSystemImageTableSelectionValidChange: (Boolean) -> Unit,
  onDownloadButtonClick: (String) -> Unit,
  onRowClick: (ISystemImage) -> Unit,
  modifier: Modifier = Modifier,
) {
  onIsSystemImageTableSelectionValidChange(selectionState.selection in images)

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
          DownloadButton(
            onClick = { onDownloadButtonClick(it.`package`.path) },
            Modifier.size(16.dp),
          )
        }
      },
      TableTextColumn("System Image", attribute = { it.`package`.displayName }),
      TableTextColumn(
        "API",
        TableColumnWidth.Fixed(125.dp),
        { it.androidVersion.apiStringWithExtension },
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

internal class DevicePanelState
internal constructor(
  selectedApi: AndroidVersionSelection,
  selectedServices: Services?,
  private val systemImages: List<ISystemImage>,
  showSdkExtensionSystemImages: Boolean = false,
  showOnlyRecommendedSystemImages: Boolean = true,
) {
  internal var selectedApi by mutableStateOf(selectedApi)
    private set

  internal var selectedServices by mutableStateOf(selectedServices)
    private set

  internal var filteredSystemImages by mutableStateOf(systemImages)
    private set

  internal var showSdkExtensionSystemImages by mutableStateOf(showSdkExtensionSystemImages)
    private set

  internal var showOnlyRecommendedSystemImages by mutableStateOf(showOnlyRecommendedSystemImages)
    private set

  init {
    filteredSystemImages = systemImages.filter(this::matches)
  }

  internal fun setSelectedApi(selectedApi: AndroidVersionSelection) {
    this.selectedApi = selectedApi
    filteredSystemImages = systemImages.filter(this::matches)
  }

  internal fun setSelectedServices(selectedServices: Services?) {
    this.selectedServices = selectedServices
    filteredSystemImages = systemImages.filter(this::matches)
  }

  internal fun setShowSdkExtensionSystemImages(showSdkExtensionSystemImages: Boolean) {
    this.showSdkExtensionSystemImages = showSdkExtensionSystemImages
    filteredSystemImages = systemImages.filter(this::matches)
  }

  internal fun setShowOnlyRecommendedSystemImages(showOnlyRecommendedSystemImages: Boolean) {
    this.showOnlyRecommendedSystemImages = showOnlyRecommendedSystemImages
    filteredSystemImages = systemImages.filter(this::matches)
  }

  private fun matches(image: ISystemImage): Boolean {
    val apiMatches = selectedApi.matches(image.androidVersion)
    val servicesMatches = selectedServices == null || image.getServices() == selectedServices
    val isSdkExtensionMatches = showSdkExtensionSystemImages || image.androidVersion.isBaseExtension
    val isRecommendedMatches = !showOnlyRecommendedSystemImages || image.isRecommended()

    return apiMatches && servicesMatches && isSdkExtensionMatches && isRecommendedMatches
  }
}

@Composable
private fun DownloadButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  IconButton(onClick, modifier) { Icon(AllIconsKeys.Actions.Download, "Download") }
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

    InfoOutlineIcon(
      "Select this option to see images of SDK extensions for the selected API level",
      Modifier.align(Alignment.CenterVertically),
    )
  }
}
