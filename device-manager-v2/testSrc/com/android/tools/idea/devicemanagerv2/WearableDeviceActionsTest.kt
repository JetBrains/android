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

import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.tools.idea.wearpairing.PairingConnectionsState
import com.android.tools.idea.wearpairing.PairingDeviceState
import com.android.tools.idea.wearpairing.WearPairingManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TestActionEvent
import icons.StudioIcons
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WearableDeviceActionsTest {
  @get:Rule val applicationRule = ApplicationRule()

  private val wearDeviceTemplate =
    FakeDeviceTemplate(
      DeviceProperties.buildForTest {
        model = "wearDevice"
        icon = StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR
        wearPairingId = "wearPairing1"
      }
    )

  private suspend fun createTestEventWithDeviceRowData(action: AnAction): AnActionEvent =
    coroutineScope {
      TestActionEvent.createTestEvent(
        action,
        SimpleDataContext.getSimpleContext(
          DEVICE_ROW_DATA_KEY,
          DeviceRowData.create(
            FakeDeviceHandle(this, wearDeviceTemplate, wearDeviceTemplate.properties),
            emptyList(),
          ),
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
}
