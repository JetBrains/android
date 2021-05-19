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
import com.android.flags.junit.RestoreFlagRule
import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.emulator.EMULATOR_CONTROLLER_KEY
import com.android.tools.idea.emulator.EMULATOR_VIEW_KEY
import com.android.tools.idea.emulator.EmulatorController
import com.android.tools.idea.emulator.EmulatorView
import com.android.tools.idea.emulator.FakeEmulator
import com.android.tools.idea.emulator.FakeEmulatorRule
import com.android.tools.idea.emulator.RunningEmulatorCatalog
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.concurrent.TimeUnit

/**
 * Tests for [EmulatorShowExtendedControlsAction].
 */
@RunsInEdt
class EmulatorShowExtendedControlsActionTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val emulatorRule = FakeEmulatorRule()
  val restoreFlagRule = RestoreFlagRule(StudioFlags.EMBEDDED_EMULATOR_EXTENDED_CONTROLS)
  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(restoreFlagRule).around(emulatorRule).around(EdtRule())

  private var nullableEmulator: FakeEmulator? = null

  private var emulator: FakeEmulator
    get() = nullableEmulator ?: throw IllegalStateException()
    set(value) { nullableEmulator = value }

  private val testRootDisposable
    get() = projectRule.fixture.testRootDisposable

  @Before
  fun setUp() {
    StudioFlags.EMBEDDED_EMULATOR_EXTENDED_CONTROLS.override(true)
    enableHeadlessDialogs(testRootDisposable)
  }

  @Test
  fun testShowExtendedControls() {
    val view = createEmulatorView()

    // Check snapshot creation.
    val actionManager = ActionManager.getInstance()
    val action = actionManager.getAction("android.emulator.extended.controls")
    val event = AnActionEvent(null, TestDataContext(view), ActionPlaces.UNKNOWN, Presentation(), actionManager, 0)
    action.actionPerformed(event)

    var call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.UiController/setUiTheme")
    assertThat(call.request).isEqualTo(ThemingStyle.getDefaultInstance())
    call = emulator.getNextGrpcCall(1, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.UiController/showExtendedControls")
  }

  private fun createEmulatorView(): EmulatorView {
    val catalog = RunningEmulatorCatalog.getInstance()
    val tempFolder = emulatorRule.root.toPath()
    emulator = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder), 8554)
    emulator.start()
    val emulators = catalog.updateNow().get()
    assertThat(emulators).hasSize(1)
    val emulatorController = emulators.first()
    val view = EmulatorView(emulatorController, testRootDisposable, false)
    waitForCondition(5, TimeUnit.SECONDS) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    emulator.getNextGrpcCall(2, TimeUnit.SECONDS) // Skip the initial "getVmState" call.
    return view
  }

  private inner class TestDataContext(private val emulatorView: EmulatorView) : DataContext {

    override fun getData(dataId: String): Any? {
      return when (dataId) {
        EMULATOR_CONTROLLER_KEY.name -> emulatorView.emulator
        EMULATOR_VIEW_KEY.name, ZOOMABLE_KEY.name -> emulatorView
        CommonDataKeys.PROJECT.name -> projectRule.project
        else -> null
      }
    }
  }
}
