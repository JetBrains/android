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
package com.android.tools.idea.run.deployment.legacyselector

import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.run.deployment.legacyselector.DevicesSelectedService.MapState
import com.android.tools.idea.run.deployment.legacyselector.DevicesSelectedService.PersistentStateComponent
import com.android.tools.idea.run.deployment.legacyselector.DevicesSelectedService.State
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.AndroidProjectRule.Companion.inMemory
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.SimpleConfigurationType
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DevicesSelectedPersistentStateComponentTest {

  @get:Rule
  val myProjectRule: AndroidProjectRule = inMemory()

  private lateinit var myPersistentStateComponent: PersistentStateComponent
  private lateinit var myRunManager: RunManager

  @Before
  fun setup() {
    myRunManager = RunManager.getInstance(myProjectRule.project)
    myPersistentStateComponent = PersistentStateComponent(myProjectRule.project)
  }

  @Test
  fun testLoadStateRemovesObsoleteRunConfigurationsStates() {
    val existingConfig = myRunManager.createConfiguration("existing config", AndroidRunConfigurationType::class.java)
    myRunManager.addConfiguration(existingConfig)
    val previousRunConfigState = State().apply {
      multipleDevicesSelectedInDropDown = true
    }

    myPersistentStateComponent.loadState(MapState().apply {
      value["existing config"] = State()
      value["non existing config"] = previousRunConfigState
    })

    val newConfig = myRunManager.createConfiguration("non existing config", AndroidRunConfigurationType::class.java)
    assertThat(myPersistentStateComponent.getState(newConfig.configuration)).isNotEqualTo(previousRunConfigState)
  }

  @Test
  fun testGetStateRemovesInvalidRunConfigurationsStates() {
    val existingConfig = myRunManager.createConfiguration("existing config", AndroidRunConfigurationType::class.java)
    val nonAndroidConfig = myRunManager.createConfiguration("non android config", SimpleConfigurationType::class.java)
    myRunManager.addConfiguration(existingConfig)
    myRunManager.addConfiguration(nonAndroidConfig)

    myPersistentStateComponent.state.apply {
      value["existing config"] = State()
      value["obsolete config"] = State()
      value["non android config"] = State()
    }

    assertThat(myPersistentStateComponent.state.value).containsKey("existing config")
    assertThat(myPersistentStateComponent.state.value).doesNotContainKey("obsolete config")
    assertThat(myPersistentStateComponent.state.value).doesNotContainKey("non android config")
  }
}