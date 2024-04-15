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
package com.android.tools.idea.run.deployment.selector

import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.openapi.actionSystem.ActionPlaces
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class DeviceAndSnapshotComboBoxActionTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  val project
    get() = projectRule.project

  private fun runTestWithFixture(test: suspend Fixture.() -> Unit) = runTest {
    with(Fixture(project, this)) { runFixture { test() } }
  }

  @Test
  fun update_noRunConfig() = runTestWithFixture {
    runManager.selectedConfiguration = null
    val event = actionEvent(dataContext(project), place = ActionPlaces.MAIN_TOOLBAR)
    comboBox.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.description).isEqualTo("Add a run/debug configuration")
  }

  @Test
  fun update_noLaunchCompatibility() = runTestWithFixture {
    RunManager.getInstance(project).createTestConfig()
    devicesFlow.value = listOf(FakeDeviceHandle(scope, null, handleId("1")))

    val event = actionEvent(dataContext(project), place = ActionPlaces.MAIN_TOOLBAR)
    comboBox.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.text).isEqualTo("Loading Devices...")
  }

  @Test
  fun update_defaultDevice() = runTestWithFixture {
    RunManager.getInstance(project).createTestConfig()
    devicesFlow.value = listOf(FakeDeviceHandle(scope, null, handleId("1")))
    sendLaunchCompatibility()

    devicesSelectedService // fetch lazy value
    testScope.advanceUntilIdle()

    val event = actionEvent(dataContext(project), place = ActionPlaces.MAIN_TOOLBAR)
    comboBox.update(event)

    assertThat(event.presentation.text).isEqualTo("1")
  }

  @Test
  fun setTargetSelectedWithCombobox() = runTestWithFixture {
    RunManager.getInstance(project).createTestConfig()
    devicesFlow.value =
      listOf(
        FakeDeviceHandle(scope, null, handleId("1")),
        FakeDeviceHandle(scope, null, handleId("2")),
      )
    sendLaunchCompatibility()

    val devices =
      devicesSelectedService.devicesAndTargetsFlow.first { it.allDevices.size == 2 }.allDevices

    SelectDeviceAction(devices[0]).actionPerformed(actionEvent(dataContext(project)))
    testScope.advanceUntilIdle()

    val event = actionEvent(dataContext(project), place = ActionPlaces.MAIN_TOOLBAR)

    comboBox.update(event)
    assertThat(event.presentation.text).isEqualTo(devices[0].name)

    SelectDeviceAction(devices[1]).actionPerformed(actionEvent(dataContext(project)))
    testScope.advanceUntilIdle()

    comboBox.update(event)
    assertThat(event.presentation.text).isEqualTo(devices[1].name)
  }
}
