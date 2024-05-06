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
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Rule
import org.junit.Test

class SelectMultipleDevicesDialogTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private lateinit var dialog: SelectMultipleDevicesDialog

  private fun DevicesSelectedServiceTestFixture.createDialog() {
    ApplicationManager.getApplication().invokeAndWait {
      dialog = SelectMultipleDevicesDialog(projectRule.project, { devicesSelectedService })
    }
  }

  @After
  fun disposeDialog() {
    ApplicationManager.getApplication().invokeAndWait { dialog.disposeIfNeeded() }
  }

  @Test
  fun showDialog() = runTestWithFixture {
    createDialog()

    assertThat(dialog.title).isEqualTo("Select Multiple Devices")
  }

  @Test
  fun enabledWhenSingleDeviceSelected() = runTestWithFixture {
    devices = listOf(createDevice("1"))
    createDialog()

    dialog.table.setSelected(true, 0)

    assertThat(dialog.isOKActionEnabled).isTrue()
  }

  @Test
  fun doOkAction() = runTestWithFixture {
    val device = createDevice("1")
    devices = listOf(device)
    createDialog()

    dialog.table.setSelected(true, 0)
    ApplicationManager.getApplication().invokeAndWait { dialog.performOKAction() }
    testScope.advanceUntilIdle()

    assertThat(devicesSelectedService.getTargetsSelectedWithDialog())
      .containsExactly(DeploymentTarget(device, DefaultBoot))
    assertThat(devicesSelectedService.devicesAndTargets.isMultipleSelectionMode).isTrue()
  }

  @Test
  fun validation() = runTestWithFixture {
    val device = createDevice("1", hasSnapshots = true)
    devices = listOf(device)
    createDialog()
    dialog.table.setSelected(true, 0)
    dialog.table.setSelected(true, 1)

    val validationInfo = dialog.doValidate()

    assertThat(validationInfo?.message)
      .isEqualTo(
        "Some of the selected targets are for the same device. Each target should be for a different device."
      )
    assertThat(validationInfo?.component).isNull()
  }

  private fun runTestWithFixture(block: suspend DevicesSelectedServiceTestFixture.() -> Unit) =
    runTestWithDevicesSelectedServiceFixture(projectRule.project, block)
}
