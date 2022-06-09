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
package com.android.tools.idea.emulator.actions

import com.android.emulator.control.ThemingStyle
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.idea.emulator.EmulatorViewRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.concurrent.TimeUnit
import javax.swing.UIManager

/**
 * Tests for [EmulatorShowExtendedControlsAction].
 */
@RunsInEdt
class EmulatorShowExtendedControlsActionTest {
  private val emulatorViewRule = EmulatorViewRule()
  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(emulatorViewRule).around(EdtRule())

  @Before
  fun setUp() {
    enableHeadlessDialogs(emulatorViewRule.testRootDisposable)
  }

  @Test
  fun testShowExtendedControls() {
    val mockLafManager = mock<LafManager>()
    whenever(mockLafManager.currentLookAndFeel).thenReturn(UIManager.LookAndFeelInfo("High contrast", "Ignored className"))
    ApplicationManager.getApplication().replaceService(LafManager::class.java, mockLafManager, emulatorViewRule.testRootDisposable)

    val view = emulatorViewRule.newEmulatorView()
    emulatorViewRule.executeAction("android.emulator.extended.controls", view)

    val emulator = emulatorViewRule.getFakeEmulator(view)
    var call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.UiController/setUiTheme")
    assertThat(call.request).isEqualTo(ThemingStyle.newBuilder().setStyle(ThemingStyle.Style.CONTRAST).build())
    call = emulator.getNextGrpcCall(1, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.UiController/showExtendedControls")
  }
}
