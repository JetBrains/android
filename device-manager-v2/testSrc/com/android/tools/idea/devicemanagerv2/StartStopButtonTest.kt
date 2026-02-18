/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.adblib.utils.createChildScope
import com.android.sdklib.deviceprovisioner.DeviceActionException
import com.android.sdklib.deviceprovisioner.DeviceError
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.deviceprovisioner.EmptyIcon
import com.android.testutils.delayUntilCondition
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.testing.TestMessagesDialog
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind.VIRTUAL_LAUNCH_ACTION
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind.VIRTUAL_STOP_ACTION
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.RuleChain
import icons.StudioIcons
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartStopButtonTest {

  private val usageTrackerRule = UsageTrackerRule()

  // Replace executors with the test dispatcher, so that we can use advanceUntilIdle to
  // execute all consequences of test actions before making assertions.
  @get:Rule val ruleChain = RuleChain(ApplicationRule(), usageTrackerRule)

  @Test
  fun enabled(): Unit = runTest {
    val handle =
      FakeDeviceHandle(
        this.createChildScope(),
        initialProperties =
          DeviceProperties.buildForTest {
            isVirtual = true
            icon = EmptyIcon.DEFAULT
          },
      )
    assertThat(handle.state).isInstanceOf(DeviceState.Disconnected::class.java)
    handle.activationAction.presentation.update { it.copy(enabled = true) }
    handle.deactivationAction.presentation.update { it.copy(enabled = false) }
    handle.pairGlassesAction.presentation.update { it.copy(enabled = false) }
    val button = StartStopButton(handle, handle.activationAction, handle.deactivationAction, null, handle.pairGlassesAction)

    assertThat(button.isEnabled).isTrue()
    assertThat(button.baseIcon).isEqualTo(StudioIcons.Avd.RUN)

    withContext(Dispatchers.EDT) { button.doClick() }
    advanceUntilIdle()

    assertThat(handle.activationAction.invoked).isEqualTo(1)

    handle.activationAction.presentation.update { it.copy(enabled = false) }
    handle.deactivationAction.presentation.update { it.copy(enabled = true) }

    delayUntilCondition(200) {
      advanceUntilIdle()
      button.baseIcon == StudioIcons.Avd.STOP && button.isEnabled
    }

    assertThat(button.baseIcon).isEqualTo(StudioIcons.Avd.STOP)
    assertThat(button.isEnabled).isTrue()
    assertThat(usageTrackerRule.deviceManagerEventKinds()).containsExactly(VIRTUAL_LAUNCH_ACTION)

    withContext(Dispatchers.EDT) { button.doClick() }
    advanceUntilIdle()

    assertThat(handle.deactivationAction.invoked).isEqualTo(1)

    handle.activationAction.presentation.update { it.copy(enabled = true) }
    handle.deactivationAction.presentation.update { it.copy(enabled = false) }

    delayUntilCondition(200) {
      advanceUntilIdle()
      button.baseIcon == StudioIcons.Avd.RUN
    }

    assertThat(button.baseIcon).isEqualTo(StudioIcons.Avd.RUN)
    assertThat(usageTrackerRule.deviceManagerEventKinds()).containsExactly(VIRTUAL_LAUNCH_ACTION, VIRTUAL_STOP_ACTION)

    handle.scope.cancel()
  }

  @Test
  fun activationError() = runTest {
    val handle =
      FakeDeviceHandle(
        this.createChildScope(),
        initialProperties =
          DeviceProperties.buildForTest {
            isVirtual = true
            icon = EmptyIcon.DEFAULT
          },
      )
    handle.activationAction.presentation.update { it.copy(enabled = true) }
    handle.activationAction.exception = DeviceActionException("Activation error")
    handle.deactivationAction.presentation.update { it.copy(enabled = false) }

    val button = StartStopButton(handle, handle.activationAction, handle.deactivationAction, null, handle.pairGlassesAction)

    val dialog = TestMessagesDialog(Messages.OK)
    TestDialogManager.setTestDialog(dialog)

    withContext(Dispatchers.EDT) { button.doClick() }

    delayUntilCondition(200) {
      advanceUntilIdle()
      dialog.displayedMessage == "Activation error"
    }
    assertThat(dialog.displayedMessage).isEqualTo("Activation error")
    handle.scope.cancel()
  }

  @Test
  fun pairableDevice() = runTest {
    val handle =
      FakeDeviceHandle(
        this.createChildScope(),
        initialProperties =
          DeviceProperties.buildForTest {
            isVirtual = true
            deviceType = DeviceType.AI_GLASSES
            icon = EmptyIcon.DEFAULT
          },
      )
    assertThat(handle.state).isInstanceOf(DeviceState.Disconnected::class.java)
    handle.activationAction.presentation.update { it.copy(enabled = true) }
    handle.deactivationAction.presentation.update { it.copy(enabled = false) }
    handle.pairGlassesAction.presentation.update { it.copy(enabled = true) }

    val button = StartStopButton(handle, handle.activationAction, handle.deactivationAction, null, handle.pairGlassesAction)

    advanceUntilIdle()
    SwingUtilities.invokeAndWait {}

    assertThat(button.isEnabled).isTrue()
    assertThat(button.baseIcon).isEqualTo(StudioIcons.Common.LINK)

    withContext(Dispatchers.EDT) { button.doClick() }
    advanceUntilIdle()

    assertThat(handle.pairGlassesAction.invoked).isEqualTo(1)
    handle.scope.cancel()
  }

  @Test
  fun repairableDevice() = runTest {
    val scope = createChildScope()
    val handle = FakeDeviceHandle(scope)
    val button =
      StartStopButton(handle, handle.activationAction, handle.deactivationAction, handle.repairDeviceAction, handle.pairGlassesAction)
    // Disable activation, since StartStopButton favors it over repair
    handle.activationAction.presentation.update { it.copy(enabled = false) }
    handle.deactivationAction.presentation.update { it.copy(enabled = false) }

    class TestError : DeviceError {
      override val severity = DeviceError.Severity.ERROR
      override val message = "error"
    }

    assertThat(button.baseIcon).isEqualTo(StudioIcons.Avd.RUN)

    handle.stateFlow.update {
      DeviceState.Disconnected(
        DeviceProperties.buildForTest { icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE },
        isTransitioning = false,
        "Disconnected",
        error = TestError(),
      )
    }
    handle.repairDeviceAction.presentation.update { it.copy(enabled = true, icon = AllIcons.Actions.Download) }

    delayUntilCondition(200) {
      advanceUntilIdle()
      button.baseIcon == AllIcons.Actions.Download
    }
    assertThat(button.baseIcon).isEqualTo(AllIcons.Actions.Download)

    handle.repairDeviceAction.presentation.update { it.copy(enabled = false) }

    delayUntilCondition(200) {
      advanceUntilIdle()
      button.baseIcon == StudioIcons.Avd.RUN
    }
    assertThat(button.baseIcon).isEqualTo(StudioIcons.Avd.RUN)

    scope.cancel()
  }
}
