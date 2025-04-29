/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.adb.wireless.v2.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.android.adblib.AdbFeatures.TRACK_MDNS_SERVICE
import com.android.adblib.MdnsTlsService
import com.android.adblib.MdnsTrackServiceInfo
import com.android.sdklib.deviceprovisioner.SetChange
import com.android.sdklib.deviceprovisioner.trackSetChanges
import com.android.tools.adtui.compose.StudioComposePanel
import com.android.tools.idea.adb.wireless.PairDevicesUsingWiFiService
import com.android.tools.idea.adb.wireless.TrackingMdnsService
import com.android.tools.idea.adb.wireless.Urls
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.adddevicedialog.EmptyStatePanel
import com.android.tools.idea.adddevicedialog.RowFilter
import com.android.tools.idea.adddevicedialog.SearchBar
import com.android.tools.idea.adddevicedialog.Table
import com.android.tools.idea.adddevicedialog.TableColumn
import com.android.tools.idea.adddevicedialog.TableColumnWidth
import com.android.tools.idea.adddevicedialog.TableTextColumn
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.ui.SimpleDialog
import com.android.tools.idea.ui.SimpleDialogOptions
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ui.JBDimension
import icons.StudioIconsCompose
import javax.swing.JComponent
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.LocalLinkStyle

// TODO(b/412571872) add tests
class WifiAvailableDevicesDialog(project: Project) : Disposable {

  private val dialog: SimpleDialog

  private val model = WifiPairableDeviceModel()

  private val scope = createCoroutineScope()

  private val rootView: JComponent = StudioComposePanel { WifiPairableDevices(model.devices) }

  private val panelPreferredSize: JBDimension
    get() = JBDimension(700, 600)

  init {
    scope.launch {
      val hostServices = AdbLibService.Companion.getSession(project).hostServices
      if (!hostServices.hostFeatures().contains(TRACK_MDNS_SERVICE)) {
        // TODO(b/412571872) check mDNS enabled
        return@launch
      }
      hostServices
        .trackMdnsServices()
        .map { it.tlsMdnsServices.toSet() }
        .trackSetChanges()
        .collect {
          when (it) {
            is SetChange.Remove -> {
              model.removeMdnsService(it.value.service.serviceInstanceName.instance)
            }
            is SetChange.Add -> {
              model.addMdnsService(it.value)
            }
          }
        }
    }

    val options =
      SimpleDialogOptions(
        project,
        true,
        DialogWrapper.IdeModalityType.IDE,
        title = "Pair new devices over Wi-Fi",
        isModal = true,
        hasOkButton = false,
        cancelButtonText = "Close",
        centerPanelProvider = { createCenterPanel() },
        preferredFocusProvider = { rootView },
      )
    dialog = SimpleDialog(options)
    dialog.init()
  }

  fun createCenterPanel(): JComponent {
    rootView.preferredSize = panelPreferredSize
    if (SystemInfo.isMac) {
      rootView.minimumSize = panelPreferredSize
    }
    return rootView
  }

  @Composable
  private fun WifiPairableDevices(devicesFlow: StateFlow<List<MdnsTlsService>>) {
    val devices by devicesFlow.collectAsState()
    val filterState = remember { TextFilterState() }
    val textState = rememberTextFieldState(filterState.searchText)
    val searchFieldFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
      snapshotFlow { textState.text.toString() }.collect { filterState.searchText = it }
    }

    Column(modifier = Modifier.fillMaxSize()) {
      Text(
        buildAnnotatedString {
          append(
            "Ensure that your workstation and device are connected to the same wireless network, "
          )
          append(
            "then enable Wireless debugging on your Android 11+ device by toggling Developer Options > wireless debugging. "
          )
          withLink(
            LinkAnnotation.Url(
              Urls.learnMore,
              TextLinkStyles(style = SpanStyle(color = LocalLinkStyle.current.colors.content)),
            )
          ) {
            append("Learn more")
          }
          append(".")
        },
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
      )
      Spacer(modifier = Modifier.weight(1f))

      Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp)) {
        SearchBar(
          textState,
          filterState.description,
          Modifier.weight(1f).padding(2.dp).focusRequester(searchFieldFocusRequester),
        )
      }

      val filteredDevices = devices.filter(filterState::apply)
      if (filteredDevices.isEmpty()) {
        if (devices.isEmpty()) {
          EmptyStatePanel("No devices found.", Modifier.fillMaxSize())
        } else {
          EmptyStatePanel(
            "No devices found for \"${filterState.searchText}\".",
            Modifier.fillMaxSize(),
          )
        }
      } else {
        Table<MdnsTlsService>(columns, filteredDevices, { it.service.serviceInstanceName.instance })
      }
    }
    LaunchedEffect(Unit) { searchFieldFocusRequester.requestFocus() }
  }

  private class TextFilterState : RowFilter<MdnsTlsService> {
    var searchText: String by mutableStateOf("")
    val description = "Search for a device by name"

    override fun apply(row: MdnsTlsService): Boolean {
      return searchText.isBlank() ||
        buildDeviceName(row.service).contains(searchText.trim(), ignoreCase = true)
    }
  }

  private val columns =
    listOf<TableColumn<MdnsTlsService>>(
      TableColumn("", TableColumnWidth.Fixed(16.dp)) { device, _ ->
        IconButton(
          onClick = {
            val controller =
              PairDevicesUsingWiFiService.getInstance(project)
                .createPairingDialogController(
                  TrackingMdnsService(
                    serviceName = device.service.serviceInstanceName.instance,
                    ipv4 = device.service.ipv4,
                    port = device.service.port.toString(),
                    deviceName = device.service.deviceModel,
                  )
                )
            controller.showDialog()
          }
        ) {
          Icon(
            key = StudioIconsCompose.Avd.PairOverWifi,
            contentDescription = "pair device over wifi",
          )
        }
      },
      TableTextColumn<MdnsTlsService>(
        "Name",
        TableColumnWidth.Weighted(2f),
        attribute = { buildDeviceName(it.service) },
        maxLines = 2,
      ),
      TableTextColumn("IP Address & Port", attribute = { "${it.service.ipv4}:${it.service.port}" }),
      TableTextColumn(
        "Serial Number",
        attribute = {
          it.service.serviceInstanceName.instance.substringAfter("-").substringBefore("-")
        },
      ),
      TableTextColumn<MdnsTlsService>(
        "API",
        width = TableColumnWidth.ToFit("API", extraPadding = 16.dp),
        attribute = { it.service.buildVersionSdkFull ?: "Unknown" },
      ),
    )

  fun showDialog() {
    dialog.show()
  }

  override fun dispose() {}
}

private fun buildDeviceName(mdnsService: MdnsTrackServiceInfo): String =
  if (mdnsService.deviceModel.isNullOrBlank()) "Device at ${mdnsService.ipv4}:${mdnsService.port}"
  else mdnsService.deviceModel!!
