/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.adb.wireless.v2.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEditable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.android.adblib.MdnsServices
import com.android.adblib.MdnsTlsService
import com.android.adblib.MdnsTrackServiceInfo
import com.android.adblib.ServiceInstanceName
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.idea.adb.wireless.MdnsSupportState
import com.android.tools.idea.adb.wireless.PairDevicesUsingWiFiService
import com.android.tools.idea.adb.wireless.TrackingMdnsService
import com.android.tools.idea.adb.wireless.WiFiPairingController
import com.android.tools.idea.adb.wireless.WiFiPairingService
import com.android.tools.idea.adb.wireless.v2.ui.WifiAvailableDevicesDialog.Companion.SEARCH_BAR_TEST_TAG
import com.android.tools.idea.testing.ProjectServiceRule
import com.android.tools.idea.testing.ProjectServiceRule.Companion.invoke
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunsInEdt
class WifiAvailableDevicesDialogTest {
  private val mockWiFiPairingService = mock<WiFiPairingService>()
  private val mockPairDevicesUsingWiFiService = mock<PairDevicesUsingWiFiService>()
  private lateinit var mockPairingDialogController: WiFiPairingController

  private val adblibMdnsServicesFlow =
    MutableStateFlow(MdnsServices(emptyList(), emptyList(), emptyList()))

  private lateinit var wifiAvailableDevicesDialog: WifiAvailableDevicesDialog

  private val composeTestRule = createStudioComposeTestRule()
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @get:Rule
  val rule =
    RuleChain(
      EdtRule(),
      ApplicationRule(),
      disposableRule,
      projectRule,
      ProjectServiceRule(
        projectRule,
        PairDevicesUsingWiFiService::class.java,
        mockPairDevicesUsingWiFiService,
      ),
      composeTestRule,
    )

  @Before
  fun setUp() {
    mockPairingDialogController = mock()
    wifiAvailableDevicesDialog =
      WifiAvailableDevicesDialog(projectRule.project, mockWiFiPairingService)
    whenever(mockPairDevicesUsingWiFiService.createPairingDialogController(any()))
      .thenReturn(mockPairingDialogController)
    whenever(mockWiFiPairingService.trackMdnsServices()).thenReturn(adblibMdnsServicesFlow)
  }

  @After
  fun tearDown() {
    // Need to call dialog.close for it to clean itself up and not leak references.
    wifiAvailableDevicesDialog.closeDialog()
  }

  private fun createMdnsTlsService(
    instance: String,
    ipv4: String,
    port: Int,
    model: String? = "Pixel Test",
    sdk: String? = "33",
    knownDevice: Boolean = false,
  ): MdnsTlsService {
    return MdnsTlsService(
      MdnsTrackServiceInfo(
        ServiceInstanceName(instance, "_adb-tls-connect._tcp", "local"),
        ipv4,
        emptyList(),
        port,
        model,
        sdk,
      ),
      knownDevice,
    )
  }

  @Test
  fun initialState_showsLoading() = runTest {
    whenever(mockWiFiPairingService.checkMdnsSupport()).thenReturn(null) // Simulate loading
    composeTestRule.setContent { wifiAvailableDevicesDialog.WifiDialog() }

    composeTestRule.onNodeWithText("Preparing Wi-Fi pairing...").assertIsDisplayed()
  }

  @Test
  fun mdnsNotSupported_showsNotSupportedError() = runTest {
    whenever(mockWiFiPairingService.checkMdnsSupport()).thenReturn(MdnsSupportState.NotSupported)
    composeTestRule.setContent { wifiAvailableDevicesDialog.WifiDialog() }

    composeTestRule
      .onNodeWithText("Wi-Fi Pairing Not Supported", substring = true)
      .assertIsDisplayed()
    composeTestRule.onNodeWithText("Open SDK Manager", substring = true).assertIsDisplayed()
  }

  @Test
  fun adbVersionTooLow_showsAdbVersionTooLowError() = runTest {
    whenever(mockWiFiPairingService.checkMdnsSupport())
      .thenReturn(MdnsSupportState.AdbVersionTooLow)
    composeTestRule.setContent { wifiAvailableDevicesDialog.WifiDialog() }

    composeTestRule.onNodeWithText("ADB Version Too Low", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Open SDK Manager", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Learn more", substring = true).assertIsDisplayed()
  }

  @Test
  fun adbInvocationError_showsAdbInvocationError() = runTest {
    whenever(mockWiFiPairingService.checkMdnsSupport())
      .thenReturn(MdnsSupportState.AdbInvocationError)
    composeTestRule.setContent { wifiAvailableDevicesDialog.WifiDialog() }

    composeTestRule.onNodeWithText("ADB Invocation Error", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Learn more", substring = true).assertIsDisplayed()
  }

  @Test
  fun adbMacEnvironmentBroken_showsMacEnvironmentBrokenError() = runTest {
    whenever(mockWiFiPairingService.checkMdnsSupport())
      .thenReturn(MdnsSupportState.AdbMacEnvironmentBroken)
    composeTestRule.setContent { wifiAvailableDevicesDialog.WifiDialog() }

    composeTestRule
      .onNodeWithText("macOS mDNS Environment Issue", substring = true)
      .assertIsDisplayed()
    composeTestRule.onNodeWithText("Open SDK Manager", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Open ADB Settings", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Learn more", substring = true).assertIsDisplayed()
  }

  @Test
  fun adbDisabled_showsMdnsDisabledError() = runTest {
    whenever(mockWiFiPairingService.checkMdnsSupport()).thenReturn(MdnsSupportState.AdbDisabled)
    composeTestRule.setContent { wifiAvailableDevicesDialog.WifiDialog() }

    composeTestRule.onNodeWithText("mDNS Disabled in ADB", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Open ADB Settings", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Learn more", substring = true).assertIsDisplayed()
  }

  @Test
  fun mdnsSupported_noDevices_showsEmptyState() = runTest {
    whenever(mockWiFiPairingService.checkMdnsSupport()).thenReturn(MdnsSupportState.Supported)
    composeTestRule.setContent { wifiAvailableDevicesDialog.WifiDialog() }

    composeTestRule.onNodeWithText("No devices found.", substring = true).assertIsDisplayed()
    composeTestRule
      .onNodeWithText("Ensure that your workstation and device are connected", substring = true)
      .assertIsDisplayed()
  }

  @Test
  fun mdnsSupported_withDevices_showsTable() = runTest {
    whenever(mockWiFiPairingService.checkMdnsSupport()).thenReturn(MdnsSupportState.Supported)
    val service = createMdnsTlsService("service1", "192.168.1.101", 5555, "Device A", "30")
    adblibMdnsServicesFlow.value = MdnsServices(emptyList(), listOf(service), emptyList())
    composeTestRule.setContent { wifiAvailableDevicesDialog.WifiDialog() }

    composeTestRule.onNodeWithText("Device A").assertIsDisplayed()
    composeTestRule.onNodeWithText("192.168.1.101:5555").assertIsDisplayed()
    composeTestRule.onNodeWithText("30").assertIsDisplayed()
  }

  @Test
  fun mdnsSupported_addDevice_updatesTable() = runTest {
    whenever(mockWiFiPairingService.checkMdnsSupport()).thenReturn(MdnsSupportState.Supported)
    adblibMdnsServicesFlow.value = MdnsServices(emptyList(), emptyList(), emptyList())
    composeTestRule.setContent { wifiAvailableDevicesDialog.WifiDialog() }

    composeTestRule.onNodeWithText("No devices found.", substring = true).assertIsDisplayed()

    val service1 = createMdnsTlsService("service1", "192.168.1.101", 5555, "Device A", "30")
    adblibMdnsServicesFlow.value = MdnsServices(emptyList(), listOf(service1), emptyList())

    composeTestRule.onNodeWithText("Device A").assertIsDisplayed()
    composeTestRule.onNodeWithText("192.168.1.101:5555").assertIsDisplayed()
  }

  @Test
  fun mdnsSupported_removeDevice_updatesTable() = runTest {
    whenever(mockWiFiPairingService.checkMdnsSupport()).thenReturn(MdnsSupportState.Supported)
    val service1 = createMdnsTlsService("service1", "192.168.1.101", 5555, "Device A", "30")
    adblibMdnsServicesFlow.value = MdnsServices(emptyList(), listOf(service1), emptyList())
    composeTestRule.setContent { wifiAvailableDevicesDialog.WifiDialog() }

    composeTestRule.onNodeWithText("Device A").assertIsDisplayed()

    adblibMdnsServicesFlow.value = MdnsServices(emptyList(), emptyList(), emptyList())
    composeTestRule.onNodeWithText("No devices found.", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Device A").assertDoesNotExist()
  }

  @Test
  fun searchFunctionality_filtersDevices() = runTest {
    whenever(mockWiFiPairingService.checkMdnsSupport()).thenReturn(MdnsSupportState.Supported)
    val service1 = createMdnsTlsService("service1", "192.168.1.101", 5555, "Device Alpha", "30")
    val service2 = createMdnsTlsService("service2", "192.168.1.102", 5556, "Device Beta", "31")
    adblibMdnsServicesFlow.value =
      MdnsServices(emptyList(), listOf(service1, service2), emptyList())
    composeTestRule.setContent { wifiAvailableDevicesDialog.WifiDialog() }

    composeTestRule.onNodeWithText("Device Alpha").assertIsDisplayed()
    composeTestRule.onNodeWithText("Device Beta").assertIsDisplayed()

    composeTestRule
      .onNode(hasTestTag(SEARCH_BAR_TEST_TAG) and isEditable(), useUnmergedTree = true)
      .performTextInput("Alpha")

    composeTestRule.onNodeWithText("Device Alpha").assertIsDisplayed()
    composeTestRule.onNodeWithText("Device Beta").assertDoesNotExist()

    composeTestRule
      .onNodeWithContentDescription("Clear search", useUnmergedTree = true)
      .performClick()

    composeTestRule.onNodeWithText("Device Alpha").assertIsDisplayed()
    composeTestRule.onNodeWithText("Device Beta").assertIsDisplayed()
  }

  @Test
  fun pairButtonClick_invokesPairingController() = runTest {
    whenever(mockWiFiPairingService.checkMdnsSupport()).thenReturn(MdnsSupportState.Supported)
    val service1 = createMdnsTlsService("service1", "192.168.1.101", 5555, "Device A", "30")
    adblibMdnsServicesFlow.value = MdnsServices(emptyList(), listOf(service1), emptyList())

    // Ensure PairDevicesUsingWiFiService is mocked correctly for the dialog instance
    composeTestRule.setContent { wifiAvailableDevicesDialog.WifiDialog() }

    composeTestRule
      .onNodeWithContentDescription("pair device over wifi", useUnmergedTree = true)
      .performClick()

    val expectedTrackingMdnsService =
      TrackingMdnsService(
        serviceName = "service1",
        ipv4 = "192.168.1.101",
        port = "5555",
        deviceName = "Device A",
      )

    verify(mockPairDevicesUsingWiFiService)
      .createPairingDialogController(
        argThat { serviceName == expectedTrackingMdnsService.serviceName }
      )
    verify(mockPairingDialogController).showDialog()
  }
}
