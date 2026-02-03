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
package com.android.tools.idea.glassespairing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.adblib.ConnectedDevice
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.ActivationAction
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.deviceprovisioner.EmptyIcon
import com.android.sdklib.deviceprovisioner.testing.FakeDeviceProvisionerPlugin
import com.android.tools.adtui.compose.TestComposeWizard
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.UsageTrackerWriter
import com.android.tools.idea.glassespairing.LaunchState.*
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Message // TODO: android-merge; needed for the new interface
import com.google.wireless.android.play.playlog.proto.ClientAnalytics
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GlassesPairingEvent
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

@RunsInEdt
class GlassesPairingWizardTest {
  @get:Rule val edtRule = EdtRule()
  @get:Rule val composeTestRule = createStudioComposeTestRule()

  @Test
  fun testGlassesPairingWizard() {
    val coroutineScope = CoroutineScope(UnconfinedTestDispatcher())
    val tracker = TestTracker()
    UsageTracker.setWriterForTest(tracker)

    try {
      val phone =
        FakeDeviceProvisionerPlugin.FakeDeviceHandle(
          "p1",
          coroutineScope,
          DeviceState.Disconnected(
            DeviceProperties.buildForTest {
              icon = EmptyIcon.DEFAULT
              manufacturer = "Google"
              model = "Pixel 9"
              deviceType = DeviceType.HANDHELD
              androidVersion = AndroidVersion(36, 1)
            }
          ),
        )
      val glasses =
        FakeDeviceProvisionerPlugin.FakeDeviceHandle(
          "g1",
          coroutineScope,
          DeviceState.Disconnected(
            DeviceProperties.buildForTest {
              icon = EmptyIcon.DEFAULT
              manufacturer = "Google"
              model = "AI Glasses"
              deviceType = DeviceType.AI_GLASSES
              androidVersion = AndroidVersion(36, 1)
            }
          ),
        )
      val devicesFlow = MutableStateFlow(listOf(phone, glasses))

      val pairingFlow = MutableStateFlow<PairingState>(PairingState.NotStarted)
      fun pair(g: DeviceHandle, p: DeviceHandle): Flow<PairingState> {
        assertThat(g).isSameAs(glasses)
        assertThat(p).isSameAs(phone)
        return pairingFlow
      }

      val glassesWizard = GlassesPairingWizard(null, coroutineScope, devicesFlow, glasses, ::pair, { true })
      val wizard = TestComposeWizard { with(glassesWizard) { SelectDevicePage() } }

      composeTestRule.setContent { wizard.Content() }

      composeTestRule.onNodeWithText("Select a device", substring = true).assertIsDisplayed()
      composeTestRule.onNodeWithText("Google Pixel 9").performClick()

      // Verify selection event
      assertThat(tracker.events).contains(GlassesPairingEvent.EventKind.PAIRING_DEVICE_SELECTED)

      composeTestRule.onNodeWithText("Next").performClick()

      // Verify initiated event
      assertThat(tracker.events).contains(GlassesPairingEvent.EventKind.PAIRING_INITIATED)

      pairingFlow.value = PairingState.Launching("Pixel 9", Booting, "AI Glasses", Booting)

      composeTestRule.onNodeWithText("Starting devices...").assertIsDisplayed()
      composeTestRule.onNodeWithText("Waiting for AI Glasses to boot").assertIsDisplayed()

      pairingFlow.value = PairingState.Pairing("Pairing in progress...")

      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithText("Pairing in progress...").assertIsDisplayed()

      pairingFlow.value = PairingState.Complete

      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithText("Pairing complete.").assertIsDisplayed()

      // Verify success event
      assertThat(tracker.events).contains(GlassesPairingEvent.EventKind.SHOW_SUCCESSFUL_PAIRING)

      composeTestRule.onNodeWithText("Previous").assertIsNotEnabled()
      composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
      composeTestRule.onNodeWithText("Cancel").assertIsNotEnabled()
      composeTestRule.onNodeWithText("Finish").assertIsEnabled().performClick()

      wizard.awaitClose()
    } finally {
      coroutineScope.cancel()
      UsageTracker.cleanAfterTesting()
    }
  }

  @Test
  fun testLaunchAvds(): Unit = runTest {
    val glasses = TestDeviceHandle(this@runTest, "G", activationDelay = 8.seconds, bootDelay = 5.seconds)
    val phone = TestDeviceHandle(this@runTest, "P", activationDelay = 8.seconds, bootDelay = 5.seconds)

    val states = flow { launchGlassesAndPhone(glasses, phone) }.toList()

    assertThat(states)
      .containsExactly(
        createLaunchingState(phone = Launching, glasses = Launching),
        createLaunchingState(phone = Booting, glasses = Booting),
        createLaunchingState(phone = Ready, glasses = Ready),
      )
      .inOrder()
  }

  @OptIn(ExperimentalTime::class)
  @Test
  fun testLaunchAvds_glassesAlreadyRunning(): Unit = runTest {
    val glasses = TestDeviceHandle(this@runTest, "G", activationDelay = 0.seconds)
    val phone = TestDeviceHandle(this@runTest, "P", activationDelay = 4.seconds, bootDelay = 4.seconds)

    glasses.activationAction!!.activate()

    val duration =
      testTimeSource.measureTime {
        val states = flow { launchGlassesAndPhone(glasses, phone) }.toList()

        assertThat(states)
          .containsExactly(
            createLaunchingState(phone = Launching, glasses = Ready),
            createLaunchingState(phone = Booting, glasses = Ready),
            createLaunchingState(phone = Ready, glasses = Ready),
          )
          .inOrder()
      }

    assertThat(duration).isLessThan(9.seconds)
  }

  @Test
  fun testPairingErrors() {
    val coroutineScope = CoroutineScope(UnconfinedTestDispatcher())
    val tracker = TestTracker()
    UsageTracker.setWriterForTest(tracker)

    try {
      val phone =
        FakeDeviceProvisionerPlugin.FakeDeviceHandle(
          "p1",
          coroutineScope,
          DeviceState.Disconnected(
            DeviceProperties.buildForTest {
              icon = EmptyIcon.DEFAULT
              manufacturer = "Google"
              model = "Pixel 9"
              deviceType = DeviceType.HANDHELD
              androidVersion = AndroidVersion(36, 1)
            }
          ),
        )
      val glasses =
        FakeDeviceProvisionerPlugin.FakeDeviceHandle(
          "g1",
          coroutineScope,
          DeviceState.Disconnected(
            DeviceProperties.buildForTest {
              icon = EmptyIcon.DEFAULT
              manufacturer = "Google"
              model = "AI Glasses"
              deviceType = DeviceType.AI_GLASSES
              androidVersion = AndroidVersion(36, 1)
            }
          ),
        )
      val devicesFlow = MutableStateFlow(listOf(phone, glasses))

      val pairingFlow = MutableStateFlow<PairingState>(PairingState.NotStarted)
      fun pair(g: DeviceHandle, p: DeviceHandle): Flow<PairingState> {
        return pairingFlow
      }

      val glassesWizard = GlassesPairingWizard(null, coroutineScope, devicesFlow, glasses, ::pair, { true })
      val wizard = TestComposeWizard { with(glassesWizard) { SelectDevicePage() } }

      composeTestRule.setContent { wizard.Content() }

      // Select device and start
      composeTestRule.onNodeWithText("Google Pixel 9").performClick()
      composeTestRule.onNodeWithText("Next").performClick()

      // 1. Test UI_CDM_ASSOCIATION_FAILED
      pairingFlow.value =
        PairingState.Error(
          heading = "Error pairing AI Glasses",
          detailText = "Failed to create companion device association with glasses device.",
        )
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithText("Error pairing AI Glasses").assertIsDisplayed()
      composeTestRule.onNodeWithText("Failed to create companion device association with glasses device.").assertIsDisplayed()
      tracker.events.clear()

      // 2. Test WORKER_GLASSES_CORE_CONNECTION_FAILED
      pairingFlow.value =
        PairingState.Error(
          heading = "Error pairing AI Glasses",
          detailText = "Failed to connect to device. Please make sure to accept all permissions on the phone.",
        )
      composeTestRule.waitForIdle()
      composeTestRule
        .onNodeWithText("Failed to connect to device. Please make sure to accept all permissions on the phone.")
        .assertIsDisplayed()
      tracker.events.clear()

      // 3. Test WORKER_BOND_FAILED
      pairingFlow.value =
        PairingState.Error(heading = "Error pairing AI Glasses", detailText = "Failed to bluetooth bond to glasses device.")
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithText("Failed to bluetooth bond to glasses device.").assertIsDisplayed()

      composeTestRule.onNodeWithText("Cancel").performClick()
      wizard.awaitClose()
    } finally {
      coroutineScope.cancel()
      UsageTracker.cleanAfterTesting()
    }
  }

  @Test
  fun testShowEnforcesSingleInstance() = runTest {
    val coroutineScope = CoroutineScope(UnconfinedTestDispatcher())
    val devicesFlow = MutableStateFlow(emptyList<DeviceHandle>())
    val glasses =
      FakeDeviceProvisionerPlugin.FakeDeviceHandle(
        "g1",
        coroutineScope,
        DeviceState.Disconnected(
          DeviceProperties.buildForTest {
            icon = EmptyIcon.DEFAULT
            manufacturer = "Google"
            model = "AI Glasses"
            deviceType = DeviceType.AI_GLASSES
            androidVersion = AndroidVersion(36, 1)
          }
        ),
      )

    val controller1 = TestWizardController()
    val controller2 = TestWizardController()
    val controllers = mutableListOf(controller1, controller2)

    val job1 = launch { GlassesPairingWizard.showCore(null, null, devicesFlow, glasses) { _, _, _, _, _, _ -> controllers.removeAt(0) } }

    // Wait for first wizard to be active
    delay(100)
    assertThat(controller1.showCalled).isTrue()

    // Try to show again for same glasses
    val result2 = GlassesPairingWizard.showCore(null, null, devicesFlow, glasses) { _, _, _, _, _, _ -> controllers.removeAt(0) }

    assertThat(result2).isNull()
    assertThat(controller1.focusedCount).isEqualTo(1)
    assertThat(controller2.showCalled).isFalse()

    // Close first wizard
    controller1.close(false)
    job1.join()
  }

  @Test
  fun testShowAllowsMultipleDevices() = runTest {
    val coroutineScope = CoroutineScope(UnconfinedTestDispatcher())
    val devicesFlow = MutableStateFlow(emptyList<DeviceHandle>())
    val glasses1 =
      FakeDeviceProvisionerPlugin.FakeDeviceHandle(
        "g1",
        coroutineScope,
        DeviceState.Disconnected(
          DeviceProperties.buildForTest {
            icon = EmptyIcon.DEFAULT
            manufacturer = "Google"
            model = "AI Glasses 1"
            deviceType = DeviceType.AI_GLASSES
            androidVersion = AndroidVersion(36, 1)
          }
        ),
      )
    val glasses2 =
      FakeDeviceProvisionerPlugin.FakeDeviceHandle(
        "g2",
        coroutineScope,
        DeviceState.Disconnected(
          DeviceProperties.buildForTest {
            icon = EmptyIcon.DEFAULT
            manufacturer = "Google"
            model = "AI Glasses 2"
            deviceType = DeviceType.AI_GLASSES
            androidVersion = AndroidVersion(36, 1)
          }
        ),
      )

    val controller1 = TestWizardController()
    val controller2 = TestWizardController()
    val controllers = mutableListOf(controller1, controller2)

    val job1 = launch { GlassesPairingWizard.showCore(null, null, devicesFlow, glasses1) { _, _, _, _, _, _ -> controllers.removeAt(0) } }

    delay(50)
    assertThat(controller1.showCalled).isTrue()

    val job2 = launch { GlassesPairingWizard.showCore(null, null, devicesFlow, glasses2) { _, _, _, _, _, _ -> controllers.removeAt(0) } }

    delay(50)
    // Both should be running
    assertThat(controller2.showCalled).isTrue()

    controller1.close(false)
    controller2.close(false)
    job1.join()
    job2.join()
  }
}

private class TestWizardController : WizardController {
  val completion = CompletableDeferred<Boolean>()
  var focusedCount = 0
  var showCalled = false

  override suspend fun show(): Boolean {
    showCalled = true
    return completion.await()
  }

  override fun focus() {
    focusedCount++
  }

  fun close(result: Boolean) {
    completion.complete(result)
  }
}

// TODO: android-merge; the UsageTrackerWrite interface had changed upstream (no generic, different callback to override)
private class TestTracker : UsageTrackerWriter() {
  val events = CopyOnWriteArrayList<GlassesPairingEvent.EventKind>()

  override fun logDetails(logEvent: ClientAnalytics.LogEvent.Builder) {}

  // TODO: android-merge: changed as in upstream
  // override fun processMessage(eventTimeMs: Long, studioEvent: AndroidStudioEvent.Builder) {
  override fun processEvent(studioEvent: AndroidStudioEvent.Builder): Message.Builder? {
    if (studioEvent.kind == AndroidStudioEvent.EventKind.GLASSES_PAIRING_EVENT) {
      events.add(studioEvent.glassesPairingEvent.kind)
    }
    return studioEvent // TODO: android-merge; changed as in upstream
  }

  override fun close() {}

  override fun flush() {}
}

private class TestDeviceHandle(
  scope: CoroutineScope,
  val name: String,
  val activationDelay: Duration,
  val bootDelay: Duration = 0.seconds,
) :
  FakeDeviceProvisionerPlugin.FakeDeviceHandle(
    name,
    scope,
    initialState =
      DeviceState.Disconnected(
        DeviceProperties.buildForTest {
          model = name
          icon = EmptyIcon.DEFAULT
        }
      ),
  ) {
  override var activationAction: ActivationAction? =
    object : FakeDeviceProvisionerPlugin.FakeActivationAction() {
      override suspend fun activate() {
        delay(activationDelay)
        stateFlow.value =
          DeviceState.Connected(
            properties = state.properties,
            connectedDevice = mock<ConnectedDevice>(),
            isTransitioning = true,
            isReady = false,
            status = "Online",
          )
        scope.launch {
          delay(bootDelay)
          stateFlow.value =
            DeviceState.Connected(
              properties = state.properties,
              connectedDevice = mock<ConnectedDevice>(),
              isTransitioning = false,
              isReady = true,
              status = "Online",
            )
        }
      }
    }
}

private fun createLaunchingState(phone: LaunchState, glasses: LaunchState) =
  PairingState.Launching(phoneName = "P", phoneLaunchState = phone, glassesName = "G", glassesLaunchState = glasses)
