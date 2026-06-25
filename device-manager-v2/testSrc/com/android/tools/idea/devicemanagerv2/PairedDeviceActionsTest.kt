/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.devicemanagerv2

import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.deviceprovisioner.PairedGlassesInfo
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.wearpairing.PairingConnectionsState
import com.android.tools.idea.wearpairing.PairingDeviceState
import com.android.tools.idea.wearpairing.WearPairingManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TestActionEvent
import icons.StudioIcons
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class PairedDeviceActionsTest {
  @get:Rule val applicationRule = ApplicationRule()

  private val wearDeviceTemplate =
    FakeDeviceTemplate(
      DeviceProperties.buildForTest {
        model = "wearDevice"
        icon = StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR
        wearPairingId = "wearPairing1"
      }
    )

  private val glassesDeviceTemplate =
    FakeDeviceTemplate(
      DeviceProperties.buildForTest {
        model = "glassesDevice"
        icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
        deviceType = DeviceType.AI_GLASSES
        pairedPhoneId = DeviceId("Test", false, "phoneId1")
      }
    )

  private val phoneWithGlassesDeviceTemplate =
    FakeDeviceTemplate(
      DeviceProperties.buildForTest {
        model = "phoneDevice"
        icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
        deviceType = DeviceType.HANDHELD
        pairedGlassesInfos = listOf(PairedGlassesInfo(DeviceId("Test", false, "glassesId1"), "00:11:22:33:44:55"))
      }
    )

  private suspend fun createTestEventWithDeviceRowData(action: AnAction, template: FakeDeviceTemplate = wearDeviceTemplate): AnActionEvent =
    coroutineScope {
      TestActionEvent.createTestEvent(
        action,
        SimpleDataContext.getSimpleContext(
          DEVICE_ROW_DATA_KEY,
          DeviceRowData.create(FakeDeviceHandle(this, template, template.properties), emptyList()),
        ),
      )
    }

  @Test
  fun testActionsDefaultState() {
    val pairAction = PairWearableDeviceAction()
    val unpairAction = UnpairWearableDeviceAction()
    run {
      val event = TestActionEvent.createTestEvent(pairAction)
      pairAction.update(event)
      assertFalse(event.presentation.isVisible)
    }

    run {
      val event = TestActionEvent.createTestEvent(unpairAction)
      unpairAction.update(event)
      assertFalse(event.presentation.isVisible)
    }
  }

  @Test
  fun testUnpairedDevicePairActionState() = runBlocking {
    val pairAction = PairWearableDeviceAction()
    val event = createTestEventWithDeviceRowData(pairAction)
    pairAction.update(event)
    assertTrue(event.presentation.isVisible)
    assertTrue(event.presentation.isEnabled)
  }

  @Test
  fun testPairedDevicePairActionState() = runBlocking {
    val pairAction = PairWearableDeviceAction()
    val event = createTestEventWithDeviceRowData(pairAction)
    WearPairingManager.getInstance()
      .loadSettings(
        listOf(PairingDeviceState("wearPairing1"), PairingDeviceState("phonePairing1")),
        listOf(
          PairingConnectionsState().apply {
            phoneId = "phonePairing1"
            wearDeviceIds.add("wearPairing1")
          }
        ),
      )
    pairAction.update(event)
    // Pair action is available even for already paired devices at the moment.
    assertTrue(event.presentation.isVisible)
    assertTrue(event.presentation.isEnabled)
  }

  @Test
  fun testUnpairedDeviceUnpairActionState() = runBlocking {
    val unpairAction = UnpairWearableDeviceAction()
    val event = createTestEventWithDeviceRowData(unpairAction)
    unpairAction.update(event)
    assertFalse(event.presentation.isVisible)
  }

  @Test
  fun testPairedDeviceUnpairActionState() = runBlocking {
    val unpairAction = UnpairWearableDeviceAction()
    val event = createTestEventWithDeviceRowData(unpairAction)
    WearPairingManager.getInstance()
      .loadSettings(
        listOf(PairingDeviceState("wearPairing1"), PairingDeviceState("phonePairing1")),
        listOf(
          PairingConnectionsState().apply {
            phoneId = "phonePairing1"
            wearDeviceIds.add("wearPairing1")
          }
        ),
      )
    unpairAction.update(event)
    assertTrue(event.presentation.isVisible)
    assertTrue(event.presentation.isEnabled)
  }

  // Regression test for b/331357060
  @Test
  fun testPairActionUnavailableForNonVirtualWatches() = runBlocking {
    val pairAction = PairWearableDeviceAction()
    val deviceTemplate =
      FakeDeviceTemplate(
        DeviceProperties.buildForTest {
          model = "wearDevice"
          icon = StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR
          wearPairingId = "wearPairing1"
          isVirtual = false
          deviceType = DeviceType.WEAR
        }
      )
    val event =
      TestActionEvent.createTestEvent(
        pairAction,
        SimpleDataContext.getSimpleContext(
          DEVICE_ROW_DATA_KEY,
          DeviceRowData.create(FakeDeviceHandle(this, deviceTemplate, deviceTemplate.properties), emptyList()),
        ),
      )
    WearPairingManager.getInstance().loadSettings(listOf(PairingDeviceState("wearPairing1"), PairingDeviceState("phonePairing1")), listOf())

    pairAction.update(event)
    // Pair action should not be available for non-emulator wear devices
    assertFalse(event.presentation.isVisible)
    assertFalse(event.presentation.isEnabled)
  }

  @Test
  fun testPairActionAvailableForPhysicalPhone() = runBlocking {
    val pairAction = PairWearableDeviceAction()
    val deviceTemplate =
      FakeDeviceTemplate(
        DeviceProperties.buildForTest {
          model = "phoneDevice"
          icon = StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR
          wearPairingId = "phonePairing1"
          isVirtual = false
          deviceType = DeviceType.HANDHELD
        }
      )
    val event =
      TestActionEvent.createTestEvent(
        pairAction,
        SimpleDataContext.getSimpleContext(
          DEVICE_ROW_DATA_KEY,
          DeviceRowData.create(FakeDeviceHandle(this, deviceTemplate, deviceTemplate.properties), emptyList()),
        ),
      )
    WearPairingManager.getInstance().loadSettings(listOf(PairingDeviceState("wearPairing1"), PairingDeviceState("phonePairing1")), listOf())

    pairAction.update(event)
    // Pair action should be available for physical phone devices
    assertTrue(event.presentation.isVisible)
    assertTrue(event.presentation.isEnabled)
  }

  @Test
  fun testPairActionAvailableForRemotePhone() = runBlocking {
    val pairAction = PairWearableDeviceAction()
    val deviceTemplate =
      FakeDeviceTemplate(
        DeviceProperties.buildForTest {
          model = "phoneDevice"
          icon = StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR
          wearPairingId = "phonePairing1"
          isVirtual = false
          isRemote = true
          deviceType = DeviceType.HANDHELD
        }
      )
    val event =
      TestActionEvent.createTestEvent(
        pairAction,
        SimpleDataContext.getSimpleContext(
          DEVICE_ROW_DATA_KEY,
          DeviceRowData.create(FakeDeviceHandle(this, deviceTemplate, deviceTemplate.properties), emptyList()),
        ),
      )
    WearPairingManager.getInstance().loadSettings(listOf(PairingDeviceState("wearPairing1"), PairingDeviceState("phonePairing1")), listOf())

    pairAction.update(event)
    // Pair action should be available for remote phone devices
    assertTrue(event.presentation.isVisible)
    assertTrue(event.presentation.isEnabled)
  }

  @Test
  fun testViewPairedDevicesActionDefaultState() {
    val viewAction = ViewPairedDevicesAction()
    val event = TestActionEvent.createTestEvent(viewAction)
    viewAction.update(event)
    assertFalse(event.presentation.isVisible)
    assertFalse(event.presentation.isEnabled)
  }

  @Test
  fun testViewPairedDevicesAction_WearPaired() = runBlocking {
    val viewAction = ViewPairedDevicesAction()
    val event = createTestEventWithDeviceRowData(viewAction, wearDeviceTemplate)

    // Pair the wear device
    WearPairingManager.getInstance()
      .loadSettings(
        listOf(PairingDeviceState("wearPairing1"), PairingDeviceState("phonePairing1")),
        listOf(
          PairingConnectionsState().apply {
            phoneId = "phonePairing1"
            wearDeviceIds.add("wearPairing1")
          }
        ),
      )

    viewAction.update(event)
    assertTrue(event.presentation.isVisible)
    assertTrue(event.presentation.isEnabled)
  }

  @Test
  fun testViewPairedDevicesAction_GlassesPaired_FlagEnabled() = runBlocking {
    StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.override(true)
    try {
      val viewAction = ViewPairedDevicesAction()
      val event = createTestEventWithDeviceRowData(viewAction, glassesDeviceTemplate)

      viewAction.update(event)
      assertTrue(event.presentation.isVisible)
      assertTrue(event.presentation.isEnabled)
    } finally {
      StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.clearOverride()
    }
  }

  @Test
  fun testViewPairedDevicesAction_GlassesPaired_FlagDisabled() = runBlocking {
    StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.override(false)
    try {
      val viewAction = ViewPairedDevicesAction()
      val event = createTestEventWithDeviceRowData(viewAction, glassesDeviceTemplate)

      viewAction.update(event)
      assertFalse(event.presentation.isVisible)
      assertFalse(event.presentation.isEnabled)
    } finally {
      StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.clearOverride()
    }
  }

  @Test
  fun testViewPairedDevicesAction_PhoneWithGlasses_FlagEnabled() = runBlocking {
    StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.override(true)
    try {
      val viewAction = ViewPairedDevicesAction()
      val event = createTestEventWithDeviceRowData(viewAction, phoneWithGlassesDeviceTemplate)

      viewAction.update(event)
      assertTrue(event.presentation.isVisible)
      assertTrue(event.presentation.isEnabled)
    } finally {
      StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.clearOverride()
    }
  }

  @Test
  fun testViewPairedDevicesAction_PhonePairedToGlassesDevice() = runBlocking {
    StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.override(true)
    try {
      val viewAction = ViewPairedDevicesAction()
      val mockDeviceProvisionerService = mock(DeviceProvisionerService::class.java)
      val mockDeviceProvisioner = mock(DeviceProvisioner::class.java)
      whenever(mockDeviceProvisionerService.deviceProvisioner).thenReturn(mockDeviceProvisioner)

      val phoneTemplate =
        FakeDeviceTemplate(
          DeviceProperties.buildForTest {
            model = "phoneDevice"
            icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
            deviceType = DeviceType.HANDHELD
          }
        )
      val phoneHandle = FakeDeviceHandle(this, phoneTemplate, phoneTemplate.properties)

      val glassesProperties =
        DeviceProperties.buildForTest {
          model = "glassesDevice"
          icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
          deviceType = DeviceType.AI_GLASSES
          pairedPhoneId = phoneHandle.id
        }
      val glassesHandle = FakeDeviceHandle(this, glassesDeviceTemplate, glassesProperties)

      whenever(mockDeviceProvisioner.devices).thenReturn(MutableStateFlow(listOf(glassesHandle)))

      val mockProject = mock(Project::class.java)
      whenever(mockProject.getService(DeviceProvisionerService::class.java)).thenReturn(mockDeviceProvisionerService)

      val event =
        TestActionEvent.createTestEvent(
          viewAction,
          SimpleDataContext.builder()
            .add(DEVICE_ROW_DATA_KEY, DeviceRowData.create(phoneHandle, emptyList()))
            .add(CommonDataKeys.PROJECT, mockProject)
            .build(),
        )

      viewAction.update(event)
      assertTrue(event.presentation.isVisible)
      assertTrue(event.presentation.isEnabled)
    } finally {
      StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.clearOverride()
    }
  }
}
