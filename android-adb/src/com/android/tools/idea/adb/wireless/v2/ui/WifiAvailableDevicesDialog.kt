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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.android.adblib.MdnsTlsService
import com.android.adblib.MdnsTrackServiceInfo
import com.android.sdklib.deviceprovisioner.SetChange
import com.android.sdklib.deviceprovisioner.trackSetChanges
import com.android.tools.adtui.compose.StudioComposePanel
import com.android.tools.adtui.compose.table.RowFilter
import com.android.tools.adtui.compose.table.Table
import com.android.tools.adtui.compose.table.TableColumn
import com.android.tools.adtui.compose.table.TableColumnWidth
import com.android.tools.adtui.compose.table.TableTextColumn
import com.android.tools.idea.adb.wireless.MdnsSupportState
import com.android.tools.idea.adb.wireless.PairDevicesUsingWiFiService
import com.android.tools.idea.adb.wireless.TrackingMdnsService
import com.android.tools.idea.adb.wireless.Urls
import com.android.tools.idea.adb.wireless.WiFiPairingService
import com.android.tools.idea.adb.wireless.needsUpdate
import com.android.tools.idea.adddevicedialog.EmptyStatePanel
import com.android.tools.idea.adddevicedialog.SearchBar
import com.android.tools.idea.ui.SimpleDialog
import com.android.tools.idea.ui.SimpleDialogOptions
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent.createEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ui.JBDimension
import icons.StudioIconsCompose
import javax.swing.JComponent
import kotlin.collections.forEach
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.styling.LocalLinkStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@OptIn(ExperimentalFoundationApi::class)
class WifiAvailableDevicesDialog(
  private val project: Project,
  private val wifiPairingService: WiFiPairingService,
) : Disposable {

  companion object {
    internal const val SEARCH_BAR_TEST_TAG = "deviceSearchBar"
    internal const val WARNING_TOOLTIP_TEST_TAG = "warningTag"
  }

  private val dialog: SimpleDialog
  private val model = WifiPairableDeviceModel()

  private val panelPreferredSize: JBDimension
    get() = JBDimension(700, 600)

  private val rootView: JComponent = StudioComposePanel { WifiDialog() }

  @Composable
  internal fun WifiDialog() {
    val state by
      produceState<MdnsSupportState?>(null) {
        val supportState = wifiPairingService.checkMdnsSupport()
        if (supportState != MdnsSupportState.Supported) {
          value = supportState
          return@produceState
        }
        if (!wifiPairingService.isTrackMdnsServiceAvailable()) {
          value = MdnsSupportState.AdbVersionTooLow
          return@produceState
        }
        value = supportState
        trackMdnsServices()
      }

    when (state) {
      null -> { // Checking mDNS state
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.padding(8.dp))
            Text("Preparing Wi-Fi pairing...")
          }
        }
      }
      MdnsSupportState.Supported -> {
        WifiPairableDevices(model.devices)
      }
      MdnsSupportState.NotSupported ->
        ErrorStateDisplay(
          title = "Wi-Fi Pairing Not Supported",
          messages =
            listOf(
              "This system does not meet the requirements to support Wi-Fi pairing.",
              "Please update to the latest version of \"platform-tools\" using the SDK manager.",
            ),
          links = listOf(Urls.openSdkManager to "Open SDK Manager", Urls.learnMore to "Learn more"),
        )
      MdnsSupportState.AdbVersionTooLow ->
        ErrorStateDisplay(
          title = "ADB Version Too Low",
          messages =
            listOf(
              "The currently installed version of the \"Android Debug Bridge\" (adb) does not support Wi-Fi pairing.",
              "Please update to the latest version of \"platform-tools\" using the SDK manager.",
            ),
          links = listOf(Urls.openSdkManager to "Open SDK Manager", Urls.learnMore to "Learn more"),
        )
      MdnsSupportState.AdbInvocationError ->
        ErrorStateDisplay(
          title = "ADB Invocation Error",
          messages = listOf("There was an unexpected error during Wi-Fi pairing initialization."),
          links = listOf(Urls.learnMore to "Learn more"),
        )
      MdnsSupportState.AdbMacEnvironmentBroken ->
        ErrorStateDisplay(
          title = "macOS mDNS Environment Issue",
          messages =
            listOf(
              "Please update to the latest version of \"platform-tools\" (minimum 35.0.2).",
              "Make sure mDNS backend 'default' is selected in ADB Settings.",
            ),
          links =
            listOf(
              Urls.openSdkManager to "Open SDK Manager",
              Urls.openAdbSettings to "Open ADB Settings",
              Urls.learnMore to "Learn more",
            ),
        )
      MdnsSupportState.AdbDisabled ->
        ErrorStateDisplay(
          title = "mDNS Disabled in ADB",
          messages =
            listOf(
              "mDNS backend is disabled.",
              "1. Make sure it is enabled in ADB Settings.",
              "2. Make sure you are not using a manually managed ADB server.",
            ),
          links =
            listOf(Urls.openAdbSettings to "Open ADB Settings", Urls.learnMore to "Learn more"),
        )
    }
  }

  init {
    val options =
      SimpleDialogOptions(
        project,
        true,
        DialogWrapper.IdeModalityType.IDE,
        title = "Pair devices over Wi-Fi",
        isModal = true,
        hasOkButton = false,
        cancelButtonText = "Close",
        centerPanelProvider = { createCenterPanel() },
        preferredFocusProvider = { rootView },
      )
    dialog = SimpleDialog(options)
    dialog.init()
  }

  private suspend fun trackMdnsServices() {
    wifiPairingService
      .trackMdnsServices()
      .map { it.tlsMdnsServices.toSet() }
      .trackSetChanges()
      // It's not possible to pair emulators.
      .filterNot {
        it is SetChange.Add &&
          it.value.service.serviceInstanceName.instance.startsWith("adb-EMULATOR")
      }
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

  fun createCenterPanel(): JComponent {
    rootView.preferredSize = panelPreferredSize
    if (SystemInfo.isMac) {
      rootView.minimumSize = panelPreferredSize
    }
    return rootView
  }

  @Composable
  private fun ErrorStateDisplay(
    title: String,
    messages: List<String>,
    links: List<Pair<String, String>>,
  ) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
      Text(
        buildAnnotatedString {
          pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
          append(title)
          pop()
          appendLine()
          appendLine()

          messages.forEach { message ->
            append(message)
            appendLine()
          }
          appendLine()

          links.forEach { (url, text) ->
            withLink(
              LinkAnnotation.Url(
                url = url,
                styles =
                  TextLinkStyles(style = SpanStyle(color = LocalLinkStyle.current.colors.content)),
                linkInteractionListener = { WifiPairingLinkHandler.handleLinkActivation(url) },
              )
            ) {
              append(text)
            }
            appendLine()
          }
        }
      )
    }
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
      Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            StudioIconsCompose.Avd.ConnectionWifi,
            contentDescription = "Same Wi-Fi Network",
            modifier = Modifier.padding(end = 16.dp),
          )
          Column {
            Text("1. Connect to the Same Network", fontWeight = FontWeight.Bold)
            Text("Ensure your workstation and device are on the same wireless network.")
          }
        }
        Spacer(modifier = Modifier.padding(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            AllIconsKeys.Actions.StartDebugger,
            contentDescription = "Wireless debugging Enabled",
            modifier = Modifier.padding(end = 16.dp),
          )
          Column {
            Text("2. Enable Wireless Debugging", fontWeight = FontWeight.Bold)
            Text(
              buildAnnotatedString {
                append("On your Android 11+ device, go to Developer Options > Wireless debugging. ")
                withLink(
                  LinkAnnotation.Url(
                    Urls.learnMore,
                    TextLinkStyles(style = SpanStyle(color = LocalLinkStyle.current.colors.content)),
                  )
                ) {
                  append("Learn more")
                }
                append(".")
              }
            )
          }
        }
      }

      Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp)) {
        if (devices.isNotEmpty()) {
          SearchBar(
            textState,
            filterState.description,
            Modifier.weight(1f)
              .padding(2.dp)
              .focusRequester(searchFieldFocusRequester)
              .testTag(SEARCH_BAR_TEST_TAG),
          )
        }
      }

      val filteredDevices = devices.filter(filterState::apply)
      if (filteredDevices.isEmpty()) {
        if (devices.isEmpty()) {
          val emptyText = buildAnnotatedString {
            append("No devices found.\n\n")
            append("Please double-check that:\n")
            append("• Your device and computer are on the same Wi-Fi network.\n")
            append("• 'Wireless debugging' is enabled in your device's Developer Options.\n\n")
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            append("If both are correct, your network may be blocking mDNS traffic.")
            pop()
          }
          Box(Modifier.fillMaxSize()) {
            Text(
              emptyText,
              Modifier.align(Alignment.Center),
              color = JewelTheme.globalColors.text.info,
            )
          }
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
    if (devices.isNotEmpty()) {
      LaunchedEffect(Unit) { searchFieldFocusRequester.requestFocus() }
    }
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
      TableColumn<MdnsTlsService>("", TableColumnWidth.Fixed(16.dp)) { device, _ ->
        if (device.service.needsUpdate()) {
          Tooltip(
            tooltip = { Text("Check for device software updates to improve Wi-Fi pairing.") },
            modifier = Modifier.testTag(WARNING_TOOLTIP_TEST_TAG),
          ) {
            Icon(
              StudioIconsCompose.Common.Warning,
              contentDescription = "device needs update warning icon",
            )
          }
        }
      },
      TableTextColumn<MdnsTlsService>(
        "Name",
        TableColumnWidth.Weighted(2f),
        attribute = { buildDeviceName(it.service) },
        maxLines = 2,
      ),
      TableTextColumn(
        "IP Address & Port",
        TableColumnWidth.Weighted(2f),
        attribute = { "${it.service.ipv4}:${it.service.port}" },
      ),
      TableTextColumn<MdnsTlsService>(
        "API",
        attribute = { it.service.buildVersionSdkFull ?: "Unknown" },
      ),
      TableColumn("", TableColumnWidth.Weighted(1f)) { device, _ ->
        OutlinedButton(
          onClick = {
            val controller =
              PairDevicesUsingWiFiService.getInstance(project)
                .createPairingDialogController(
                  TrackingMdnsService(
                    serviceName = device.service.serviceInstanceName.instance,
                    ipv4 = device.service.ipv4,
                    port = device.service.port.toString(),
                    deviceName = buildDeviceName(device.service),
                    mdnsServiceVersion = device.service.mdnsServiceVersion,
                  )
                )
            controller.showDialog()
          }
        ) {
          Row {
            Icon(StudioIconsCompose.Avd.PairOverWifi, contentDescription = "pair device over wifi")
            Spacer(Modifier.width(4.dp))
            Text("Pair")
          }
        }
      },
    )

  fun showDialog() {
    dialog.show()
  }

  fun closeDialog(exitCode: Int = DialogWrapper.CANCEL_EXIT_CODE) {
    dialog.close(exitCode)
  }

  override fun dispose() {}
}

private fun buildDeviceName(mdnsService: MdnsTrackServiceInfo): String =
  mdnsService.givenName.takeUnless { it.isNullOrBlank() }
    ?: mdnsService.deviceModel.takeUnless { it.isNullOrBlank() }
    ?: "Device"

object WifiPairingLinkHandler {

  fun handleLinkActivation(url: String) {
    when (url) {
      Urls.openSdkManager -> {
        ActionManager.getInstance().getAction("Android.RunAndroidSdkManager").let {
          it.actionPerformed(
            createEvent(it, DataContext.EMPTY_CONTEXT, null, "", ActionUiKind.NONE, null)
          )
        }
      }
      Urls.openAdbSettings -> {
        ActionManager.getInstance().getAction("Android.AdbSettings").let {
          it.actionPerformed(
            createEvent(it, DataContext.EMPTY_CONTEXT, null, "", ActionUiKind.NONE, null)
          )
        }
      }
      else -> {
        if (url.isNotBlank()) {
          BrowserUtil.browse(url)
        }
      }
    }
  }
}
