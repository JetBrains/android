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
package com.android.tools.idea.devicemanagerv2

import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.idea.wearpairing.WearPairingManager
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.verifyNoInteractions

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteActionTest {

  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val disposableRule = DisposableRule()

  private val pairingManager = mock<WearPairingManager>()

  @Before
  fun setup() {
    enableHeadlessDialogs(disposableRule.disposable)
    TestDialogManager.setTestDialog(TestDialog.YES)
    ApplicationManager.getApplication()
      .replaceService(WearPairingManager::class.java, pairingManager, disposableRule.disposable)
  }

  // Regression test for b/226299557
  @Test
  fun `deleting a paired device unpairs it`() = runTest {
    runWithSingleFakeDevice {
      // the device is paired
      handle.stateFlow.value =
        DeviceState.Disconnected(
          DeviceProperties.buildForTest {
            copyFrom(handle.state.properties)
            wearPairingId = "some pairing id"
          }
        )

      DeleteAction(applicationScope = this@runTest).actionPerformed(actionEvent)
      advanceUntilIdle()

      assertThat(handle.deleteAction.invoked).isEqualTo(1)
      verifyBlocking(pairingManager) { pairingManager.removeAllPairedDevices("some pairing id") }
    }
  }

  // Regression test for b/226299557
  @Test
  fun `deleting an unpaired device does not attempt to unpair it`() = runTest {
    runWithSingleFakeDevice {
      // the device is not paired
      handle.stateFlow.value =
        DeviceState.Disconnected(
          DeviceProperties.buildForTest {
            copyFrom(handle.state.properties)
            wearPairingId = null
          }
        )

      DeleteAction(applicationScope = this@runTest).actionPerformed(actionEvent)
      advanceUntilIdle()

      assertThat(handle.deleteAction.invoked).isEqualTo(1)
      verifyNoInteractions(pairingManager)
    }
  }
}
