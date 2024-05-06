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
import com.android.sdklib.deviceprovisioner.EmptyIcon
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.android.tools.idea.testing.TestMessagesDialog
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind.VIRTUAL_LAUNCH_ACTION
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind.VIRTUAL_STOP_ACTION
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.RuleChain
import icons.StudioIcons
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import javax.swing.SwingUtilities

@OptIn(ExperimentalCoroutinesApi::class)
class StartStopButtonTest {

  private val testScope = TestScope()
  private val testDispatcher = UnconfinedTestDispatcher(testScope.testScheduler)
  private val usageTrackerRule = UsageTrackerRule()

  // Replace executors with the test dispatcher, so that we can use advanceUntilIdle to
  // execute all consequences of test actions before making assertions.
  @get:Rule
  val ruleChain =
    RuleChain(
      ApplicationRule(),
      usageTrackerRule,
      AndroidExecutorsRule(
        workerThreadExecutor = testDispatcher.asExecutor(),
        diskIoThreadExecutor = testDispatcher.asExecutor(),
        uiThreadExecutor = { _, runnable -> testScope.launch { runnable.run() } }
      )
    )

  @Test
  fun enabled(): Unit =
    testScope.runTest {
      val handle =
        FakeDeviceHandle(
          this.createChildScope(),
          initialProperties =
            DeviceProperties.buildForTest {
              isVirtual = true
              icon = EmptyIcon.DEFAULT
            }
        )
      handle.activationAction.presentation.update { it.copy(enabled = true) }
      handle.deactivationAction.presentation.update { it.copy(enabled = false) }
      val button = StartStopButton(handle, handle.activationAction, handle.deactivationAction, null)

      assertThat(button.isEnabled).isTrue()
      assertThat(button.baseIcon).isEqualTo(StudioIcons.Avd.RUN)

      SwingUtilities.invokeAndWait { button.doClick() }
      advanceUntilIdle()

      assertThat(handle.activationAction.invoked).isEqualTo(1)

      handle.activationAction.presentation.update { it.copy(enabled = false) }
      handle.deactivationAction.presentation.update { it.copy(enabled = true) }
      advanceUntilIdle()

      assertThat(button.baseIcon).isEqualTo(StudioIcons.Avd.STOP)
      assertThat(button.isEnabled).isTrue()
      assertThat(usageTrackerRule.deviceManagerEventKinds()).containsExactly(VIRTUAL_LAUNCH_ACTION)

      SwingUtilities.invokeAndWait { button.doClick() }
      advanceUntilIdle()

      assertThat(handle.deactivationAction.invoked).isEqualTo(1)

      handle.activationAction.presentation.update { it.copy(enabled = true) }
      handle.deactivationAction.presentation.update { it.copy(enabled = false) }
      advanceUntilIdle()

      assertThat(button.baseIcon).isEqualTo(StudioIcons.Avd.RUN)
      assertThat(usageTrackerRule.deviceManagerEventKinds())
        .containsExactly(VIRTUAL_LAUNCH_ACTION, VIRTUAL_STOP_ACTION)

      handle.scope.cancel()
    }

  @Test
  fun activationError(): Unit =
    testScope.runTest {
      val handle =
        FakeDeviceHandle(
          this.createChildScope(),
          initialProperties =
            DeviceProperties.buildForTest {
              isVirtual = true
              icon = EmptyIcon.DEFAULT
            }
        )
      handle.activationAction.presentation.update { it.copy(enabled = true) }
      handle.activationAction.exception = DeviceActionException("Activation error")
      handle.deactivationAction.presentation.update { it.copy(enabled = false) }

      val button = StartStopButton(handle, handle.activationAction, handle.deactivationAction, null)

      val dialog = TestMessagesDialog(Messages.OK)
      TestDialogManager.setTestDialog(dialog)

      SwingUtilities.invokeAndWait { button.doClick() }
      advanceUntilIdle()

      assertThat(dialog.displayedMessage).isEqualTo("Activation error")
      handle.scope.cancel()
    }

  @Test
  fun repairableDevice() =
    testScope.runTest {
      val scope = createChildScope()
      val handle = FakeDeviceHandle(scope)
      val button =
        StartStopButton(
          handle,
          handle.activationAction,
          handle.deactivationAction,
          handle.repairDeviceAction
        )
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
          error = TestError()
        )
      }
      handle.repairDeviceAction.presentation.update {
        it.copy(enabled = true, icon = AllIcons.Actions.Download)
      }

      advanceUntilIdle()
      assertThat(button.baseIcon).isEqualTo(AllIcons.Actions.Download)

      handle.repairDeviceAction.presentation.update { it.copy(enabled = false) }

      advanceUntilIdle()
      assertThat(button.baseIcon).isEqualTo(StudioIcons.Avd.RUN)

      scope.cancel()
    }
}
