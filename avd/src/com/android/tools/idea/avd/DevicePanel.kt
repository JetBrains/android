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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.sdklib.AndroidVersion
import com.android.sdklib.ISystemImage
import com.android.sdklib.RemoteSystemImage
import com.android.sdklib.displayApiString
import com.android.tools.adtui.compose.LocalProject
import com.android.tools.idea.adddevicedialog.EmptyStatePanel
import com.android.tools.idea.adddevicedialog.SortOrder
import com.android.tools.idea.adddevicedialog.Table
import com.android.tools.idea.adddevicedialog.TableColumn
import com.android.tools.idea.adddevicedialog.TableColumnWidth
import com.android.tools.idea.adddevicedialog.TableSelectionState
import com.android.tools.idea.adddevicedialog.TableSortState
import com.android.tools.idea.adddevicedialog.TableTextColumn
import com.intellij.ide.BrowserUtil
import icons.StudioIconsCompose
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.ExternalLink
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DevicePanel(
  configureDevicePanelState: ConfigureDevicePanelState,
  systemImageFilterState: SystemImageFilterState,
  imageState: SystemImageState,
  androidVersions: ImmutableList<AndroidVersion>,
  servicesCollection: ImmutableCollection<Services>,
  onDownloadButtonClick: (String) -> Unit,
  onSystemImageTableRowClick: (ISystemImage) -> Unit,
  modifier: Modifier = Modifier,
) {
  val nameFocusRequester = remember { FocusRequester() }
  Column(modifier) {
    Text("Name", Modifier.padding(bottom = Padding.SMALL))

    val nameState = rememberTextFieldState(configureDevicePanelState.device.name)
    LaunchedEffect(Unit) {
      nameFocusRequester.requestFocus()
      snapshotFlow { nameState.text.toString() }
        .collect { configureDevicePanelState.device.name = it }
    }

    ErrorTooltip(configureDevicePanelState.deviceNameError) {
      TextField(
        nameState,
        Modifier.padding(bottom = Padding.MEDIUM_LARGE).focusRequester(nameFocusRequester),
        outline =
          if (configureDevicePanelState.deviceNameError == null) Outline.None else Outline.Error,
      )
    }

    Text(
      "Select system image",
      fontWeight = FontWeight.SemiBold,
      fontSize = LocalTextStyle.current.fontSize * 1.1,
      modifier = Modifier.padding(bottom = Padding.SMALL_MEDIUM),
    )

    Text(
      "Use the filters to help find the system image that you prefer. The combination of device " +
        "profile and system image is only an approximation of the equivalent physical hardware.",
      color = JewelTheme.globalColors.text.info,
      modifier = Modifier.padding(bottom = Padding.SMALL_MEDIUM),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(Padding.LARGE)) {
      ApiFilter(
        androidVersions,
        systemImageFilterState.selectedApi,
        systemImageFilterState::selectedApi::set,
        Modifier.padding(bottom = Padding.MEDIUM_LARGE),
      )

      ServicesDropdown(
        systemImageFilterState.selectedServices,
        servicesCollection,
        systemImageFilterState::selectedServices::set,
        Modifier.padding(bottom = Padding.MEDIUM_LARGE),
      )
    }

    val baseExtensionLevels = remember(imageState.images) { BaseExtensionLevels(imageState.images) }
    val filteredSystemImages = systemImageFilterState.filter(imageState.images, baseExtensionLevels)
    configureDevicePanelState.isSystemImageTableSelectionValid =
      configureDevicePanelState.systemImageTableSelectionState.selection in filteredSystemImages

    Box(Modifier.weight(1f).padding(bottom = Padding.SMALL)) {
      if (filteredSystemImages.isEmpty()) {
        EmptyStatePanel(
          "No system images available matching the current set of filters.",
          Modifier.fillMaxSize(),
        )
      } else {
        if (imageState.error != null) {
          ErrorPanel(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            imageState.error,
          )
        } else if (!imageState.hasRemote) {
          ProgressIndicatorPanel(
            "Loading system images...",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
          )
        }
        SystemImageTable(
          filteredSystemImages,
          configureDevicePanelState.systemImageTableSelectionState,
          onDownloadButtonClick,
          onSystemImageTableRowClick,
          Modifier.border(1.dp, JewelTheme.globalColors.borders.normal),
        )
      }
    }

    ShowSdkExtensionSystemImagesCheckbox(
      systemImageFilterState.showSdkExtensionSystemImages,
      systemImageFilterState::showSdkExtensionSystemImages::set,
      Modifier.padding(bottom = Padding.SMALL),
    )

    CheckboxRow(
      "Show unsupported system images",
      systemImageFilterState.showUnsupportedSystemImages,
      systemImageFilterState::showUnsupportedSystemImages::set,
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
  onDownloadButtonClick: (String) -> Unit,
  onRowClick: (ISystemImage) -> Unit,
  modifier: Modifier = Modifier,
) {
  val sortedImages = images.sortedWith(SystemImageComparator)
  val starredImage by rememberUpdatedState(sortedImages.last().takeIf { it.isSupported() })
  val starColumn = remember {
    TableColumn("", TableColumnWidth.Fixed(16.dp), comparator = SystemImageComparator) { image, _ ->
      if (image == starredImage) {
        @OptIn(ExperimentalFoundationApi::class)
        Tooltip(
          tooltip = {
            Text(
              "This is the recommended system image for your workstation and selected device configuration.",
              Modifier.widthIn(max = 300.dp),
            )
          }
        ) {
          Icon(
            AllIconsKeys.Nodes.Favorite,
            contentDescription = "Recommended",
            modifier = Modifier.size(16.dp),
          )
        }
      } else {
        val warnings = image.imageWarnings()
        if (warnings.isNotEmpty()) {
          SystemImageWarningIcon(warnings)
        }
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
      ) { image, _ ->
        if (image is RemoteSystemImage) {
          DownloadButton(
            onClick = { onDownloadButtonClick(image.`package`.path) },
            Modifier.size(16.dp),
          )
        }
      },
      TableTextColumn("System Image", attribute = { it.`package`.displayName }),
      TableTextColumn(
        "API",
        TableColumnWidth.Fixed(125.dp),
        { it.androidVersion.displayApiString },
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
      "Show system images with SDK extensions",
      sdkExtensionSystemImagesVisible,
      onSdkExtensionSystemImagesVisibleChange,
      Modifier.padding(end = Padding.MEDIUM),
    )

    @OptIn(ExperimentalFoundationApi::class)
    LingeringTooltip(
      tooltip = {
        Column(Modifier.widthIn(max = 400.dp)) {
          Text("SDK extensions add new features to previous versions of Android.")
          Spacer(Modifier.size(4.dp))
          val project = LocalProject.current
          val url = "https://developer.android.com/guide/sdk-extensions"
          ExternalLink("Learn more", onClick = { BrowserUtil.browse(url, project) })
        }
      },
      Modifier.align(Alignment.CenterVertically),
    ) {
      Icon(AllIconsKeys.General.Note, null)
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SystemImageWarningIcon(warnings: List<String>) {
  Tooltip(
    tooltip = {
      Column(Modifier.widthIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (warning in warnings) {
          Text(warning)
        }
      }
    }
  ) {
    Icon(
      StudioIconsCompose.Common.Warning,
      contentDescription = "Non-recommended image",
      modifier = Modifier.size(16.dp),
    )
  }
}
