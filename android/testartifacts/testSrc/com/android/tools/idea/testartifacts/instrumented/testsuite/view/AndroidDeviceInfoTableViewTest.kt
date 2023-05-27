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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.MockitoAnnotations

/**
 * Unit tests for [AndroidDeviceInfoTableView].
 */
@RunWith(JUnit4::class)
@RunsInEdt
class AndroidDeviceInfoTableViewTest {

  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  @get:Rule val rules: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(EdtRule())
    .around(disposableRule)

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun initialTable() {
    val table = AndroidDeviceInfoTableView()

    val model = table.myTableView.model
    val view = table.myTableView
    assertThat(model.columnCount).isEqualTo(2)
    assertThat(model.getColumnName(0)).isEqualTo("Property")
    assertThat(model.getColumnName(1)).isEqualTo("Description")
    assertThat(model.rowCount).isEqualTo(0)
    assertThat(view.isFocusable).isFalse()
    assertThat(view.rowSelectionAllowed).isFalse()
  }

  @Test
  fun setAndroidDevice() {
    val table = AndroidDeviceInfoTableView()

    val device = AndroidDevice(
      "mock device id", "mock device name", "mock device name",
      AndroidDeviceType.LOCAL_EMULATOR,
      AndroidVersion(29),
      mutableMapOf("Manufacturer" to "mock manufacturer name"))
    table.setAndroidDevice(device)

    val model = table.myTableView.model
    assertThat(model.rowCount).isEqualTo(3)
    assertThat(model.getValueAt(0, 0)).isEqualTo("Device Name")
    assertThat(model.getValueAt(0, 1)).isEqualTo("mock device name")
    assertThat(model.getValueAt(1, 0)).isEqualTo("OS Version")
    assertThat(model.getValueAt(1, 1)).isEqualTo("29")
    assertThat(model.getValueAt(2, 0)).isEqualTo("Manufacturer")
    assertThat(model.getValueAt(2, 1)).isEqualTo("mock manufacturer name")
  }
}