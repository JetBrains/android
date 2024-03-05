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
import com.intellij.ide.ActivityTracker
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesSelectedServiceTest {

  @get:Rule val projectRule: AndroidProjectRule = AndroidProjectRule.inMemory()

  val project
    get() = projectRule.project

  @Test
  fun defaultDevice() = runTestWithFixture {
    val device = createDevice("1")
    devices = listOf(device)

    val devicesAndTargets = devicesSelectedService.devicesAndTargetsFlow.first()

    assertThat(devicesAndTargets.allDevices).contains(device)
    assertThat(devicesAndTargets.selectedTargets).containsExactly(device.defaultTarget)
  }

  @Test
  fun updateSingleSelection() = runTestWithFixture {
    val device1 = createDevice("1")
    val target1 = DeploymentTarget(device1, DefaultBoot)

    val state = selectedTargetStateService.getState(runConfiguration.configuration)
    selectedTargetStateService.updateState(
      state.copy(
        selectionMode = SelectionMode.DROPDOWN,
        dropdownSelection =
          DropdownSelection(target = target1.id, timestamp = clock.now() - 10.seconds),
      )
    )

    val device2 = createDevice("2", connectionTime = clock.now() - 5.seconds)
    val target2 = DeploymentTarget(device2, DefaultBoot)

    devices = listOf(device1, device2)

    // Favor the more recently connected device over the selected device.
    assertThat(devicesSelectedService.devicesAndTargets.selectedTargets).containsExactly(target2)

    val modificationCount = ActivityTracker.getInstance().count
    devicesSelectedService.setTargetSelectedWithComboBox(target1)
    testScope.advanceUntilIdle()

    // Now favor the just-selected device
    assertThat(devicesSelectedService.devicesAndTargets.selectedTargets).containsExactly(target1)
    assertThat(ActivityTracker.getInstance().count).isGreaterThan(modificationCount)
  }

  @Test
  fun setMultipleDevices() = runTestWithFixture {
    val device1 = createDevice("1")
    val device2 = createDevice("2")
    val device3 = createDevice("3")

    val target1 = DeploymentTarget(device1, DefaultBoot)
    val target2 = DeploymentTarget(device2, ColdBoot)
    val target3 = DeploymentTarget(device3, ColdBoot)

    devices = listOf(device1, device2, device3)

    devicesSelectedService.setTargetSelectedWithComboBox(target3)
    testScope.advanceUntilIdle()
    devicesSelectedService.setTargetsSelectedWithDialog(listOf(target1, target2))
    testScope.advanceUntilIdle()

    assertThat(devicesSelectedService.devicesAndTargets.isMultipleSelectionMode).isTrue()
    assertThat(devicesSelectedService.devicesAndTargets.selectedTargets)
      .containsExactly(target1, target2)
    assertThat(devicesSelectedService.getTargetsSelectedWithDialog())
      .containsExactly(target1, target2)

    devices = listOf(device1, device3)

    assertThat(devicesSelectedService.devicesAndTargets.isMultipleSelectionMode).isTrue()
    assertThat(devicesSelectedService.devicesAndTargets.selectedTargets).containsExactly(target1)
    assertThat(devicesSelectedService.getTargetsSelectedWithDialog()).containsExactly(target1)

    // Switch to single-selection mode after all of the multi-selected devices go away
    devices = listOf(device3)

    assertThat(devicesSelectedService.devicesAndTargets.isMultipleSelectionMode).isFalse()
    assertThat(devicesSelectedService.devicesAndTargets.selectedTargets).containsExactly(target3)
    assertThat(devicesSelectedService.getTargetsSelectedWithDialog()).isEmpty()

    // Since we're in single-selection mode now, we stick with device3
    devices = listOf(device1, device2, device3)

    assertThat(devicesSelectedService.devicesAndTargets.isMultipleSelectionMode).isFalse()
    assertThat(devicesSelectedService.devicesAndTargets.selectedTargets).containsExactly(target3)
    assertThat(devicesSelectedService.getTargetsSelectedWithDialog())
      .containsExactly(target1, target2)
  }

  @Test
  fun resolve() = runTestWithFixture {
    val template1 = createTemplate("T1")
    val device1a = createDevice("D1A", sourceTemplate = template1)
    val device1b = createDevice("D1B", sourceTemplate = template1)

    val template2 = createTemplate("T2")
    val device2 = createDevice("D2", sourceTemplate = template2)

    val device3 = createDevice("D3")

    fun DeploymentTargetDevice.targetId() = DeploymentTarget(this, DefaultBoot).id
    fun DeploymentTargetDevice.resolve(vararg devices: DeploymentTargetDevice) =
      targetId().resolve(listOf(*devices))?.device

    // For each class of device, after the null cases, add items to the set of choices in order of
    // preference.
    assertThat(template1.resolve(template2, device2, device3)).isNull()
    assertThat(template1.resolve(template1)).isEqualTo(template1)
    assertThat(template1.resolve(template1, device1a)).isEqualTo(device1a)

    assertThat(device1a.resolve(template2, device2, device3)).isNull()
    assertThat(device1a.resolve(template1)).isEqualTo(template1)
    assertThat(device1a.resolve(template1, device1b)).isEqualTo(device1b)
    assertThat(device1a.resolve(template1, device1b, device1a)).isEqualTo(device1a)

    assertThat(device3.resolve(template1, device1a, device1b, template2, device2)).isNull()
    assertThat(device3.resolve(device3)).isEqualTo(device3)
  }

  private fun runTestWithFixture(block: suspend DevicesSelectedServiceTestFixture.() -> Unit) =
    runTestWithDevicesSelectedServiceFixture(projectRule.project, block)
}
