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

import com.android.tools.app.inspection.AppInspection
import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.adb.executeShellCommand
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.INCOMPATIBLE_LIBRARY_MESSAGE
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.PROGUARDED_LIBRARY_MESSAGE
import com.android.tools.idea.layoutinspector.pipeline.appinspection.inspectors.sendEvent
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewLayoutInspectorClient
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol as ComposeProtocol
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
    inspectionRule.viewInspector.listenWhen({ it.hasStartFetchCommand() }) { command ->
      assertThat(command.startFetchCommand.continuous).isTrue()
      startFetchReceived.complete(Unit)
    }

    // Initial fetch additionally triggers requests for composables
    val composeCommands = ArrayBlockingQueue<ComposeProtocol.Command>(1)
    inspectionRule.composeInspector.listenWhen({ true }) { command ->
      composeCommands.add(command)
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    startFetchReceived.await() // If here, we already successfully connected (and sent an initial command)
    assertThat(inspectorRule.inspectorClient).isInstanceOf(AppInspectionInspectorClient::class.java)

    // View Inspector layout event -> Compose Inspector get composables command
    composeCommands.take().let { command ->
      assertThat(command.specializedCase).isEqualTo(ComposeProtocol.Command.SpecializedCase.GET_COMPOSABLES_COMMAND)
    }
  }

  @Test
  fun inspectorRequestsSingleFetchIfSnapshotMode() = runBlocking {
    InspectorClientSettings.isCapturingModeOn = false

    val startFetchReceived = CompletableDeferred<Unit>()
    inspectionRule.viewInspector.listenWhen({ it.hasStartFetchCommand() }) { command ->
      assertThat(command.startFetchCommand.continuous).isFalse()
      startFetchReceived.complete(Unit)
    }

    // Initial fetch additionally triggers requests for composables
    val composeCommands = ArrayBlockingQueue<ComposeProtocol.Command>(2)
    inspectionRule.composeInspector.listenWhen({ true }) { command ->
      composeCommands.add(command)
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    startFetchReceived.await() // If here, we already successfully connected (and sent an initial command)
    assertThat(inspectorRule.inspectorClient).isInstanceOf(AppInspectionInspectorClient::class.java)

    // View Inspector layout event -> Compose Inspector get composables command
    composeCommands.take().let { command ->
      assertThat(command.specializedCase).isEqualTo(ComposeProtocol.Command.SpecializedCase.GET_COMPOSABLES_COMMAND)
    }
    // View Inspector properties event -> Compose Inspector get all parameters
    composeCommands.take().let { command ->
      assertThat(command.specializedCase).isEqualTo(ComposeProtocol.Command.SpecializedCase.GET_ALL_PARAMETERS_COMMAND)
    }

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
    inspectionRule.viewInspector.listenWhen({ it.hasStopFetchCommand() }) {
      stopFetchReceived.complete(Unit)
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    inspectorRule.inspectorClient.stopFetching()
    stopFetchReceived.await()
  }

  @Test
  fun inspectorFiresErrorOnErrorEvent() = runBlocking {
    val startFetchError = "Failed to start fetching or whatever"

    inspectionRule.viewInspector.interceptWhen({ it.hasStartFetchCommand() }) {
      inspectionRule.viewInspector.connection.sendEvent {
        errorEventBuilder.apply {
          message = startFetchError
        }
      }

      ViewProtocol.Response.newBuilder().setStartFetchResponse(ViewProtocol.StartFetchResponse.getDefaultInstance()).build()
    }

    val error = CompletableDeferred<String>()
    inspectorRule.launcher.addClientChangedListener { client ->
      client.registerErrorCallback { error.complete(it) }
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(error.await()).isEqualTo(startFetchError)
  }

  @Test
  fun composeClientShowsMessageIfOlderComposeUiLibrary() {
    inspectionRule.composeInspector.createResponseStatus = AppInspection.CreateInspectorResponse.Status.VERSION_INCOMPATIBLE
    val banner = InspectorBanner(inspectorRule.project)

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)

    assertThat(banner.text.text).isEqualTo(INCOMPATIBLE_LIBRARY_MESSAGE)
  }

  @Test
  fun composeClientShowsMessageIfProguardedComposeUiLibrary() {
    inspectionRule.composeInspector.createResponseStatus = AppInspection.CreateInspectorResponse.Status.APP_PROGUARDED
    val banner = InspectorBanner(inspectorRule.project)

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)

    assertThat(banner.text.text).isEqualTo(PROGUARDED_LIBRARY_MESSAGE)
  }

  @Test
  fun inspectorTreeEventIncludesUpdateScreenshotTypeCallback() {
    val screenshotTypeUpdated = CountDownLatch(1)
    inspectionRule.viewInspector.listenWhen({ it.hasUpdateScreenshotTypeCommand() }) { command ->
      assertThat(command.updateScreenshotTypeCommand.type).isEqualTo(ViewProtocol.Screenshot.Type.BITMAP)
      assertThat(command.updateScreenshotTypeCommand.scale).isEqualTo(1.0f)
      screenshotTypeUpdated.countDown()
    }

    inspectorRule.launcher.addClientChangedListener { client ->
      client.registerTreeEventCallback { data ->
        (data as ViewLayoutInspectorClient.Data).updateScreenshotType(ViewProtocol.Screenshot.Type.BITMAP)
      }
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    screenshotTypeUpdated.await()
  }
}