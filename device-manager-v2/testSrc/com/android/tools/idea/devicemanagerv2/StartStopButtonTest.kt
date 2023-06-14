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

import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.utils.createChildScope
import com.android.sdklib.deviceprovisioner.DeviceError
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.testFramework.ApplicationRule
import icons.StudioIcons
import javax.swing.SwingUtilities
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class StartStopButtonTest {

  @get:Rule val ruleChain = RuleChain.outerRule(ApplicationRule()).around(AndroidExecutorsRule())

  @Test
  fun enabled(): Unit = runBlocking {
    val handle = FakeDeviceHandle(this.createChildScope())
    val button = StartStopButton(handle, handle.activationAction, handle.deactivationAction, null)

    assertThat(button.isEnabled).isTrue()
    assertThat(button.baseIcon).isEqualTo(StudioIcons.Avd.RUN)

    button.doClick()

    yieldUntil { handle.activationAction.invoked == 1 }
    yieldUntil { button.baseIcon == StudioIcons.Avd.STOP }

    assertThat(button.isEnabled).isTrue()

    button.doClick()

    yieldUntil { handle.deactivationAction.invoked == 1 }
    yieldUntil { button.baseIcon == StudioIcons.Avd.RUN }

    handle.scope.cancel()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun repairableDevice() = runTest {
    val scope = createChildScope(context = UnconfinedTestDispatcher(testScheduler))

    val handle = FakeDeviceHandle(scope)
    val button =
      StartStopButton(
        handle,
        handle.activationAction,
        handle.deactivationAction,
        handle.repairDeviceAction
      )
    handle.activationAction.presentation.update { it.copy(enabled = false) }

    class TestError : DeviceError {
      override val message = "error"
    }

    assertThat(button.baseIcon).isEqualTo(StudioIcons.Avd.RUN)

    handle.stateFlow.update {
      DeviceState.Disconnected(
        DeviceProperties.build {},
        isTransitioning = false,
        "Disconnected",
        error = TestError()
      )
    }
    handle.repairDeviceAction.presentation.update {
      it.copy(enabled = true, icon = AllIcons.Actions.Download)
    }

    // The icon is updated on the UI thread, pump all EDT events:
    SwingUtilities.invokeAndWait {}
    assertThat(button.baseIcon).isEqualTo(AllIcons.Actions.Download)

    handle.repairDeviceAction.presentation.update { it.copy(enabled = false) }

    SwingUtilities.invokeAndWait {}
    assertThat(button.baseIcon).isEqualTo(StudioIcons.Avd.RUN)

    scope.cancel()
  }
}
