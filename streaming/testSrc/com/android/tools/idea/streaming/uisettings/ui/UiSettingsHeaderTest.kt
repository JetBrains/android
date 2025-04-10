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
package com.android.tools.idea.streaming.uisettings.ui

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.getDescendant
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import com.intellij.ui.components.ActionLink
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import kotlin.time.Duration.Companion.seconds

class UiSettingsHeaderTest {
  private lateinit var model: UiSettingsModel
  private lateinit var header: UiSettingsHeader
  private lateinit var ui: FakeUi
  private var lastCommand: String = ""

  @get:Rule
  val disposableRule = DisposableRule()

  @Before
  fun before() {
    model = UiSettingsModel(Dimension(1344, 2992), 480, 34, DeviceType.HANDHELD)
    header = UiSettingsHeader(model)
    ui = FakeUi(header, createFakeWindow = true, parentDisposable = disposableRule.disposable)
    model.resetAction = { lastCommand = "reset" }
  }

  @Test
  fun testResetButton() {
    val link = header.getDescendant<ActionLink> { it.name == RESET_TITLE }
    assertThat(link.accessibleContext.accessibleName).isEqualTo(RESET_TITLE)
    model.differentFromDefault.setFromController(false)
    assertThat(link.isShowing).isFalse()
    model.differentFromDefault.setFromController(true)
    assertThat(link.isShowing).isTrue()
    link.doClick()
    waitForCondition(1.seconds) { lastCommand == "reset" }
  }
}
