/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.flags.junit.FlagRule
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.deviceprovisioner.DEVICE_HANDLE_KEY
import com.android.tools.idea.deviceprovisioner.GlassesInteractivePairableDeviceHandle
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TestActionEvent
import icons.StudioIcons
import java.awt.Component
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GlassesPairingTest {
  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val wizardFlagRule = FlagRule(StudioFlags.AI_GLASSES_PHONE_EMULATOR_PAIRING_WIZARD_ENABLED, true)
  @get:Rule val reconciliationFlagRule = FlagRule(StudioFlags.AI_GLASSES_PAIRING_RECONCILIATION_ENABLED, true)

  private class FakeGlassesInteractivePairableDeviceHandle(
    val delegate: FakeDeviceHandle,
    var pairGlassesEnabled: Boolean = true,
    var unpairGlassesEnabled: Boolean = true,
  ) : GlassesInteractivePairableDeviceHandle, com.android.sdklib.deviceprovisioner.DeviceHandle by delegate {

    override fun isPairGlassesEnabled(): Boolean = pairGlassesEnabled

    override suspend fun pairGlasses(parent: Component?, project: Project?): Boolean {
      return true
    }

    override fun isUnpairGlassesEnabled(): Boolean = unpairGlassesEnabled

    override suspend fun unpairGlasses(parent: Component?) {}
  }

  private suspend fun createTestEvent(
    deviceType: DeviceType = DeviceType.AI_GLASSES,
    pairedPhoneId: DeviceId? = null,
    pairGlassesEnabled: Boolean = true,
    unpairGlassesEnabled: Boolean = true,
    action: AnAction,
  ): AnActionEvent = coroutineScope {
    val fakeHandle =
      FakeDeviceHandle(
        scope = this,
        initialProperties =
          DeviceProperties.buildForTest {
            this.deviceType = deviceType
            this.pairedPhoneId = pairedPhoneId
            this.icon = StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE
          },
      )
    val glassesHandle =
      FakeGlassesInteractivePairableDeviceHandle(
        delegate = fakeHandle,
        pairGlassesEnabled = pairGlassesEnabled,
        unpairGlassesEnabled = unpairGlassesEnabled,
      )
    TestActionEvent.createTestEvent(action, SimpleDataContext.getSimpleContext(DEVICE_HANDLE_KEY, glassesHandle))
  }

  @Test
  fun testActionsDefaultState() = runBlocking {
    val pairAction = PairGlassesAction()
    val unpairAction = UnpairGlassesAction()

    // Test when flags are disabled
    StudioFlags.AI_GLASSES_PHONE_EMULATOR_PAIRING_WIZARD_ENABLED.override(false)
    StudioFlags.AI_GLASSES_PAIRING_RECONCILIATION_ENABLED.override(false)
    try {
      val pairEvent = createTestEvent(action = pairAction)
      pairAction.update(pairEvent)
      assertFalse(pairEvent.presentation.isVisible)
      assertFalse(pairEvent.presentation.isEnabled)

      val unpairEvent = createTestEvent(action = unpairAction)
      unpairAction.update(unpairEvent)
      assertFalse(unpairEvent.presentation.isVisible)
      assertFalse(unpairEvent.presentation.isEnabled)
    } finally {
      StudioFlags.AI_GLASSES_PHONE_EMULATOR_PAIRING_WIZARD_ENABLED.override(true)
      StudioFlags.AI_GLASSES_PAIRING_RECONCILIATION_ENABLED.override(true)
    }

    // Test when device handle is not GlassesInteractivePairableDeviceHandle
    coroutineScope {
      val regularHandle = FakeDeviceHandle(this)
      val pairEvent = TestActionEvent.createTestEvent(pairAction, SimpleDataContext.getSimpleContext(DEVICE_HANDLE_KEY, regularHandle))
      pairAction.update(pairEvent)
      assertFalse(pairEvent.presentation.isVisible)
      assertFalse(pairEvent.presentation.isEnabled)

      val unpairEvent = TestActionEvent.createTestEvent(unpairAction, SimpleDataContext.getSimpleContext(DEVICE_HANDLE_KEY, regularHandle))
      unpairAction.update(unpairEvent)
      assertFalse(unpairEvent.presentation.isVisible)
      assertFalse(unpairEvent.presentation.isEnabled)
    }
  }

  @Test
  fun testUnpairedDevicePairActionState() = runBlocking {
    val pairAction = PairGlassesAction()
    val pairEvent =
      createTestEvent(deviceType = DeviceType.AI_GLASSES, pairedPhoneId = null, pairGlassesEnabled = true, action = pairAction)
    pairAction.update(pairEvent)
    assertTrue(pairEvent.presentation.isVisible)
    assertTrue(pairEvent.presentation.isEnabled)
  }

  @Test
  fun testUnpairedDevicePairActionStateDisabled() = runBlocking {
    val pairAction = PairGlassesAction()
    val pairEvent =
      createTestEvent(deviceType = DeviceType.AI_GLASSES, pairedPhoneId = null, pairGlassesEnabled = false, action = pairAction)
    pairAction.update(pairEvent)
    assertTrue(pairEvent.presentation.isVisible)
    assertFalse(pairEvent.presentation.isEnabled)
  }

  @Test
  fun testPairedDevicePairActionState() = runBlocking {
    val pairAction = PairGlassesAction()
    val event = createTestEvent(deviceType = DeviceType.AI_GLASSES, pairedPhoneId = DeviceId("Phone", false, "path"), action = pairAction)
    pairAction.update(event)
    assertFalse(event.presentation.isVisible)
    assertFalse(event.presentation.isEnabled)
  }

  @Test
  fun testUnpairedDeviceUnpairActionState() = runBlocking {
    val unpairAction = UnpairGlassesAction()
    val event = createTestEvent(deviceType = DeviceType.AI_GLASSES, pairedPhoneId = null, action = unpairAction)
    unpairAction.update(event)
    assertFalse(event.presentation.isVisible)
    assertFalse(event.presentation.isEnabled)
  }

  @Test
  fun testPairedDeviceUnpairActionState() = runBlocking {
    val unpairAction = UnpairGlassesAction()
    val unpairEvent =
      createTestEvent(
        deviceType = DeviceType.AI_GLASSES,
        pairedPhoneId = DeviceId("Phone", false, "path"),
        unpairGlassesEnabled = true,
        action = unpairAction,
      )
    unpairAction.update(unpairEvent)
    assertTrue(unpairEvent.presentation.isVisible)
    assertTrue(unpairEvent.presentation.isEnabled)
  }

  @Test
  fun testPairedDeviceUnpairActionStateDisabled() = runBlocking {
    val unpairAction = UnpairGlassesAction()
    val unpairEvent =
      createTestEvent(
        deviceType = DeviceType.AI_GLASSES,
        pairedPhoneId = DeviceId("Phone", false, "path"),
        unpairGlassesEnabled = false,
        action = unpairAction,
      )
    unpairAction.update(unpairEvent)
    assertTrue(unpairEvent.presentation.isVisible)
    assertFalse(unpairEvent.presentation.isEnabled)
  }

  @Test
  fun testActionsUnavailableForNonGlasses() = runBlocking {
    val pairAction = PairGlassesAction()
    val unpairAction = UnpairGlassesAction()

    val pairEvent = createTestEvent(deviceType = DeviceType.HANDHELD, pairedPhoneId = null, action = pairAction)
    pairAction.update(pairEvent)
    assertFalse(pairEvent.presentation.isVisible)
    assertFalse(pairEvent.presentation.isEnabled)

    val unpairEvent =
      createTestEvent(deviceType = DeviceType.HANDHELD, pairedPhoneId = DeviceId("Phone", false, "path"), action = unpairAction)
    unpairAction.update(unpairEvent)
    assertFalse(unpairEvent.presentation.isVisible)
    assertFalse(unpairEvent.presentation.isEnabled)
  }

  @Test
  fun testGlassesPairedDevicesFlow_indirectPairingsFromGlasses() = runBlocking {
    val phoneHandle =
      FakeDeviceHandle(
        scope = this,
        initialProperties =
          DeviceProperties.buildForTest {
            model = "Phone"
            deviceType = DeviceType.HANDHELD
            icon = StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE
          },
      )
    val glassesHandle =
      FakeDeviceHandle(
        scope = this,
        initialProperties =
          DeviceProperties.buildForTest {
            model = "Glasses"
            deviceType = DeviceType.AI_GLASSES
            pairedPhoneId = phoneHandle.id
            icon = StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE
          },
      )

    val flow = flowOf(listOf(phoneHandle, glassesHandle)).glassesPairedDevicesFlow()
    val result = flow.first()

    assertThat(result).containsKey(phoneHandle.id.toString())
    val phonePairings = result[phoneHandle.id.toString()]!!
    assertThat(phonePairings).hasSize(1)
    assertThat(phonePairings[0].id).isEqualTo(glassesHandle.id.toString())
  }
}
