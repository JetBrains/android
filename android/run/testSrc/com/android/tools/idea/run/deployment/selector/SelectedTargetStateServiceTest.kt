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

import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.SimpleConfigurationType
import kotlinx.datetime.Clock
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SelectedTargetStateServiceTest {
  @get:Rule val projectRule: AndroidProjectRule = AndroidProjectRule.inMemory()

  private lateinit var runManager: RunManager
  private lateinit var stateService: SelectedTargetStateService

  @Before
  fun setup() {
    runManager = RunManager.getInstance(projectRule.project)
    stateService = SelectedTargetStateService(projectRule.project)
  }

  internal fun arbitraryDropdownSelection() =
    DropdownSelection(
      target = TargetId(arbitraryDeviceId(), null, DefaultBoot),
      timestamp = Clock.System.now(),
    )

  private fun arbitraryDeviceId() = DeviceId("Test", false, "device${nextCounter()}")

  private fun nextCounter(): Int = counter++

  private var counter = 1

  @Test
  fun loadStateRemovesObsoleteRunConfigurationStates() {
    val existingConfig =
      runManager.createConfiguration("existing config", AndroidRunConfigurationType::class.java)
    runManager.addConfiguration(existingConfig)

    val oldConfigState =
      SelectionState(runConfigName = "old config", dropdownSelection = arbitraryDropdownSelection())
    val existingConfigState =
      SelectionState(
        runConfigName = "existing config",
        dropdownSelection = arbitraryDropdownSelection(),
      )
    val persistedRunConfigState =
      SelectionStateList().apply {
        selectionStates.add(existingConfigState.toXml())
        selectionStates.add(oldConfigState.toXml())
      }

    stateService.loadState(persistedRunConfigState)

    val newConfig =
      runManager.createConfiguration("old config", AndroidRunConfigurationType::class.java)
    assertThat(stateService.getState(existingConfig.configuration)).isEqualTo(existingConfigState)
    assertThat(stateService.getState(newConfig.configuration)).isNotEqualTo(oldConfigState)
  }

  @Test
  fun getStateRemovesInvalidRunConfigurationStates() {
    val existingConfig =
      runManager.createConfiguration("existing config", AndroidRunConfigurationType::class.java)
    val nonAndroidConfig =
      runManager.createConfiguration("non android config", SimpleConfigurationType::class.java)
    runManager.addConfiguration(existingConfig)
    runManager.addConfiguration(nonAndroidConfig)

    stateService.state.apply {
      selectionStates.add(SelectionStateXml(runConfigName = "existing config"))
      selectionStates.add(SelectionStateXml(runConfigName = "obsolete config"))
      selectionStates.add(SelectionStateXml(runConfigName = "non android config"))
    }

    val runConfigNames = stateService.state.selectionStates.map { it.runConfigName }.toSet()
    assertThat(runConfigNames).contains("existing config")
    assertThat(runConfigNames).doesNotContain("obsolete config")
    assertThat(runConfigNames).doesNotContain("non android config")
  }
}
