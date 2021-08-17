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
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.adb.executeShellCommand
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.INCOMPATIBLE_LIBRARY_MESSAGE
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.PROGUARDED_LIBRARY_MESSAGE
import com.android.tools.idea.layoutinspector.pipeline.appinspection.inspectors.sendEvent
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewLayoutInspectorClient
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.layoutinspector.util.ReportingCountDownLatch
import com.android.tools.idea.protobuf.ByteString
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol as ComposeProtocol
import layoutinspector.view.inspection.LayoutInspectorViewProtocol as ViewProtocol

private val MODERN_PROCESS = MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

/** Timeout used in this test. While debugging, you may want extend the timeout */
private const val TIMEOUT = 1L
private val TIMEOUT_UNIT = TimeUnit.SECONDS

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
    val screenshotTypeUpdated = ReportingCountDownLatch(1)
    inspectionRule.viewInspector.listenWhen({ it.hasUpdateScreenshotTypeCommand() }) { command ->
      assertThat(command.updateScreenshotTypeCommand.type).isEqualTo(ViewProtocol.Screenshot.Type.BITMAP)
      assertThat(command.updateScreenshotTypeCommand.scale).isEqualTo(1.0f)
      screenshotTypeUpdated.countDown()
    }

    inspectorRule.launcher.addClientChangedListener { client ->
      client.registerTreeEventCallback { data ->
        (client as AppInspectionInspectorClient).updateScreenshotType(AndroidWindow.ImageType.BITMAP_AS_REQUESTED)
      }
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    screenshotTypeUpdated.await(TIMEOUT, TIMEOUT_UNIT)
  }

  @Test
  fun viewClientOnlyHandlesMostRecentLayoutEvent() {
    // This test will send two batches of layout events, to verify that we not only process the last event of a
    // batch, but that once processed, new events can be handled afterwards.
    var handlingFirstBatch = true

    inspectionRule.viewInspector.interceptWhen({ it.hasStartFetchCommand() }) {
      inspectionRule.viewInspector.connection.sendEvent {
        // We must always send roots at least once before the very first layout event
        rootsEventBuilder.apply {
          addIds(1)
        }
      }

      for (i in 0..10) {
        inspectionRule.viewInspector.connection.sendEvent {
          layoutEventBuilder.apply {
            rootViewBuilder.apply {
              id = 1
            }
            screenshotBuilder.apply {
              bytes = ByteString.copyFrom(byteArrayOf(i.toByte()))
            }
          }
        }
      }

      ViewProtocol.Response.newBuilder().setStartFetchResponse(ViewProtocol.StartFetchResponse.getDefaultInstance()).build()
    }

    inspectionRule.viewInspector.interceptWhen({ it.hasStopFetchCommand() }) {
      // Note: We don't normally spam a bunch of layout events when you stop fetching, but we do it
      // here for convenience, as stopFetching is easy to trigger in the test.
      for (i in 11..20) {
        inspectionRule.viewInspector.connection.sendEvent {
          layoutEventBuilder.apply {
            rootViewBuilder.apply {
              id = 1
            }
            screenshotBuilder.apply {
              bytes = ByteString.copyFrom(byteArrayOf(i.toByte()))
            }
          }
        }
      }

      ViewProtocol.Response.newBuilder().setStopFetchResponse(ViewProtocol.StopFetchResponse.getDefaultInstance()).build()
    }

    val treeEventsHandled = ReportingCountDownLatch(1)
    inspectorRule.launcher.addClientChangedListener { client ->
      client.registerTreeEventCallback { data ->
        (data as ViewLayoutInspectorClient.Data).viewEvent.let { viewEvent ->
          assertThat(viewEvent.rootView.id).isEqualTo(1)

          if (handlingFirstBatch) {
            handlingFirstBatch = false
            assertThat(viewEvent.screenshot.bytes.byteAt(0)).isEqualTo(10.toByte())
            client.stopFetching() // Triggers second batch of layout events
          }
          else {
            assertThat(viewEvent.screenshot.bytes.byteAt(0)).isEqualTo(20.toByte())
            treeEventsHandled.countDown()
          }
        }
      }
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS) // Triggers first batch of layout events
    treeEventsHandled.await(TIMEOUT, TIMEOUT_UNIT)
  }

  @Test
  fun testCapabilitiesUpdateWithoutComposeNodes() {
    val inspectorState = FakeInspectorState(inspectionRule.viewInspector, inspectionRule.composeInspector)
    inspectorState.createFakeViewTree()

    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)
    assertThat(inspectorRule.inspectorClient.capabilities).containsNoneOf(Capability.SUPPORTS_COMPOSE, Capability.SUPPORTS_SEMANTICS)
  }

  @Test
  fun testCapabilitiesUpdateWithComposeNodes() {
    val inspectorState = FakeInspectorState(inspectionRule.viewInspector, inspectionRule.composeInspector)
    inspectorState.createFakeViewTree()
    inspectorState.createFakeComposeTree(withSemantics = false)

    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)
    assertThat(inspectorRule.inspectorClient.capabilities).contains(Capability.SUPPORTS_COMPOSE)
    assertThat(inspectorRule.inspectorClient.capabilities).doesNotContain(Capability.SUPPORTS_SEMANTICS)
  }

  @Test
  fun testCapabilitiesUpdateWithComposeNodesWithSemantics() {
    val inspectorState = FakeInspectorState(inspectionRule.viewInspector, inspectionRule.composeInspector)
    inspectorState.createFakeViewTree()
    inspectorState.createFakeComposeTree(withSemantics = true)

    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)
    assertThat(inspectorRule.inspectorClient.capabilities).containsAllOf(Capability.SUPPORTS_COMPOSE, Capability.SUPPORTS_SEMANTICS)
  }

  @Test
  fun testTextViewUnderComposeNode() {
    val inspectorState = FakeInspectorState(inspectionRule.viewInspector, inspectionRule.composeInspector)
    inspectorState.createFakeViewTree()
    inspectorState.createFakeComposeTree()
    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    // Verify that the MaterialTextView from the views were placed under the ComposeViewNode: "ComposeNode" with id of -7
    val composeNode = inspectorRule.inspectorModel[-7]!!
    assertThat(composeNode.parent?.qualifiedName).isEqualTo("AndroidView")
    assertThat(composeNode.qualifiedName).isEqualTo("ComposeNode")
    assertThat(composeNode.children.single().qualifiedName).isEqualTo("com.google.android.material.textview.MaterialTextView")

    // Also verify that the ComposeView do not contain the MaterialTextView nor the RippleContainer in its children:
    val composeView = inspectorRule.inspectorModel[6]!!
    assertThat(composeView.qualifiedName).isEqualTo("android.view.ComposeView")
    assertThat(composeView.children.single().qualifiedName).isEqualTo("Surface")
  }
}
