/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.adb.executeShellCommand
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import layoutinspector.view.inspection.LayoutInspectorViewProtocol as ViewProtocol

private val MODERN_PROCESS = MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

class AppInspectionInspectorClientTest {
  private val inspectionRule = AppInspectionInspectorRule()
  private val inspectorRule = LayoutInspectorRule(inspectionRule.createInspectorClientProvider()) { listOf(MODERN_PROCESS.name) }

  @get:Rule
  val ruleChain = RuleChain.outerRule(inspectionRule).around(inspectorRule)

  @Test
  fun clientCanConnectDisconnectAndReconnect() {
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(inspectorRule.inspectorClient.isConnected).isTrue()

    inspectorRule.processNotifier.fireDisconnected(MODERN_PROCESS)
    assertThat(inspectorRule.inspectorClient.isConnected).isFalse()

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(inspectorRule.inspectorClient.isConnected).isTrue()
  }

  @Test
  fun inspectorStartsFetchingContinuouslyOnConnectIfLiveMode() = runBlocking {
    InspectorClientSettings.isCapturingModeOn = true

    val startFetchReceived = CompletableDeferred<Unit>()
    inspectionRule.viewInspector.addCommandListener(ViewProtocol.Command.SpecializedCase.START_FETCH_COMMAND) { command ->
      assertThat(command.startFetchCommand.continuous).isTrue()
      startFetchReceived.complete(Unit)
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    startFetchReceived.await() // If here, we already successfully connected (and sent an initial command)
    assertThat(inspectorRule.inspectorClient).isInstanceOf(AppInspectionInspectorClient::class.java)
  }

  @Test
  fun inspectorRequestsSingleFetchIfSnapshotMode() = runBlocking {
    InspectorClientSettings.isCapturingModeOn = false

    val startFetchReceived = CompletableDeferred<Unit>()
    inspectionRule.viewInspector.addCommandListener(ViewProtocol.Command.SpecializedCase.START_FETCH_COMMAND) { command ->
      assertThat(command.startFetchCommand.continuous).isFalse()
      startFetchReceived.complete(Unit)
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    startFetchReceived.await() // If here, we already successfully connected (and sent an initial command)
    assertThat(inspectorRule.inspectorClient).isInstanceOf(AppInspectionInspectorClient::class.java)
  }

  @Test
  fun testViewDebugAttributesApplicationPackageSetAndReset() {
    inspectorRule.attachDevice(MODERN_DEVICE)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(MODERN_PROCESS.name)

    // Disconnect directly instead of calling fireDisconnected - otherwise, we don't have an easy way to wait for the disconnect to
    // happen on a background thread
    inspectorRule.launcher.disconnectActiveClient()
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isNull()
    // No other attributes were modified
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(2)
  }

  @Test
  fun testViewDebugAttributesApplicationPackageOverriddenAndReset() {
    inspectorRule.attachDevice(MODERN_PROCESS.device)
    inspectorRule.adbRule.bridge.executeShellCommand(MODERN_PROCESS.device,
                                                     "settings put global debug_view_attributes_application_package com.example.another-app")

    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo("com.example.another-app")

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(MODERN_PROCESS.name)

    inspectorRule.launcher.disconnectActiveClient()
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isNull()
  }

  @Test
  fun testViewDebugAttributesApplicationPackageNotOverriddenIfMatching() {
    inspectorRule.attachDevice(MODERN_PROCESS.device)
    inspectorRule.adbRule.bridge.executeShellCommand(MODERN_PROCESS.device,
                                                     "settings put global debug_view_attributes_application_package ${MODERN_PROCESS.name}")

    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(MODERN_PROCESS.name)
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(1)

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(MODERN_PROCESS.name)
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(1)

    inspectorRule.launcher.disconnectActiveClient()
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(MODERN_PROCESS.name)
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(1)
  }

  @Test
  fun inspectorSendsStopFetchCommand() = runBlocking {
    val stopFetchReceived = CompletableDeferred<Unit>()
    inspectionRule.viewInspector.addCommandListener(ViewProtocol.Command.SpecializedCase.STOP_FETCH_COMMAND) {
      stopFetchReceived.complete(Unit)
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    inspectorRule.inspectorClient.stopFetching()
    stopFetchReceived.await()
  }

  @Test
  fun inspectorFiresErrorOnErrorEvent() = runBlocking {
    val startFetchError = "Failed to start fetching or whatever"

    inspectionRule.viewInspector.addCommandListener(ViewProtocol.Command.SpecializedCase.START_FETCH_COMMAND) {
      inspectionRule.viewInspector.connection.sendEvent {
        errorEventBuilder.apply {
          message = startFetchError
        }
      }
    }

    val error = CompletableDeferred<String>()
    inspectorRule.launcher.addClientChangedListener { client ->
      client.registerErrorCallback { error.complete(it) }
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(error.await()).isEqualTo(startFetchError)
  }
}